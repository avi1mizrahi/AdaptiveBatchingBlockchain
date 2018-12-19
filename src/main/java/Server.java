import ClientServerCommunication.ClientServerComm;
import ClientServerCommunication.TransferGrpc;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;


public class Server {
    public static void main(String[] args) throws IOException {

        ServerBuilder.forPort(50505).addService(new TransferGrpc.TransferImplBase() {
            @Override
            public void createAccount(ClientServerComm.CreateAccountReq request,
                                      StreamObserver<ClientServerComm.CreateAccountRes> responseObserver) {
                System.out.println("SERVER: " + request);
                var account = ClientServerComm.Account.newBuilder().setId(50).build();
                var res = ClientServerComm.CreateAccountRes.newBuilder().setAccount(account).build();
                responseObserver.onNext(res);
            }

        }).build().start();
    }
}
