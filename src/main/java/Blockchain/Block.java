package Blockchain;

import Blockchain.Transaction.Transaction;
import ServerCommunication.BlockMsg;
import ServerCommunication.Tx;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BlockId {
    private final int serverId;
    private final int serialNum;

    public int getServerId() {
        return serverId;
    }

    public int getSerialNum() {
        return serialNum;
    }

    private BlockId(int serverId, int serialNum) {
        this.serverId = serverId;
        this.serialNum = serialNum;
    }

    public static BlockId from(int serverId, int serialNum) {
        return new BlockId(serverId, serialNum);
    }

    public static BlockId from(ServerCommunication.BlockId blockIdMsg) {
        return new BlockId(blockIdMsg.getServerId(), blockIdMsg.getSerialNumber());
    }

    public ServerCommunication.BlockId toBlockIdMsg() {
        return ServerCommunication.BlockId.newBuilder()
                                          .setServerId(serverId)
                                          .setSerialNumber(serialNum)
                                          .build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, serialNum);
    }

    public boolean equals(BlockId other) {
        return this.serverId == other.serverId &&
                this.serialNum == other.serialNum;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BlockId && equals((BlockId) obj);
    }

    @Override
    public String toString() {
        return String.format("[BlockId(sid=%d, i=%d)]", serverId, serialNum);
    }
}

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
        return new Block(BlockId.from(blockMsg.getId()),
                         blockMsg.getTxsList().stream().map(Transaction::from));
    }

    void applyTo(Ledger ledger) {
        txs.forEach(entry -> entry.result = entry.tx.process(ledger));
    }

    Map<TxId, Transaction.Result> getResults() {
        HashMap<TxId, Transaction.Result> map = new HashMap<>();

        for (int i = 0; i < txs.size(); i++) {
            map.put(new TxId(blockId.getServerId(), blockId.getSerialNum(), i),
                    txs.get(i).getResult());
        }
        return map;
    }

    BlockMsg toBlockMsg() {
        Stream<Tx> txMsgs = txs.stream()
                               .map(TxEntry::getTx)
                               .map(Transaction::toTxMsg);

        return BlockMsg.newBuilder()
                       .setId(blockId.toBlockIdMsg())
                       .addAllTxs(txMsgs::iterator)
                       .build();
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(TxEntry::toString)
                  .collect(Collectors.joining("\n",
                                              "+++BLOCK+++ [" +
                                                      blockId + "][size=" + txs.size() + "]\n",
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

        return new Block(BlockId.from(id, prevBlockIdx), oldList.stream());
    }

}
