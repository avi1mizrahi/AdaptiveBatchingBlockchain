import ClientServerCommunication.*;
import io.grpc.ManagedChannelBuilder;

import java.util.Optional;
import java.util.OptionalInt;


public class Client {

    private static ClientGrpc.ClientBlockingStub stub;

    Client(String name, int port) {
        stub = ClientGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(name, port)
                                                               .usePlaintext()
                                                               .build());
    }

    public static void main(String[] args) {

        // TODO: create a test class for client-server communication
        // TODO: migrate this test to there and remove this main
        // TODO: create a mock-server (we can use "mockito" for mocking automation)
        // TODO: add tests for basic scenarios
        // TODO: optional (I'm not sure if it's cost-effective):
        //       - write here a main-loop that will receive commands interactively
        // TODO: somehow make the test run once with a mock-server (aka unittest)
        //        and once with the real server which we haven't yet (aka integration test)

        var client    = new Client("localhost", 55555);
        var account1 = client.createAccount().get();
        var account2 = client.createAccount().get();

        client.addAmount(account1, 50);
        client.transfer(account1, account2, 50);
        assert client.getAmount(account1).getAsInt() == 0;
        assert client.getAmount(account2).getAsInt() == 50;

        client.deleteAccount(account1);
        client.deleteAccount(account2);
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

        public int getId() {return id;}
    }
}
