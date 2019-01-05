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

    abstract void stop();
}

class FixedWindowBatching extends BatchingStrategy {
    private final AtomicBoolean terminating = new AtomicBoolean(false);
    private final Thread        appender;

    FixedWindowBatching(Runnable doBatch, Duration blockWindow) {
        super(doBatch);

        appender = new Thread(() -> {
            while (!terminating.getAcquire()) { // must use acquire semantics, as long as we don't lock inside block.isEmpty()
                batch();

                try {
                    Thread.sleep(blockWindow.toMillis());
                } catch (InterruptedException ignored) {
                }
            }

            batch();
        });
    }

    @Override
    void start() {
        appender.start();
    }

    @Override
    void stop() {
        terminating.setRelease(true);

        try {
            appender.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
