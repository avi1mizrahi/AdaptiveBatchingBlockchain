package Blockchain;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ThreadSafe
class Ledger {
    private final ReadWriteLock             lock   = new ReentrantReadWriteLock();
    private final List<Block>               chain  = new ArrayList<>();
    private final HashMap<Account, Integer> data   = new HashMap<>();
    private       int                       lastId = 0;

    Account newAccount() {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            int id      = ++lastId;
            var account = Account.from(id);
            var old     = data.put(account, 0);
            assert old == null;
            return account;
        }
    }

    boolean deleteAccount(Account account) {
        return null != data.remove(account);
    }

    boolean add(Account account, int amount) {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            if (amount < 0) return false;
            return null != data.computeIfPresent(account,
                                                 (id, currentAmount) ->
                                                         currentAmount + amount);
        }

    }

    boolean subtract(Account account, int amount) {
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

    List<Transaction.Result> apply(Block block) {
        try (var ignored = CriticalSection.start(lock.writeLock())) {
            List<Transaction.Result> results = block.applyTo(this);
            chain.add(block);
            return results;
        }
    }
}