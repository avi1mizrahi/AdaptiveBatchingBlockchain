import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


class Ledger {
    // TODO: protect both with RW lock, here or outside (probably here)
    private final ConcurrentHashMap<Account, Integer> data   = new ConcurrentHashMap<>();
    private final AtomicInteger                       lastId = new AtomicInteger();

    Account newAccount() {
        int id      = lastId.incrementAndGet();
        var account = Account.from(id);
        var old     = data.put(account, 0);
        assert old == null;
        return account;
    }

    boolean deleteAccount(Account account) {
        return null != data.remove(account);
    }

    boolean add(Account account, int amount) {
        if (amount < 0) return false;
        return null != data.computeIfPresent(account,
                                             (id, currentAmount) ->
                                                     currentAmount + amount);

    }

    boolean subtract(Account account, int amount) {
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

    @Nullable
    Integer get(Account account) {
        return data.get(account);
    }
}