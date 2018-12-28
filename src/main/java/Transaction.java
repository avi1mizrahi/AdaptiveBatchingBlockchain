import ClientServerCommunication.AddAmountRsp;
import ClientServerCommunication.CreateAccountRsp;
import ClientServerCommunication.DeleteAccountRsp;
import ClientServerCommunication.TransferRsp;
import io.grpc.stub.StreamObserver;

abstract class Transaction {
    Transaction setResponse(StreamObserver response) {
        this.response = response;
        return this;
    }

    StreamObserver response = null;

    public void process(Ledger ledger) {
        doYourThing(ledger);
        response.onCompleted();
        response = null;
    }

    abstract void doYourThing(Ledger ledger);

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
        if (response != null) {
            var rspBuilder = CreateAccountRsp.newBuilder();
            rspBuilder.setId(account.getId()).setSuccess(true);
            response.onNext(rspBuilder.build());
        }
        System.out.println("SERVER: Created " + account);
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
        if (response != null) {
            response.onNext(DeleteAccountRsp.getDefaultInstance());
        }
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
        if (response != null) {
            response.onNext(AddAmountRsp.newBuilder().setSuccess(success).build());
        }
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
        if (response != null) {
            response.onNext(TransferRsp.newBuilder().setSuccess(success).build());
        }
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

    @Override
    public String toString() {
        return super.toString() + "Transfer[" + from + "->" + to + "]+" + amount;
    }
}
