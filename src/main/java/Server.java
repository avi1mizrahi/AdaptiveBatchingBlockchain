import ClientServerCommunication.*;
import ServerCommunication.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class Server {
    private final int                                  id;
    private       int                                  myBlockNum   = 0;
    private final Ledger                               ledger       = new Ledger();
    // TODO: add concurrentSet<ServerID> crashedservers
    private final ConcurrentHashMap<BlockId, BlockMsg> pending      = new ConcurrentHashMap<>();
    private final AtomicBoolean                        terminating  = new AtomicBoolean(false);
    private final Duration                             blockWindow;
    private final BlockBuilder                         blockBuilder = new BlockBuilder();
    private final Thread                               appender     = new Thread(new Appender());
    private final io.grpc.Server                       serverListener;
    private final io.grpc.Server                       clientListener;

    public Server(int id, int clientPort, int serverPort, Duration blockWindow) {
        this.id = id;
        this.blockWindow = blockWindow;
        serverListener = io.grpc.ServerBuilder.forPort(serverPort)
                                              .addService(new ServerRpc())
                                              .build();
        clientListener = io.grpc.ServerBuilder.forPort(clientPort)
                                              .addService(new ClientRpc())
                                              .build();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 55555;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        var server = new ServerBuilder().setClientPort(port)
                                        .setServerPort(port - 11111)
                                        .createServer()
                                        .start();
        server.awaitTermination();
    }

    Server start() throws IOException {
        appender.start();
        serverListener.start();
        clientListener.start();
        return this;
    }

    void awaitTermination() throws InterruptedException {
        clientListener.awaitTermination();
        serverListener.awaitTermination();
    }

    void shutdown() {
        terminating.setRelease(true);
        clientListener.shutdown();
        try {
            appender.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        serverListener.shutdown();
        assert blockBuilder.isEmpty();
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
    private void onBlockChained(BlockId blockId) {
        var blockMsg = pending.remove(blockId);
        if (blockMsg == null) {
            // TODO: pull missing block from others
            assert false;//TODO remove
        }

        ledger.apply(Block.from(blockMsg));
    }

    private void pushBlock(Block block) {
        var blockMsgBuilder = BlockMsg.newBuilder();
        block.addToBlockMsg(blockMsgBuilder);

        blockMsgBuilder.getIdBuilder().setServerId(id).setSerialNumber(myBlockNum++);

        BlockMsg blockMsg = blockMsgBuilder.build();
        //TODO: send this to others
        //TODO: wait for more than half
    }

    private void trySealBlock() {
        if (blockBuilder.isEmpty()) {
            return;
        }

        Block block = blockBuilder.seal();

        pushBlock(block);
        // TODO: add to chain, ZK consensus
        ledger.apply(block);
        System.out.println("SERVER: appended!");
        System.out.println(block);

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
            System.out.println("SERVER: Create account");

            blockBuilder.append(new NewAccountTx().setResponse(responseObserver));
        }

        @Override
        public void deleteAccount(DeleteAccountReq request,
                                  StreamObserver<DeleteAccountRsp> responseObserver) {
            var account = Account.from(request.getAccountId());

            System.out.println("SERVER: Delete " + account);

            blockBuilder.append(new DeleteAccountTx(account).setResponse(responseObserver));
        }

        @Override
        public void addAmount(AddAmountReq request, StreamObserver<AddAmountRsp> responseObserver) {
            var amount  = request.getAmount();
            var account = Account.from(request.getAccountId());
            System.out.println("SERVER: " + account + ", add " + amount);

            blockBuilder.append(new DepositTx(account,
                                              amount).setResponse(responseObserver));
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
            var amount = request.getAmount();
            var from   = Account.from(request.getFromId());
            var to     = Account.from(request.getToId());
            System.out.println("SERVER: from " + from + " to " + to + " : " + amount);

            blockBuilder.append(new TransferTx(from, to, amount).setResponse(responseObserver));
        }

    }

    private class Appender implements Runnable {
        @Override
        public void run() {

            while (!terminating.getAcquire()) { // must use acquire semantics, as long as we don't lock inside block.isEmpty()
                trySealBlock();

                try {
                    Thread.sleep(blockWindow.toMillis());
                } catch (InterruptedException ignored) {
                }
            }

            trySealBlock();
        }
    }
}
