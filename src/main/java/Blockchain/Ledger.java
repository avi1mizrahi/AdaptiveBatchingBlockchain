package Blockchain;

import Blockchain.Transaction.Transaction;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ThreadSafe
public
class Ledger {
    private final ReadWriteLock                 lock   = new ReentrantReadWriteLock();
    private final ConcurrentMap<BlockId, Block> chain  = new ConcurrentHashMap<>();
    private final HashMap<Account, Integer>     data   = new HashMap<>();
    private       int                           lastId = 0;

    public int chainSize() {
        return chain.size();
    }

    public Account newAccount() {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            int id      = ++lastId;
            var account = Account.from(id);
            var old     = data.put(account, 0);
            assert old == null;
            return account;
        }
    }

    public boolean deleteAccount(Account account) {
        return null != data.remove(account);
    }

    public boolean add(Account account, int amount) {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            if (amount < 0) return false;
            return null != data.computeIfPresent(account,
                                                 (id, currentAmount) ->
                                                         currentAmount + amount);
        }

    }

    public boolean subtract(Account account, int amount) {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            var ref = new Object() {
                boolean executed = false;
            };

            data.computeIfPresent(account, (id_, currentAmount) -> {
                var newAmount = currentAmount - amount;
                if (newAmount < 0) return currentAmount;
                ref.executed = true;
                return newAmount;
            });
            return ref.executed;
        }

    }

    @Nullable
    Integer get(Account account) {
        try (var ignored = CriticalSection.start(lock.readLock())) {
            return data.get(account);
        }
    }

    void apply(Block block) {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            block.applyTo(this);
            chain.put(block.getId(), block);
        }
    }

    Optional<Transaction.Result> getStatus(TxId txId) {
        Block block = chain.get(BlockId.from(txId));
        if (block == null) return Optional.empty();

        return Optional.of(block.getResult(txId));
    }
}