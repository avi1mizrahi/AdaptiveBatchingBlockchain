package Blockchain;

import ServerCommunication.CreateAccountReq;
import ServerCommunication.Tx;

abstract class Transaction {
    private TxId id;

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

    void setId(TxId id) {
        this.id = id;
    }

    public Result process(Ledger ledger) {
        return doYourThing(ledger);
    }

    abstract Result doYourThing(Ledger ledger);

    final Tx toTxMsg() {
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

    static class Result {
        boolean isCommitted() {
            return true;
        }
    }
}

class NewAccountTx extends Transaction {
    @Override
    Transaction.Result doYourThing(Ledger ledger) {
        var account = ledger.newAccount();
        System.out.println("SERVER: Created " + account);

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

    static class Result extends Transaction.Result {
        private Account newAccount;

        private Result(Account newAccount) {
            this.newAccount = newAccount;
        }

        public Account getNewAccount() {
            return newAccount;
        }
    }
}

class DeleteAccountTx extends Transaction {
    private final Account account;

    DeleteAccountTx(Account account) {
        this.account = account;
    }

    @Override
    Transaction.Result doYourThing(Ledger ledger) {
        ledger.deleteAccount(account);

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

class DepositTx extends Transaction {
    private final Account account;
    private final int     amount;

    DepositTx(Account account, int amount) {
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
    Transaction.Result doYourThing(Ledger ledger) {
        boolean success = transfer(ledger);

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

    private boolean transfer(Ledger ledger) {
        if (ledger.subtract(from, amount)) {
            if (ledger.add(to, amount)) {
                return true;
            }
            ledger.add(from, amount);//return the stolen money
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
