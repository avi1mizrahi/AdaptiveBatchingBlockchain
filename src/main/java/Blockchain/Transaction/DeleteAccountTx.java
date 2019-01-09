package Blockchain.Transaction;

import Blockchain.Account;
import Blockchain.Ledger;
import ServerCommunication.Tx;

public class DeleteAccountTx extends Transaction {
    private final Account account;

    public DeleteAccountTx(Account account) {
        this.account = account;
    }

    @Override
    Transaction.Result doYourThing(Ledger.State state) {
        state.getData().remove(account);
        return new Result();
    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.getDeleteBuilder().setAccountId(account.getId());
    }

    @Override
    public String toString() {
        return super.toString() + "Delete[" + account + "]";
    }

    static class Result extends Transaction.Result {
    }
}
