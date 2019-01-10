package Blockchain.Batch;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

public abstract class BatchingStrategy {
    private BatcherProxy batcherProxy;

    void batch() throws InterruptedException {
        batcherProxy.batch();
    }

    void interrupted() {
        batcherProxy.interrupted();
    }

    void onRequestBegin() {
    }

    void onRequestEnd() {
    }

    public void start(BatcherProxy batcherProxy) {
        this.batcherProxy = batcherProxy;
    }

    public abstract void shutdown();

    public RequestWindow createRequestWindow() {
        return new RequestWindow(this);
    }

    public static class RequestWindow implements Closeable {
        private final BatchingStrategy strategy;

        private RequestWindow(@NotNull BatchingStrategy strategy) {
            this.strategy = strategy;
            strategy.onRequestBegin();
        }

        @Override
        public void close() {
            strategy.onRequestEnd();
        }
    }
}


