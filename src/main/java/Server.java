import ClientServerCommunication.Account;
import ClientServerCommunication.CreateAccountReq;
import ClientServerCommunication.CreateAccountRes;
import ClientServerCommunication.TransferGrpc;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;


public class Server {
    public static void main(String[] args) throws IOException, InterruptedException {

        ServerBuilder.forPort(50505).addService(new TransferGrpc.TransferImplBase() {
            @Override
            public void createAccount(CreateAccountReq request,
                                      StreamObserver<CreateAccountRes> responseObserver) {
                System.out.println("SERVER: " + request);
                var account = Account.newBuilder().setId(50).build();
                var res = CreateAccountRes.newBuilder().setAccount(account).build();
                responseObserver.onNext(res);
                responseObserver.onCompleted();
            }

        }).build().start().awaitTermination();
    }
}
