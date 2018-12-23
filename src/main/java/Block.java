import ClientServerCommunication.AddAmountRsp;
import ClientServerCommunication.CreateAccountRsp;
import ClientServerCommunication.DeleteAccountRsp;
import ClientServerCommunication.TransferRsp;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


abstract class Transaction {
    public Transaction setResponse(StreamObserver response) {
        this.response = response;
        return this;
    }

    StreamObserver response = null;

    abstract public void doYourThing(Ledger ledger);

    @Override
    public String toString() {
        return "[TX]:";
    }
}

class NewAccountTx extends Transaction {
    private final int id;

    NewAccountTx(int id) {
        this.id = id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doYourThing(Ledger ledger) {
        boolean success = ledger.newAccount(id);
        if (response != null) {
            var rspBuilder = CreateAccountRsp.newBuilder();
            if (success) {
                rspBuilder.setId(id).setSuccess(true);
            }
            response.onNext(rspBuilder.build());
            response.onCompleted();
        }
        System.out.println("SERVER: Created ID:" + id + "=" + success);
    }

    @Override
    public String toString() {
        return super.toString() + "New[" + id + "]";
    }
}

class DeleteAccountTx extends Transaction {
    private final int id;

    DeleteAccountTx(int id) {
        this.id = id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doYourThing(Ledger ledger) {
        ledger.deleteAccount(id);
        if (response != null) {
            response.onNext(DeleteAccountRsp.getDefaultInstance());
            response.onCompleted();
        }
    }

    @Override
    public String toString() {
        return super.toString() + "Delete[" + id + "]";
    }
}

class DepositTx extends Transaction {
    private final int account;
    private final int amount;

    DepositTx(int account, int amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doYourThing(Ledger ledger) {
        boolean success = ledger.add(account, amount);
        if (response != null) {
            response.onNext(AddAmountRsp.newBuilder().setSuccess(success).build());
            response.onCompleted();

        }
    }

    @Override
    public String toString() {
        return super.toString() + "Deposit[" + account + "]+" + amount;
    }
}

class TransferTx extends Transaction {
    private final int from;
    private final int to;
    private final int amount;

    TransferTx(int from, int to, int amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doYourThing(Ledger ledger) {
        boolean success = transfer(ledger);
        if (response != null) {
            response.onNext(TransferRsp.newBuilder().setSuccess(success).build());
            response.onCompleted();
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


class OpenedBlock {
    private final List<Transaction> requests = new ArrayList<>();
    private       boolean           opened   = true;

    boolean isEmpty() {
        return requests.isEmpty();
    }

    void append(Transaction tx) {
        assert opened;
        requests.add(tx);
    }

    void apply(Ledger ledger) {
        requests.forEach(transaction -> transaction.doYourThing(ledger));
        opened = false;
    }

    @Override
    public String toString() {
        return requests.stream()
                       .map(Transaction::toString)
                       .collect(Collectors.joining("\n", "+++BLOCK+++\n", "\n---BLOCK---"));
    }
}
