import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;


class Ledger {
    private final ConcurrentHashMap<Integer, Integer> data = new ConcurrentHashMap<>();

    boolean newAccount(int accountId) {
        return null == data.putIfAbsent(accountId, 0);
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