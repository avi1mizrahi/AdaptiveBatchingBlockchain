import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Block {
    private final List<Transaction> txs;

    Block(List<Transaction> txs) {
        this.txs = txs;
    }

    void apply(Ledger ledger) {
        txs.forEach(transaction -> transaction.process(ledger));
    }

    @Override
    public String toString() {
        return txs.stream()
                  .map(Transaction::toString)
                  .collect(Collectors.joining("\n", "+++BLOCK+++\n", "\n---BLOCK---"));
    }
}

class BlockBuilder {
    private List<Transaction> txs = new ArrayList<>();

    boolean isEmpty() {
        return txs.isEmpty();
    }

    void append(Transaction tx) {
        txs.add(tx);
    }

    Block seal() {
        var block = new Block(txs);
        txs = new ArrayList<>();
        return block;
    }

}
