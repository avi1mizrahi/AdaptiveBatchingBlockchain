package Blockchain.Transaction;

import Blockchain.Account;
import Blockchain.Ledger;
import ServerCommunication.Tx;

public class DepositTx extends Transaction {
    private final Account account;
    private final int     amount;

    public DepositTx(Account account, int amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    Transaction.Result doYourThing(Ledger ledger) {
        boolean success = ledger.add(account, amount);

        return new Result(success);
    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.getAddAmountBuilder().setAccountId(account.getId()).setAmount(amount);
    }

    @Override
    public String toString() {
        return super.toString() + "Deposit[" + account + "]+" + amount;
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
