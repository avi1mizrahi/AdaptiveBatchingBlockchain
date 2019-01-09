package Blockchain;

import ServerCommunication.ServerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

class PeerServer {
    private final ServerGrpc.ServerStub stub;
    private final ManagedChannel        channel;

    PeerServer(@NotNull InetSocketAddress address) {
        channel = ManagedChannelBuilder.forAddress(address.getHostName(), address.getPort())
                                       .usePlaintext()
                                       .build();
        stub = ServerGrpc.newStub(channel);
    }

    void shutdown() {
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    ServerGrpc.ServerStub stub() {
        return stub;
    }
}
