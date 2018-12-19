import ClientServerCommunication.ClientServerComm;
import ClientServerCommunication.TransferGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class Client {
    public static void main(String[] args) {
        TransferGrpc.TransferBlockingStub stub;

        try {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",
                                                                      50505)
                                                          .usePlaintext()
                                                          .build();
            stub = TransferGrpc.newBlockingStub(channel);
        } catch (StatusRuntimeException e) {
            e.printStackTrace();
            return;
        }

        ClientServerComm.CreateAccountReq request = ClientServerComm.CreateAccountReq.newBuilder()
                                                                                     .build();
        ClientServerComm.CreateAccountRes response;

        try {
            response = stub.createAccount(request);
        } catch (StatusRuntimeException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("CLIENT: " + response);
    }
}
