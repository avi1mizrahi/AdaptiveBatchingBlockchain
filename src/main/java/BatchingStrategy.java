import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class BatchingStrategy {
    private final Runnable doBatch;

    BatchingStrategy(Runnable doBatch) {
        this.doBatch = doBatch;
    }

    void batch() {
        doBatch.run();
    }

    void onRequestBegin() {
    }

    void onRequestEnd() {
    }

    abstract void start();

    abstract void shutdown();
}

class TimedAdaptiveBatching extends BatchingStrategy {
    private final AtomicBoolean isVisited      = new AtomicBoolean(false);
    private final Thread        appender;
    private       int           skippedWindows = 0;

    TimedAdaptiveBatching(Runnable doBatch, Duration blockWindow) {
        this(doBatch, blockWindow, 5);
    }

    TimedAdaptiveBatching(Runnable doBatch, Duration blockWindow, int windowsLimit) {
        super(doBatch);

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
                batch();
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

    FixedWindowBatching(Runnable doBatch, Duration blockWindow) {
        super(doBatch, blockWindow, 1);
    }
}


