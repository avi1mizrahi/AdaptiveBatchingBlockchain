import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


class Ledger {
    // TODO: protect both with RW lock, here or outside (probably here)
    private final ConcurrentHashMap<Integer, Integer> data   = new ConcurrentHashMap<>();
    private final AtomicInteger                       lastId = new AtomicInteger();

    int newAccount() {
        int id = lastId.incrementAndGet();
        var old = data.put(id, 0);
        assert old == null;
        return id;
    }

    boolean deleteAccount(int accountId) {
        return null != data.remove(accountId);
    }

    boolean add(int accountId, int amount) {
        if (amount < 0) return false;
        return null != data.computeIfPresent(accountId,
                                             (id, currentAmount) ->
                                                     currentAmount + amount);

    }

    boolean subtract(int accountId, int amount) {
        var ref = new Object() {
            boolean executed = false;
        };

        data.computeIfPresent(accountId, (id_, currentAmount) -> {
            var newAmount = currentAmount - amount;
            if (newAmount < 0) return currentAmount;
            ref.executed = true;
            return newAmount;
        });
        return ref.executed;

    }

    @Nullable
    Integer get(int accountId) {
        return data.get(accountId);
    }
}