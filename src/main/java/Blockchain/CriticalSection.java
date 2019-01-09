package Blockchain;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.concurrent.locks.Lock;

class CriticalSection implements Closeable {
    private final Lock lock;

    private CriticalSection(@NotNull Lock lock) {
        this.lock = lock;
        lock.lock();
    }

    @NotNull
    @Contract("_ -> new")
    static CriticalSection start(Lock lock) {
        return new CriticalSection(lock);
    }

    @Override
    public void close() {
        lock.unlock();
    }
}
