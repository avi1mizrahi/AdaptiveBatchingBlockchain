package Blockchain.Batch;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimedAdaptiveBatching extends BatchingStrategy {
    private final AtomicBoolean isVisited      = new AtomicBoolean(false);
    private final Thread        appender;
    private       int           skippedWindows = 0;

    public TimedAdaptiveBatching(Duration blockWindow) {
        this(blockWindow, 5);
    }

    public TimedAdaptiveBatching(Duration blockWindow, int windowsLimit) {
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
                    interrupted();
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
    public void start(BatcherProxy batcherProxy) {
        super.start(batcherProxy);
        appender.start();
    }

    @Override
    public void shutdown() {
        appender.interrupt();

        try {
            appender.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
