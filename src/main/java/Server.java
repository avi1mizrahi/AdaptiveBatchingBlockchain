import ClientServerCommunication.*;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private final Ledger         ledger = new Ledger();
    private       AtomicInteger  lastId = new AtomicInteger();
    private final io.grpc.Server clientListener;

    public static void main(String[] args) throws IOException, InterruptedException {
        Integer port = 55555;
        if (args.length >= 1) port = Integer.valueOf(args[0]);
        var server = new Server(port);
        server.awaitTermination();
    }

    Server(int port) throws IOException {
        clientListener = ServerBuilder.forPort(port).addService(new ClientRpc()).build().start();
    }

    void awaitTermination() throws InterruptedException {
        clientListener.awaitTermination();
    }

    void shutdown() {
        clientListener.shutdown();
    }

    private class ClientRpc extends ClientGrpc.ClientImplBase {
        @Override
        public void createAccount(CreateAccountReq request,
                                  StreamObserver<CreateAccountRsp> responseObserver) {
            System.out.println("SERVER: Create account");
            var rspBuilder = CreateAccountRsp.newBuilder();

            int id = lastId.incrementAndGet();
            if (ledger.newAccount(id)) { //new account
                rspBuilder.setId(id).setSuccess(true);
                System.out.println("SERVER: Created ID:" + id);
            }

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void deleteAccount(DeleteAccountReq request,
                                  StreamObserver<DeleteAccountRsp> responseObserver) {
            int accountId = request.getAccountId();
            System.out.println("SERVER: Delete account:" + accountId);
            ledger.deleteAccount(accountId);

            responseObserver.onNext(DeleteAccountRsp.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void addAmount(AddAmountReq request, StreamObserver<AddAmountRsp> responseObserver) {
            int amount = request.getAmount();
            int id     = request.getAccountId();
            System.out.println("SERVER: Account " + id + ", add " + amount);
            var rspBuilder = AddAmountRsp.newBuilder();

            rspBuilder.setSuccess(ledger.add(id, amount));

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
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
            var rspBuilder = TransferRsp.newBuilder();

            if (ledger.subtract(from, amount))
                rspBuilder.setSuccess(ledger.add(to, amount));

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

    }
}
