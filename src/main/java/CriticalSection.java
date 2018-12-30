import java.io.Closeable;
import java.util.concurrent.locks.Lock;

class CriticalSection implements Closeable {
    private final Lock lock;

    private CriticalSection(Lock lock) {
        this.lock = lock;
        lock.lock();
    }

    static CriticalSection start(Lock lock) {
        return new CriticalSection(lock);
    }

    @Override
    public void close() {
        lock.unlock();
    }
}
