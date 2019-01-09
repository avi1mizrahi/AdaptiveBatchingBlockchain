package Blockchain;

import java.time.Duration;

public class ServerBuilder {
    private int      serverPort  = -1;
    private Duration blockWindow = Duration.ofMillis(100);
    private int      id          = -1;

    public ServerBuilder setId(int id) {
        this.id = id;
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
        assert id != -1  && serverPort != -1;
        return new Server(id, serverPort, blockWindow);
    }
}