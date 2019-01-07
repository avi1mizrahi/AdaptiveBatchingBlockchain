import java.net.InetSocketAddress;

public class SocketAddressFactory {
    static public InetSocketAddress from(String host, int port) {
        return InetSocketAddress.createUnresolved(host, port);
    }

    static public InetSocketAddress from(String address) {
        String[] data = address.split(":");
        return from(data[0], Integer.parseInt(data[1]));
    }
}
