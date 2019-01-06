import ServerCommunication.ServerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

class PeerServer {
    private final ServerGrpc.ServerFutureStub stub;
    private final ManagedChannel              channel;

    PeerServer(InetSocketAddress address) {
        channel = ManagedChannelBuilder.forAddress(address.getHostName(), address.getPort())
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
}
