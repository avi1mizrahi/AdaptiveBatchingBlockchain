import ClientServerCommunication.ClientGrpc;
import ClientServerCommunication.CreateAccountReq;
import ClientServerCommunication.CreateAccountRsp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class Client {
    public static void main(String[] args) {
        ClientGrpc.ClientBlockingStub stub;

        try {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",
                                                                      50505)
                                                          .usePlaintext()
                                                          .build();
            stub = ClientGrpc.newBlockingStub(channel);
        } catch (StatusRuntimeException e) {
            e.printStackTrace();
            return;
        }

        CreateAccountReq request = CreateAccountReq.newBuilder().build();
        CreateAccountRsp response;

        try {
            response = stub.createAccount(request);
        } catch (StatusRuntimeException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("CLIENT: " + response);
    }
}
