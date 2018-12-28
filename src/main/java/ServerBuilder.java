import java.time.Duration;

public class ServerBuilder {
    private int      clientPort;
    private int      serverPort;
    private Duration blockWindow = Duration.ofSeconds(1);

    public ServerBuilder setClientPort(int port) {
        this.clientPort = port;
        return this;
    }

    public ServerBuilder setServerPort(int port) {
        this.serverPort = port;
        return this;
    }

    public ServerBuilder setBlockWindow(Duration blockWindow) {
        this.blockWindow = blockWindow;
        return this;
    }

    public Server createServer() {
        return new Server(clientPort, serverPort, blockWindow);
    }
}