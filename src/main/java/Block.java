import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


abstract class Transaction {
    @Override
    public String toString() {
        return "[TX]:";
    }
}

class TransferTx extends Transaction {
    private int from;
    private int to;
    private int amount;

    TransferTx(int from, int to, int amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return super.toString() + "Transfer[" + from + "->" + to + "]+" + amount;
    }
}

class NewAccountTx extends Transaction {
    private int id;

    NewAccountTx(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return super.toString() + "New[" + id + "]";
    }
}

class DeleteAccountTx extends Transaction {
    private int id;

    DeleteAccountTx(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return super.toString() + "Delete[" + id + "]";
    }
}

class DepositTx extends Transaction {
    private int account;
    private int amount;

    DepositTx(int account, int amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return super.toString() + "Deposit[" + account + "]+" + amount;
    }
}

class OpenedBlock {
    private List<Request> requests = new ArrayList<>();

    void append(Transaction tx, StreamObserver response) {
        requests.add(new Request(tx, response));
    }

    @Override
    public String toString() {
        return requests.stream()
                       .map(Request::toString)
                       .collect(Collectors.joining("\n", "+++BLOCK+++\n", "\n---BLOCK---"));
    }

    class Request {
        Transaction    tx;
        StreamObserver response;
        public Request(Transaction tx, StreamObserver response) {
            this.tx = tx;
            this.response = response;
        }

        @Override
        public String toString() {
            return tx.toString();
        }
    }
}
