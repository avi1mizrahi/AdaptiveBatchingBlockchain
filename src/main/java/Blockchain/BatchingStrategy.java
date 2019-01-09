package Blockchain;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

interface BatcherProxy {
    void batch() throws InterruptedException;

    void interrupted();
}

abstract class BatchingStrategy {
    private final BatcherProxy batcherProxy;

    BatchingStrategy(BatcherProxy batcherProxy) {
        this.batcherProxy = batcherProxy;
    }

    void batch() throws InterruptedException {
        batcherProxy.batch();
    }

    void onRequestBegin() {
    }

    void onRequestEnd() {
    }

    abstract void start();

    abstract void shutdown();

    public RequestWindow createRequestWindow() {
        return new RequestWindow(this);
    }

    static class RequestWindow implements Closeable {
        private final BatchingStrategy strategy;

        private RequestWindow(BatchingStrategy strategy) {
            this.strategy = strategy;
            strategy.onRequestBegin();
        }

        @Override
        public void close() {
            strategy.onRequestEnd();
        }
    }
}

class TimedAdaptiveBatching extends BatchingStrategy {
    private final AtomicBoolean isVisited      = new AtomicBoolean(false);
    private final Thread        appender;
    private       int           skippedWindows = 0;

    TimedAdaptiveBatching(BatcherProxy batcherProxy, Duration blockWindow) {
        this(batcherProxy, blockWindow, 5);
    }

    TimedAdaptiveBatching(BatcherProxy batcherProxy, Duration blockWindow, int windowsLimit) {
        super(batcherProxy);

        appender = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    Thread.sleep(blockWindow.toMillis());
                } catch (InterruptedException ignored) {
                    break;
                }

                boolean isVisited = this.isVisited.getAndSet(false);

                if (isVisited && ++skippedWindows < windowsLimit) {
                    continue;
                }

                skippedWindows = 0;

                try {
                    batch();
                } catch (InterruptedException e) {
                    System.err.println("batching was interrupted");
                    e.printStackTrace();
                    batcherProxy.interrupted();
                    break;
                }
            }
        });
    }

    @Override
    void onRequestEnd() {
        isVisited.setRelease(true);
    }

    @Override
    void start() {
        appender.start();
    }

    @Override
    void shutdown() {
        appender.interrupt();

        try {
            appender.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class FixedWindowBatching extends TimedAdaptiveBatching {

    FixedWindowBatching(BatcherProxy batcherProxy, Duration blockWindow) {
        super(batcherProxy, blockWindow, 1);
    }
}


