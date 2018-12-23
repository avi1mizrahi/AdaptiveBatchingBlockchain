import java.time.Duration;

public class ServerBuilder {
    private int      port;
    private Duration blockWindow = Duration.ofSeconds(1);

    public ServerBuilder setPort(int port) {
        this.port = port;
        return this;
    }

    public ServerBuilder setBlockWindow(Duration blockWindow) {
        this.blockWindow = blockWindow;
        return this;
    }

    public Server createServer() {
        return new Server(port, blockWindow);
    }
}