package Blockchain;

import Blockchain.Transaction.Transaction;
import ServerCommunication.BlockId;
import ServerCommunication.BlockMsg;
import ServerCommunication.Tx;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
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

        @Override
        public String toString() {
            char status;
            if (result == null) {
                status = ' ';
            } else if (result.isCommitted()) {
                status = 'V';
            } else {
                status = 'X';
            }
            return String.format("[%c]%s", status, tx.toString());
        }
    }

    private final BlockId       blockId;
    private final List<TxEntry> txs;

    public BlockId getId() {
        return blockId;
    }

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

    Map<TxId, Transaction.Result> getResults() {
        HashMap<TxId, Transaction.Result> map = new HashMap<>();

        for (int i = 0; i < txs.size(); i++) {
            map.put(new TxId(blockId.getServerId(), blockId.getSerialNumber(), i),
                    txs.get(i).getResult());
        }
        return map;
    }

    BlockMsg toBlockMsg() {
        Stream<Tx> txMsgs = txs.stream()
                               .map(TxEntry::getTx)
                               .map(Transaction::toTxMsg);

        return BlockMsg.newBuilder().setId(blockId).addAllTxs(txMsgs::iterator).build();
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(TxEntry::toString)
                  .collect(Collectors.joining("\n",
                                              "+++BLOCK+++ [" +
                                                      getId() + "][size=" + txs.size() + "]\n",
                                              "\n---BLOCK---"));
    }
}

class BlockBuilder {
    private       ConcurrentLinkedQueue<Transaction> txs           = new ConcurrentLinkedQueue<>();
    private final ReadWriteLock                      readWriteLock = new ReentrantReadWriteLock();

    private final int           id;
    private       int           blockIdx = 0;
    private       AtomicInteger txIdx    = new AtomicInteger(0);

    BlockBuilder(int id) {
        this.id = id;
    }

    boolean isEmpty() {
        return txs.isEmpty();
    }

    TxId append(Transaction tx) {
        try (var ignored = CriticalSection.start(readWriteLock.readLock())) {
            TxId txId = new TxId(this.id, blockIdx, txIdx.getAndIncrement());
            txs.add(tx);
            return txId;
        }
    }

    Block seal() {
        ConcurrentLinkedQueue<Transaction> newList = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Transaction> oldList;

        int prevBlockIdx;

        try (var ignored = CriticalSection.start(readWriteLock.writeLock())) {
            oldList = txs;
            txs = newList;
            prevBlockIdx = blockIdx++;
            txIdx.set(0);
        }

        return new Block(BlockId.newBuilder().setServerId(id).setSerialNumber(prevBlockIdx).build(),
                         oldList.stream());
    }

}
