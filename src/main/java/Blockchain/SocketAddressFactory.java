package Blockchain;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

public class SocketAddressFactory {
    @NotNull
    @Contract(pure = true)
    static public InetSocketAddress from(String host, int port) {
        return InetSocketAddress.createUnresolved(host, port);
    }

    @NotNull
    @Contract(pure = true)
    static public InetSocketAddress from(@NotNull String address) {
        String[] data = address.split(":");
        return from(data[0], Integer.parseInt(data[1]));
    }
}
