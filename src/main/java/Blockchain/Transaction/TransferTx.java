package Blockchain.Transaction;

import Blockchain.Account;
import Blockchain.Ledger;
import ServerCommunication.Tx;

public class TransferTx extends Transaction {
    private final Account from;
    private final Account to;
    private final int     amount;

    public TransferTx(Account from, Account to, int amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        if (amount < 0) throw new RuntimeException("amount " + amount + " is negative");
    }

    @Override
    Transaction.Result doYourThing(Ledger.State state) {
        boolean success = transfer(state);

        return new Result(success);
    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.getTransferBuilder()
                 .setFromId(from.getId())
                 .setToId(to.getId())
                 .setAmount(amount);
    }

    @Override
    public String toString() {
        return super.toString() + "Transfer[" + from + "->" + to + "]+" + amount;
    }

    private boolean transfer(Ledger.State state) {
        if (state.subtract(from, amount)) {
            if (state.add(to, amount)) {
                return true;
            }
            state.add(from, amount);//return the stolen money
        }
        return false;
    }

    static class Result extends Transaction.Result {
        private boolean committed;

        Result(boolean committed) {
            this.committed = committed;
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }
    }
}
