package Blockchain;

import Blockchain.Batch.AdaptiveBatching;
import Blockchain.Batch.BatchingStrategy;

import java.util.MissingResourceException;

public class ServerBuilder {
    private int serverPort   = -1;
    private int id           = -1;
    private int faultSetSize = 3;
    private BatchingStrategy batchingStrategy = new AdaptiveBatching();

    public ServerBuilder setFaultSetSize(int faultSetSize) {
        this.faultSetSize = faultSetSize;
        return this;
    }

    public ServerBuilder setBatchingStrategy(BatchingStrategy batchingStrategy) {
        this.batchingStrategy = batchingStrategy;
        return this;
    }

    public ServerBuilder setId(int id) {
        this.id = id;
        return this;
    }

    public ServerBuilder setServerPort(int port) {
        this.serverPort = port;
        return this;
    }

    public Server createServer() {
        if (id == -1) throw new MissingResourceException("unset id", int.class.getName(), "");
        if (serverPort == -1) throw new MissingResourceException("unset port", int.class.getName(), "");

        return new Server(id, serverPort, batchingStrategy, faultSetSize);
    }
}