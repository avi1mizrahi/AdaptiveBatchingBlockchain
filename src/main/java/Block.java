import ServerCommunication.BlockMsg;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
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

    void applyTo(Ledger ledger) {
        txs.forEach(transaction -> transaction.process(ledger));
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(Transaction::toString)
                  .collect(Collectors.joining("\n",
                                              "+++BLOCK+++ [size=" + txs.size() + "]\n",
                                              "\n---BLOCK---"));
    }

    static Block from(BlockMsg blockMsg) {
        return new Block(blockMsg.getTxsList().stream().map(Transaction::from));
    }

    void addToBlockMsg(BlockMsg.Builder blockBuilder) {
        var txMsgs = txs.stream().map(Transaction::toTxMsg).collect(Collectors.toList());
        blockBuilder.addAllTxs(txMsgs);
    }
}

@ThreadSafe
class BlockBuilder {
    private ConcurrentLinkedQueue<Transaction> txs           = new ConcurrentLinkedQueue<>();
    private ReadWriteLock                      readWriteLock = new ReentrantReadWriteLock();

    boolean isEmpty() {
        return txs.isEmpty();
    }

    void append(Transaction tx) {
        try (var ignored = CriticalSection.start(readWriteLock.readLock())) {
            txs.add(tx);
        }

    }

    Block seal() {
        ConcurrentLinkedQueue<Transaction> newList = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Transaction> oldList;

        try (var ignored = CriticalSection.start(readWriteLock.writeLock())) {
            oldList = txs;
            txs = newList;
        }

        return new Block(oldList.stream());
    }

}
