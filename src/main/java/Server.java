import ClientServerCommunication.*;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private final ConcurrentHashMap<Integer, Integer> data   = new ConcurrentHashMap<>();
    private       AtomicInteger                       lastId = new AtomicInteger();
    private final io.grpc.Server                      clientListener;

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
            if (data.putIfAbsent(id, 0) == null) { //new account
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
            data.remove(accountId);

            responseObserver.onNext(DeleteAccountRsp.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void addAmount(AddAmountReq request, StreamObserver<AddAmountRsp> responseObserver) {
            int amount = request.getAmount();
            int id     = request.getAccountId();
            System.out.println("SERVER: Account " + id + ", add " + amount);
            var rspBuilder = AddAmountRsp.newBuilder();

            rspBuilder.setSuccess(add(amount, id));

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void getAmount(GetAmountReq request, StreamObserver<GetAmountRsp> responseObserver) {
            int id = request.getAccountId();
            System.out.println("SERVER: getAmount " + id);
            var rspBuilder = GetAmountRsp.newBuilder();

            var amount = data.get(id);
            if (amount != null) {
                rspBuilder.setAmount(amount);
                rspBuilder.setSuccess(true);
                System.out.println("SERVER: Amount of " + id + " is " + amount);
            }

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void transfer(TransactionReq request,
                             StreamObserver<TransactionRsp> responseObserver) {
            int amount = request.getAmount();
            int from   = request.getFromId();
            int to     = request.getToId();
            System.out.println("SERVER: from " + from + " to " + to + " : " + amount);
            var rspBuilder = TransactionRsp.newBuilder();

            var ref = new Object() {
                boolean executed;
            };

            data.computeIfPresent(from,
                                  (id_, currentAmount) ->
                                  {
                                      var newAmount = currentAmount - amount;
                                      if (newAmount < 0) return currentAmount;
                                      ref.executed = true;
                                      return newAmount;
                                  });
            if (ref.executed)
                rspBuilder.setSuccess(add(amount, to));

//            Integer currentAmount;
//            Integer newAmount = -1;
//            do {
//                currentAmount = data.get(from);
//                if (currentAmount == null) break;
//                newAmount = currentAmount - amount;
//            } while (newAmount >= 0 && !data.replace(from, currentAmount, newAmount));
//
//            if (currentAmount != null && newAmount >= 0) {
//                rspBuilder.setSuccess(add(amount, to));
//            }

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }

        private boolean add(int amount, int to) {
            Integer newValue = data.computeIfPresent(to,
                                                     (id_, currentAmount) ->
                                                             currentAmount + amount);

            if (newValue != null) {
                System.out.println("SERVER: Account " + to + " now has " + newValue);
                return true;
            }
            return false;
        }
    }
}
