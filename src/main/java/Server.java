import ClientServerCommunication.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Server {
    private final Ledger         ledger       = new Ledger();
    private final List<Block>    chain        = new ArrayList<>();
    private final AtomicBoolean  terminating  = new AtomicBoolean(false);
    private final io.grpc.Server clientListener;
    private final Thread         appender     = new Thread(new Appender());
    private final Duration       blockWindow;
    private final BlockBuilder   blockBuilder = new BlockBuilder();

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
        assert blockBuilder.isEmpty();
    }

    private void trySealBlock() {
        if (blockBuilder.isEmpty()) {
            return;
        }

        Block block = blockBuilder.seal();

        block.apply(ledger);
        chain.add(block);

        System.out.println("SERVER: appended!");
        System.out.println(block);

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
            int accountId = request.getAccountId();
            System.out.println("SERVER: Delete account:" + accountId);

            blockBuilder.append(new DeleteAccountTx(accountId).setResponse(responseObserver));
        }

        @Override
        public void addAmount(AddAmountReq request, StreamObserver<AddAmountRsp> responseObserver) {
            int amount = request.getAmount();
            int id     = request.getAccountId();
            System.out.println("SERVER: Account " + id + ", add " + amount);

            blockBuilder.append(new DepositTx(id, amount).setResponse(responseObserver));
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
