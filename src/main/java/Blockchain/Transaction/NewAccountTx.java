package Blockchain.Transaction;

import Blockchain.Account;
import Blockchain.Ledger;
import ServerCommunication.CreateAccountReq;
import ServerCommunication.Tx;

public class NewAccountTx extends Transaction {
    @Override
    Transaction.Result doYourThing(Ledger.State state) {
        var account = Account.from(state.allocateId());
        var old     = state.getData().put(account, 0);
        if (old != null) throw new AssertionError();
        return new Result(account);
    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.setCreate(CreateAccountReq.getDefaultInstance());
    }

    @Override
    public String toString() {
        return super.toString() + "New";
    }

    public static class Result extends Transaction.Result {
        private Account newAccount;

        private Result(Account newAccount) {
            this.newAccount = newAccount;
        }

        public Account getNewAccount() {
            return newAccount;
        }
    }
}
