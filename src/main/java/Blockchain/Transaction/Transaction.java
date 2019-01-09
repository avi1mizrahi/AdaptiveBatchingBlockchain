package Blockchain.Transaction;

import Blockchain.Account;
import Blockchain.Ledger;
import ServerCommunication.Tx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public abstract class Transaction {
    // This is ugly
    @NotNull
    @Contract("_ -> new")
    public static Transaction from(@NotNull Tx tx) {
        switch (tx.getTxTypeCase()) {
            case CREATE:
                return new NewAccountTx();
            case DELETE:
                return new DeleteAccountTx(Account.from(tx.getDelete().getAccountId()));
            case ADDAMOUNT:
                var addTx = tx.getAddAmount();
                return new DepositTx(Account.from(addTx.getAccountId()), addTx.getAmount());
            case TRANSFER:
                var transferTx = tx.getTransfer();
                return new TransferTx(Account.from(transferTx.getFromId()),
                                      Account.from(transferTx.getToId()),
                                      transferTx.getAmount());
            default:
                throw new RuntimeException();
        }
    }

    public Result process(Ledger.State state) {
        return doYourThing(state);
    }

    abstract Result doYourThing(Ledger.State state);

    public final Tx toTxMsg() {
        var builder = Tx.newBuilder();
        addToMsg(builder);
        return builder.build();
    }

    abstract void addToMsg(Tx.Builder txBuilder);

    @Override
    public String toString() {
        return "[TX]:";
    }

    public static class Result {
        public boolean isCommitted() {
            return true;
        }
    }
}

