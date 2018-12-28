import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Block {
    private final List<Transaction> txs;

    Block(Stream<Transaction> txs) {
        this.txs = txs.collect(Collectors.toUnmodifiableList());
    }

    void apply(Ledger ledger) {
        txs.forEach(transaction -> transaction.process(ledger));
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(Transaction::toString)
                  .collect(Collectors.joining("\n", "+++BLOCK+++\n", "\n---BLOCK---"));
    }
}

class BlockBuilder {
    private ConcurrentLinkedQueue<Transaction> txs           = new ConcurrentLinkedQueue<>();
    private ReadWriteLock                      readWriteLock = new ReentrantReadWriteLock();

    boolean isEmpty() {
        return txs.isEmpty();
    }

    void append(Transaction tx) {
        final var readLock = readWriteLock.readLock();

        readLock.lock();
        try {
            txs.add(tx);
        } finally {
            readLock.unlock();
        }

    }

    Block seal() {
        ConcurrentLinkedQueue<Transaction> newList = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Transaction> oldList;

        final var writeLock = readWriteLock.writeLock();

        writeLock.lock();
        oldList = txs;
        txs = newList;
        writeLock.unlock();

        return new Block(oldList.stream());
    }

}
