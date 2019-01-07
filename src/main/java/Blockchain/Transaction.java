package Blockchain;

import ClientServerCommunication.CreateAccountReq;
import ServerCommunication.Tx;

abstract class Transaction {

    // This is ugly
    static Transaction from(Tx tx) {
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

    public void process(Ledger ledger) {
        doYourThing(ledger);
    }

    abstract void doYourThing(Ledger ledger);

    final Tx toTxMsg() {
        var builder = Tx.newBuilder();
        addToMsg(builder);
        return builder.build();
    }

    abstract void addToMsg(Tx.Builder txBuilder);

    @Override
    public String toString() {
        return "[TX]:";
    }
}

class NewAccountTx extends Transaction {
    NewAccountTx() {
    }

    @Override
    @SuppressWarnings("unchecked")
    void doYourThing(Ledger ledger) {
        var account = ledger.newAccount();
        System.out.println("SERVER: Created " + account);
    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.setCreate(CreateAccountReq.getDefaultInstance());
    }

    @Override
    public String toString() {
        return super.toString() + "New";
    }
}

class DeleteAccountTx extends Transaction {
    private final Account account;

    DeleteAccountTx(Account account) {
        this.account = account;
    }

    @Override
    @SuppressWarnings("unchecked")
    void doYourThing(Ledger ledger) {
        ledger.deleteAccount(account);

    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.getDeleteBuilder().setAccountId(account.getId());
    }

    @Override
    public String toString() {
        return super.toString() + "Delete[" + account + "]";
    }
}

class DepositTx extends Transaction {
    private final Account account;
    private final int     amount;

    DepositTx(Account account, int amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    @SuppressWarnings("unchecked")
    void doYourThing(Ledger ledger) {
        boolean success = ledger.add(account, amount);

    }

    @Override
    void addToMsg(Tx.Builder txBuilder) {
        txBuilder.getAddAmountBuilder().setAccountId(account.getId()).setAmount(amount);
    }

    @Override
    public String toString() {
        return super.toString() + "Deposit[" + account + "]+" + amount;
    }
}

class TransferTx extends Transaction {
    private final Account from;
    private final Account to;
    private final int     amount;

    TransferTx(Account from, Account to, int amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    @Override
    @SuppressWarnings("unchecked")
    void doYourThing(Ledger ledger) {
        boolean success = transfer(ledger);

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

    private boolean transfer(Ledger ledger) {
        if (ledger.subtract(from, amount)) {
            if (ledger.add(to, amount)) {
                return true;
            }
            ledger.add(from, amount);//return the stolen money
        }
        return false;
    }
}
