import ServerCommunication.ServerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;

class PeerServer {
    private final int serverId;
    private final ServerGrpc.ServerFutureStub stub;
    private final ManagedChannel channel;

    PeerServer(int serverId, String name, int port) {
        this.serverId = serverId;
        channel = ManagedChannelBuilder.forAddress(name, port)
                                       .usePlaintext()
                                       .build();
        stub = ServerGrpc.newFutureStub(channel);
    }

    void shutdown() {
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    int getServerId() {
        return serverId;
    }
}
