import ClientServerCommunication.*;
import ServerCommunication.*;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Server {
    private final BatchingStrategy                     batchingStrategy;
    private final int                                  id;
    private final Ledger                               ledger       = new Ledger();
    private final Set<Integer>                         lostPeerIds  = new HashSet<>();// TODO: protect
    private final Map<Integer, PeerServer>             peers        = new HashMap<>();// TODO: protect
    private final ConcurrentHashMap<BlockId, BlockMsg> pending      = new ConcurrentHashMap<>();
    private final BlockBuilder                         blockBuilder = new BlockBuilder();
    private final io.grpc.Server                       serverListener;
    private final io.grpc.Server                       clientListener;
    private final ZooKeeperClient                      zkClient;
    private final InetSocketAddress                    address;
    private final int                                  serverPort;
    private       int                                  myBlockNum = 0;

    Server(int id, int clientPort, int serverPort, Duration blockWindow) {
        this.id = id;
        address = SocketAddressFactory.from("localhost", serverPort);
        batchingStrategy = new TimedAdaptiveBatching(this::trySealBlock, blockWindow);
        this.serverPort = serverPort;
        serverListener = io.grpc.ServerBuilder.forPort(serverPort)
                                              .addService(new ServerRpc())
                                              .build();
        clientListener = io.grpc.ServerBuilder.forPort(clientPort)
                                              .addService(new ClientRpc())
                                              .build();
        zkClient = new ZooKeeperClient(this);
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
        // clean up removed peers
        List<Integer> removedPeersIds = peers.keySet()
                                             .stream()
                                             .flatMap(peerId -> {
                                                 if (newView.contains(peerId))
                                                     return Stream.empty();
                                                 return Stream.of(peerId);
                                             }).collect(Collectors.toList());

        for (Integer peerId : removedPeersIds) {
            final PeerServer peer = peers.remove(peerId);
            peer.shutdown();
            cleanUpServerBlocks(peerId);
        }

        // add new peers
        for (Integer serverId : newView) {
            peers.computeIfAbsent(serverId,
                                  id -> new PeerServer(zkClient.getServerMembershipData(id)));
        }
    }

    private void cleanUpServerBlocks(int serverId) {
        // TODO: synchronize with ZK to find the latest block chained by this server.
        //  this can by done once for all disconnected servers
        int latestBlock = Integer.MAX_VALUE - 1; // TODO replace with the above real value

        lostPeerIds.add(serverId);

        List<BlockId> toBeDeleted = new ArrayList<>();
        pending.forEachKey(1024, blockId -> {
            if (blockId.getServerId() == serverId && blockId.getSerialNumber() > latestBlock) {
                toBeDeleted.add(blockId);
            }
        });

        toBeDeleted.forEach(pending::remove);
    }

    void onBlockChained(BlockId blockId) {
        var blockMsg = pending.remove(blockId);
        assert blockMsg != null;//TODO remove, what if it's not here yet? need to pull

        ledger.apply(Block.from(blockMsg));
    }

    private BlockId pushBlock(@NotNull Block block) {
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

    InetSocketAddress getServerAddress() {
        return address;
    }

    private class ServerRpc extends ServerGrpc.ServerImplBase {
        @Override
        public void pushBlock(PushBlockReq request, StreamObserver<PushBlockRsp> responseObserver) {
            var block   = request.getBlock();
            var blockId = block.getId();

            if (lostPeerIds.contains(blockId.getServerId()))
                return; // don't listen to this zombie

            pending.putIfAbsent(blockId, block);

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
