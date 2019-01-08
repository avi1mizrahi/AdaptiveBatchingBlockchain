package Blockchain;

import Blockchain.Transaction.Transaction;
import ServerCommunication.BlockId;
import ServerCommunication.BlockMsg;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Block {
    private class TxEntry {
        private final Transaction        tx;
        private       Transaction.Result result = null;

        TxEntry(Transaction tx) {
            this.tx = tx;
        }

        @Nullable
        Transaction.Result getResult() {
            return result;
        }

        Transaction getTx() {
            return tx;
        }
    }

    private final BlockId       blockId;
    private final List<TxEntry> txs;

    Block(BlockId blockId, Stream<Transaction> txs) {
        this.blockId = blockId;
        this.txs = txs.map(TxEntry::new).collect(Collectors.toUnmodifiableList());
    }

    static Block from(BlockMsg blockMsg) {
        return new Block(blockMsg.getId(), blockMsg.getTxsList().stream().map(Transaction::from));
    }

    void applyTo(Ledger ledger) {
        txs.forEach(entry -> entry.result = entry.tx.process(ledger));
    }

    public static class TxStatus {
        private TxStatus(TxId txId, Transaction.Result result) {
            this.txId = txId;
            this.result = result;
        }

        public TxId               txId;
        public Transaction.Result result;
    }

    List<TxStatus> getResults() {
        List<TxStatus> list = new ArrayList<>();

        for (int i = 0; i < txs.size(); i++) {
            list.add(new TxStatus(new TxId(blockId.getServerId(), blockId.getSerialNumber(), i),
                                  txs.get(i).getResult()));
        }
        return list;
    }

    void addToBlockMsg(BlockMsg.Builder blockBuilder) {
        var txMsgs = txs.stream()
                        .map(TxEntry::getTx)
                        .map(Transaction::toTxMsg)
                        .collect(Collectors.toList());
        blockBuilder.addAllTxs(txMsgs);
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(TxEntry::toString)
                  .collect(Collectors.joining("\n",
                                              "+++BLOCK+++ [size=" + txs.size() + "]\n",
                                              "\n---BLOCK---"));
    }
}

class BlockBuilder {
    private       ConcurrentLinkedQueue<Transaction> txs           = new ConcurrentLinkedQueue<>();
    private final ReadWriteLock                      readWriteLock = new ReentrantReadWriteLock();

    private final int id;
    private       int blockIdx = 0;
    private       int txIdx    = 0;

    BlockBuilder(int id) {
        this.id = id;
    }

    boolean isEmpty() {
        return txs.isEmpty();
    }

    TxId append(Transaction tx) {
        TxId txId;
        try (var ignored = CriticalSection.start(readWriteLock.readLock())) {
            txId = new TxId(this.id, blockIdx, txIdx++);
            txs.add(tx);
        }
        return txId;
    }

    Block seal() {
        ConcurrentLinkedQueue<Transaction> newList = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Transaction> oldList;

        try (var ignored = CriticalSection.start(readWriteLock.writeLock())) {
            oldList = txs;
            txs = newList;
            blockIdx++;
            txIdx = 0;
        }

        return new Block(BlockId.newBuilder().setServerId(id).setSerialNumber(blockIdx).build(),
                         oldList.stream());
    }

}
