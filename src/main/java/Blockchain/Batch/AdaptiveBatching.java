package Blockchain.Batch;

import java.util.concurrent.atomic.AtomicInteger;

public class AdaptiveBatching extends BatchingStrategy {
    private AtomicInteger present = new AtomicInteger(0);

    @Override
    void onRequestBegin() {
        present.incrementAndGet();
    }

    @Override
    void onRequestEnd() {
        if (this.present.decrementAndGet() == 0) {
            try {
                batch();
            } catch (InterruptedException e) {
                System.err.println("batching was interrupted");
                e.printStackTrace();
                interrupted();
            }
        }

    }

    @Override
    public void start(BatcherProxy batcherProxy) {
        super.start(batcherProxy);
    }
}
