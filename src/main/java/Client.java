import ClientServerCommunication.*;
import io.grpc.ManagedChannelBuilder;

import java.util.Optional;
import java.util.OptionalInt;


public class Client {
// TODO: optional (I'm not sure if it's cost-effective):
//       - write here a main-loop that will receive commands interactively

    private static ClientGrpc.ClientBlockingStub stub;

    Client(String name, int port) {
        stub = ClientGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(name, port)
                                                               .usePlaintext()
                                                               .build());
    }

    Optional<Account> createAccount() {
        var response = stub.createAccount(CreateAccountReq.getDefaultInstance());

        if (!response.getSuccess()) {
            return Optional.empty();
        }
        return Optional.of(new Account(response.getId()));
    }

    void deleteAccount(Account account) {
        stub.deleteAccount(DeleteAccountReq.newBuilder().setAccountId(account.getId()).build());
    }

    boolean addAmount(Account account, int amount) {
        var response = stub.addAmount(AddAmountReq.newBuilder()
                                                  .setAccountId(account.getId())
                                                  .setAmount(amount)
                                                  .build());
        return response.getSuccess();
    }

    OptionalInt getAmount(Account account) {
        var response = stub.getAmount(GetAmountReq.newBuilder()
                                                  .setAccountId(account.getId())
                                                  .build());
        if (!response.getSuccess()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(response.getAmount());
    }

    boolean transfer(Account from, Account to, int amount) {
        var response = stub.transfer(TransactionReq.newBuilder()
                                                   .setFromId(from.getId())
                                                   .setToId(to.getId())
                                                   .setAmount(amount)
                                                   .build());
        return response.getSuccess();
    }

    static class Account {
        private final int id;

        Account(int id) {this.id = id;}

        int getId() {return id;}
    }
}
