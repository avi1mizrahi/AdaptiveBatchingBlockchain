import ClientServerCommunication.*;
import ServerCommunication.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class Server {
    private final BatchingStrategy                     batchingStrategy;
    private final int                                  id;
    private final Ledger                               ledger       = new Ledger();
    // TODO: add concurrentSet<ServerID> crashedservers
    private final ConcurrentHashMap<BlockId, BlockMsg> pending      = new ConcurrentHashMap<>();
    private final BlockBuilder                         blockBuilder = new BlockBuilder();
    private final io.grpc.Server                       serverListener;
    private final io.grpc.Server                       clientListener;

    private final ZooKeeperClient  zkClient;
    private final String           hostname   = "localhost";
    private final int              clientPort;
    private final int              serverPort;
    private final List<PeerServer> serversView;
    private       int              myBlockNum = 0;

    Server(int id, int clientPort, int serverPort, Duration blockWindow) {
        this.id = id;
        batchingStrategy = new TimedAdaptiveBatching(this::trySealBlock, blockWindow);
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        serverListener = io.grpc.ServerBuilder.forPort(serverPort)
                                              .addService(new ServerRpc())
                                              .build();
        clientListener = io.grpc.ServerBuilder.forPort(clientPort)
                                              .addService(new ClientRpc())
                                              .build();
        zkClient = new ZooKeeperClient(this);
        serversView = new ArrayList<>();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 55555;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        Server server = new ServerBuilder().setId(1)
                                           .setClientPort(port)
                                           .setServerPort(port - 11111)
                                           .createServer()
                                           .start();
        server.awaitTermination();
    }

    Server start() throws IOException {
        batchingStrategy.start();
        serverListener.start();
        clientListener.start();
        return this;
    }

    void awaitTermination() throws InterruptedException {
        clientListener.awaitTermination();
        serverListener.awaitTermination();
    }

    void shutdown() {
        zkClient.shutdown();
        batchingStrategy.shutdown();
        clientListener.shutdown();
        serverListener.shutdown();
        assert blockBuilder.isEmpty();
    }

    void onMembershipChange(Set<Integer> newView) {
        for (PeerServer peerServer : serversView) {
            if (!newView.contains(peerServer.getServerId())) {
                peerServer.shutdown();
            }
        }
        for (Integer serverId : newView) {
//            boolean inOldView = false;
            for (PeerServer peerServer : serversView) {
                if (peerServer.getServerId() == serverId) {
//                    inOldView = true;
                    break;
                }
                // serverId not in old view
                String[] serverData = zkClient.getServerMembershipData(serverId);
                serversView.add(new PeerServer(serverId,
                                               serverData[0],
                                               Integer.parseInt(serverData[1])));
            }
        }
    }

    private void cleanUpServerBlocks(int serverId /* TODO: server can be identified either by id or by host:port*/) {
        // TODO: synchronize with ZK to find the latest block chained by this server.
        //  this can by done once for all disconnected servers
        int latestBlock = Integer.MAX_VALUE - 1; // TODO replace with the above real value

        // TODO: first mark this server as down
        //  crashedservers.add

        List<BlockId> toBeDeleted = new ArrayList<>();
        pending.forEachKey(1024, blockId -> {
            if (blockId.getServerId() == serverId && blockId.getSerialNumber() > latestBlock) {
                toBeDeleted.add(blockId);
            }
        });

        toBeDeleted.forEach(pending::remove);
    }

    // TODO: this should be done for each pending block that was agreed
    void onBlockChained(BlockId blockId) {
        var blockMsg = pending.remove(blockId);
        assert blockMsg != null;//TODO remove

        ledger.apply(Block.from(blockMsg));
    }

    private BlockId pushBlock(Block block) {
        var blockMsgBuilder = BlockMsg.newBuilder();
        block.addToBlockMsg(blockMsgBuilder);

        var blockId = BlockId.newBuilder()
                             .setServerId(id)
                             .setSerialNumber(myBlockNum++)
                             .build();
        blockMsgBuilder.setId(blockId);

        BlockMsg blockMsg = blockMsgBuilder.build();
        //TODO: send this to others
        //TODO: wait for more than half

        return blockId;
    }

    private void trySealBlock() {
        if (blockBuilder.isEmpty()) {
            return;
        }
        Block block   = blockBuilder.seal();
        var   blockId = pushBlock(block);
        zkClient.postBlock(blockId);
        ledger.apply(block);
        System.out.println("SERVER: appended!");
        System.out.println(block);

    }

    int getId() {
        return id;
    }

    String getHostname() {
        return hostname;
    }

    int getClientPort() {
        return clientPort;
    }

    int getServerPort() {
        return serverPort;
    }

    private class ServerRpc extends ServerGrpc.ServerImplBase {
        @Override
        public void pushBlock(PushBlockReq request, StreamObserver<PushBlockRsp> responseObserver) {
            var block = request.getBlock();
            // TODO: if the sending server is down, don't put
            //  if server is in crashedservers
            pending.putIfAbsent(block.getId(), block);

            responseObserver.onNext(PushBlockRsp.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void pullBlock(PullBlockReq request, StreamObserver<PullBlockRsp> responseObserver) {
            var block = pending.get(request.getId());

            var builder = PullBlockRsp.newBuilder();
            if (block != null) {
                builder.setBlock(block).setSuccess(true);
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }

    private class ClientRpc extends ClientGrpc.ClientImplBase {
        @Override
        public void createAccount(CreateAccountReq request,
                                  StreamObserver<CreateAccountRsp> responseObserver) {
            batchingStrategy.onRequestBegin();

            System.out.println("SERVER: Create account");
            blockBuilder.append(new NewAccountTx().setResponse(responseObserver));

            batchingStrategy.onRequestEnd();
        }

        @Override
        public void deleteAccount(DeleteAccountReq request,
                                  StreamObserver<DeleteAccountRsp> responseObserver) {
            batchingStrategy.onRequestBegin();

            var account = Account.from(request.getAccountId());
            System.out.println("SERVER: Delete " + account);

            blockBuilder.append(new DeleteAccountTx(account).setResponse(responseObserver));

            batchingStrategy.onRequestEnd();
        }

        @Override
        public void addAmount(AddAmountReq request, StreamObserver<AddAmountRsp> responseObserver) {
            batchingStrategy.onRequestBegin();

            var amount  = request.getAmount();
            var account = Account.from(request.getAccountId());
            System.out.println("SERVER: " + account + ", add " + amount);

            blockBuilder.append(new DepositTx(account,
                                              amount).setResponse(responseObserver));

            batchingStrategy.onRequestEnd();
        }

        @Override
        public void getAmount(GetAmountReq request, StreamObserver<GetAmountRsp> responseObserver) {
            var account = Account.from(request.getAccountId());
            System.out.println("SERVER: getAmount " + account);
            var rspBuilder = GetAmountRsp.newBuilder();

            Integer amount = ledger.get(account);

            if (amount != null) {
                rspBuilder.setAmount(amount);
                rspBuilder.setSuccess(true);
                System.out.println("SERVER: Amount of " + account + " is " + amount);
            }

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void transfer(TransferReq request,
                             StreamObserver<TransferRsp> responseObserver) {
            batchingStrategy.onRequestBegin();

            var amount = request.getAmount();
            var from   = Account.from(request.getFromId());
            var to     = Account.from(request.getToId());
            System.out.println("SERVER: from " + from + " to " + to + " : " + amount);

            blockBuilder.append(new TransferTx(from, to, amount).setResponse(responseObserver));

            batchingStrategy.onRequestEnd();
        }
    }
}
