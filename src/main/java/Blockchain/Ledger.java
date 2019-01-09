package Blockchain;

import Blockchain.Transaction.Transaction;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;


@ThreadSafe
public
class Ledger {
    private final ConcurrentMap<BlockId, Block> chain = new ConcurrentHashMap<>();
    private final AtomicReference<State>        state = new AtomicReference<>(new State());

    public int chainSize() {
        return chain.size();
    }

    public static class State {
        private final HashMap<Account, Integer> data;
        private       int                       lastId;

        private State() {
            this(new HashMap<>(), 0);
        }

        private State(HashMap<Account, Integer> data, int lastId) {
            this.data = data;
            this.lastId = lastId;
        }

        public boolean add(Account account, int amount) {
            if (amount < 0) return false;
            return null != data.computeIfPresent(account,
                                                 (id, currentAmount) ->
                                                         currentAmount + amount);
        }

        public boolean subtract(Account from, int amount) {
            if (amount < 0) return false;

            final boolean[] executed = {false};

            data.computeIfPresent(from, (id, currentAmount) -> {
                var newAmount = currentAmount - amount;
                if (newAmount < 0) return currentAmount;
                executed[0] = true;
                return newAmount;
            });

            return executed[0];
        }

        public int allocateId() {
            return ++lastId;
        }

        public Map<Account, Integer> getData() {
            return data;
        }

        State fork() {
            return new State(new HashMap<>(data), lastId);
        }
    }

    @Nullable
    Integer get(Account account) {
        return state.getAcquire().getData().get(account);
    }

    synchronized void apply(Block block) {
        State forked = state.get().fork();
        block.applyTo(forked);
        state.setRelease(forked);
        chain.put(block.getId(), block);
    }

    Optional<Transaction.Result> getStatus(TxId txId) {
        Block block = chain.get(BlockId.from(txId));
        if (block == null) return Optional.empty();

        return Optional.of(block.getResult(txId));
    }
}