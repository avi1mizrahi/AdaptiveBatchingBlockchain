import ClientServerCommunication.*;
import ServerCommunication.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Server {
    private final ReadWriteLock                        ledgerLock   = new ReentrantReadWriteLock();
    private final Ledger                               ledger       = new Ledger();
    private final List<Block>                          chain        = new ArrayList<>();
    private final ConcurrentHashMap<BlockId, BlockMsg> pending      = new ConcurrentHashMap<>();
    private final AtomicBoolean                        terminating  = new AtomicBoolean(false);
    private final Duration                             blockWindow;
    private final BlockBuilder                         blockBuilder = new BlockBuilder();
    private final Thread                               appender     = new Thread(new Appender());
    private final io.grpc.Server                       serverListener;
    private final io.grpc.Server                       clientListener;

    Server(int clientPort, int serverPort, Duration blockWindow) {
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
        System.out.println(chain.stream()
                                .map(Objects::toString)
                                .collect(Collectors.joining("\n",
                                                            "=== CHAIN AT EXIT: ===\n",
                                                            "\n")));
        assert blockBuilder.isEmpty();
    }

    private void trySealBlock() {
        if (blockBuilder.isEmpty()) {
            return;
        }

        Block block = blockBuilder.seal();

        var writeLock = ledgerLock.writeLock();
        writeLock.lock();
        try {
            block.apply(ledger);
        } finally {
            writeLock.unlock();
            chain.add(block);
        }

        System.out.println("SERVER: appended!");
        System.out.println(block);

    }

    private class ServerRpc extends ServerGrpc.ServerImplBase {
        @Override
        public void pushBlock(PushBlockReq request, StreamObserver<PushBlockRsp> responseObserver) {
            var block = request.getBlock();

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

            Integer amount;

            ledgerLock.readLock().lock();
            try {
                amount = ledger.get(account);
            } finally {
                ledgerLock.readLock().unlock();
            }

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

            while (!terminating.getAcquire()) {
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
