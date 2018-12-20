import ClientServerCommunication.ClientGrpc;
import ClientServerCommunication.CreateAccountReq;
import ClientServerCommunication.CreateAccountRsp;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;


public class Server {
    public static void main(String[] args) throws IOException, InterruptedException {

        ServerBuilder.forPort(50505).addService(new ClientGrpc.ClientImplBase() {
            @Override
            public void createAccount(CreateAccountReq request,
                                      StreamObserver<CreateAccountRsp> responseObserver) {
                System.out.println("SERVER: " + request);
                var res = CreateAccountRsp.newBuilder().setId(50).build();
                responseObserver.onNext(res);
                responseObserver.onCompleted();
            }

        }).build().start().awaitTermination();
    }
}
