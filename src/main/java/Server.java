import ClientServerCommunication.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Server {
    private final Ledger            ledger       = new Ledger();
    private final List<OpenedBlock> chain        = new ArrayList<>();// TODO: should not OpenedBlock, or at least rename to "Block"
    private final AtomicInteger     lastId       = new AtomicInteger();
    private final ReadWriteLock     rwLock       = new ReentrantReadWriteLock();
    private final AtomicBoolean     terminating  = new AtomicBoolean(false);
    private final io.grpc.Server    clientListener;
    private final Thread            appender     = new Thread(new Appender());
    private       Duration          blockWindow;
    private       OpenedBlock       currentBlock = new OpenedBlock();

    Server(int port, Duration blockWindow) {
        this.blockWindow = blockWindow;
        clientListener = io.grpc.ServerBuilder.forPort(port).addService(new ClientRpc()).build();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 55555;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        var server = new ServerBuilder().setPort(port).createServer().start();
        server.awaitTermination();
    }

    Server start() throws IOException {
        appender.start();
        clientListener.start();
        return this;
    }

    void awaitTermination() throws InterruptedException {
        clientListener.awaitTermination();
    }

    void shutdown() {
        terminating.setRelease(true);
        clientListener.shutdown();
        try {
            appender.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(chain.stream()
                                .map(Objects::toString)
                                .collect(Collectors.joining("\n",
                                                            "=== CHAIN AT EXIT: ===\n",
                                                            "\n")));
        assert currentBlock.isEmpty();
    }

    private void trySealBlock() {
        final var   wLock = rwLock.writeLock();
        OpenedBlock block = null;

        wLock.lock();
        try {
            if (!currentBlock.isEmpty()) {
                block = currentBlock;
                currentBlock = new OpenedBlock();
            }
        } finally {
            wLock.unlock();
        }

        if (block != null) {
            block.apply(ledger);
            chain.add(block);
            System.out.println("SERVER: appended!");
            System.out.println(block);
        }
    }

    private class ClientRpc extends ClientGrpc.ClientImplBase {
        @Override
        public void createAccount(CreateAccountReq request,
                                  StreamObserver<CreateAccountRsp> responseObserver) {
            System.out.println("SERVER: Create account");

            int id = lastId.incrementAndGet();

            currentBlock.append(new NewAccountTx(id).setResponse(responseObserver));
        }

        @Override
        public void deleteAccount(DeleteAccountReq request,
                                  StreamObserver<DeleteAccountRsp> responseObserver) {
            int accountId = request.getAccountId();
            System.out.println("SERVER: Delete account:" + accountId);

            final var rLock = rwLock.readLock();
            rLock.lock();
            try {
                currentBlock.append(new DeleteAccountTx(accountId).setResponse(responseObserver));
            } finally {
                rLock.unlock();
            }
        }

        @Override
        public void addAmount(AddAmountReq request, StreamObserver<AddAmountRsp> responseObserver) {
            int amount = request.getAmount();
            int id     = request.getAccountId();
            System.out.println("SERVER: Account " + id + ", add " + amount);

            final var rLock = rwLock.readLock();
            rLock.lock();
            try {
                currentBlock.append(new DepositTx(id, amount).setResponse(responseObserver));
            } finally {
                rLock.unlock();
            }
        }

        @Override
        public void getAmount(GetAmountReq request, StreamObserver<GetAmountRsp> responseObserver) {
            int id = request.getAccountId();
            System.out.println("SERVER: getAmount " + id);
            var rspBuilder = GetAmountRsp.newBuilder();

            var amount = ledger.get(id);
            if (amount != null) {
                rspBuilder.setAmount(amount);
                rspBuilder.setSuccess(true);
                System.out.println("SERVER: Amount of " + id + " is " + amount);
            }

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void transfer(TransferReq request,
                             StreamObserver<TransferRsp> responseObserver) {
            int amount = request.getAmount();
            int from   = request.getFromId();
            int to     = request.getToId();
            System.out.println("SERVER: from " + from + " to " + to + " : " + amount);

            final var rLock = rwLock.readLock();
            rLock.lock();
            try {
                currentBlock.append(new TransferTx(from, to, amount).setResponse(responseObserver));
            } finally {
                rLock.unlock();
            }
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
