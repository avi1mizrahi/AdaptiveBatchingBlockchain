package Blockchain;

import ServerCommunication.BlockMsg;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Block {
    private final List<Transaction> txs;

    Block(Stream<Transaction> txs) {
        this.txs = txs.collect(Collectors.toUnmodifiableList());
    }

    static Block from(BlockMsg blockMsg) {
        return new Block(blockMsg.getTxsList().stream().map(Transaction::from));
    }

    List<Transaction.Result> applyTo(Ledger ledger) {
        return txs.stream()
                  .map(transaction -> transaction.process(ledger))
                  .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(Transaction::toString)
                  .collect(Collectors.joining("\n",
                                              "+++BLOCK+++ [size=" + txs.size() + "]\n",
                                              "\n---BLOCK---"));
    }

    void addToBlockMsg(BlockMsg.Builder blockBuilder) {
        var txMsgs = txs.stream().map(Transaction::toTxMsg).collect(Collectors.toList());
        blockBuilder.addAllTxs(txMsgs);
    }
}

@ThreadSafe
class BlockBuilder {
    private final ReadWriteLock                      readWriteLock = new ReentrantReadWriteLock();
    private       ConcurrentLinkedQueue<Transaction> txs           = new ConcurrentLinkedQueue<>();
    private final AtomicInteger                      myTxNum       = new AtomicInteger(0);
    private final int                                id;

    BlockBuilder(int id) {
        this.id = id;
    }

    boolean isEmpty() {
        return txs.isEmpty();
    }

    TxId append(Transaction tx) {
        TxId txId = new TxId(this.id, myTxNum.incrementAndGet());
        try (var ignored = CriticalSection.start(readWriteLock.readLock())) {
            txs.add(tx);
        }
        tx.setId(txId);
        return txId;
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
