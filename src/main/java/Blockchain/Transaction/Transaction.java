package Blockchain.Transaction;

import Blockchain.Account;
import Blockchain.Ledger;
import Blockchain.TxId;
import ServerCommunication.Tx;

public abstract class Transaction {
    private TxId id;

    // This is ugly
    public static Transaction from(Tx tx) {
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
            case GETAMOUNT:
            case TXTYPE_NOT_SET:
                throw new RuntimeException();
        }

        return null;
    }

    public void setId(TxId id) {
        this.id = id;
    }

    public Result process(Ledger ledger) {
        return doYourThing(ledger);
    }

    abstract Result doYourThing(Ledger ledger);

    public final Tx toTxMsg() {
        var builder = Tx.newBuilder();
        builder.getIdBuilder().setSerialNumber(id.getTxId()).setServerId(id.getServerId());
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

