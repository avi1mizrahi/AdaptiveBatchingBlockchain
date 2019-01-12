package Blockchain;

import Blockchain.Batch.AdaptiveBatching;
import Blockchain.Batch.BatchingStrategy;

import java.net.InetSocketAddress;
import java.util.MissingResourceException;

public class ServerBuilder {
    private InetSocketAddress address          = null;
    private int               id               = -1;
    private int               faultSetSize     = 2;
    private BatchingStrategy  batchingStrategy = new AdaptiveBatching();

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

    public ServerBuilder setServerAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public Server createServer() {
        System.out.println("Creating new server");
        if (id == -1) throw new MissingResourceException("missing id", int.class.getName(), "");
        if (address == null) throw new MissingResourceException("missing address", int.class.getName(), "");

        return new Server(id, address, batchingStrategy, faultSetSize);
    }
}