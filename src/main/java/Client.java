import ClientServerCommunication.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.Scanner;


public class Client {

    private final ClientGrpc.ClientBlockingStub stub;
    private final ManagedChannel channel;

    Client(String name, int port) {
        channel = ManagedChannelBuilder.forAddress(name, port)
                                       .usePlaintext()
                                       .build();
        stub = ClientGrpc.newBlockingStub(channel);
    }

    void shutdown() {
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    Optional<Account> createAccount() {
        var response = stub.createAccount(CreateAccountReq.getDefaultInstance());

        if (!response.getSuccess()) {
            return Optional.empty();
        }
        return Optional.of(Account.from(response.getId()));
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
        var response = stub.transfer(TransferReq.newBuilder()
                                                .setFromId(from.getId())
                                                .setToId(to.getId())
                                                .setAmount(amount)
                                                .build());
        return response.getSuccess();
    }

    public static void main(String[] args) {
        String LOCALHOST = "localhost";
        int    PORT      = 55555;

        if (args.length == 2) {
            LOCALHOST = args[0];
            PORT      = Integer.parseInt(args[1]);
        }

        var client = new Client(LOCALHOST, PORT);

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String[] operation = scanner.nextLine().split(" ");
            if (operation.length == 1 && operation[0].equals("shutdown")) {
                client.shutdown();
                break;
            }
            if (operation.length == 1 && operation[0].equals("createAccount")) {
                var account = client.createAccount().get();
                System.out.println(">>account id: " + account.getId());
            }
            if (operation.length == 2 && operation[0].equals("deleteAccount")) {
                client.deleteAccount(Account.from(Integer.parseInt(operation[1])));
            }
            if (operation.length == 3 && operation[0].equals("addAmount")) {
                var success = client.addAmount(Account.from(Integer.parseInt(operation[1])),
                                               Integer.parseInt(operation[2]));
                System.out.println(">>success=" + success);
            }
            if (operation.length == 2 && operation[0].equals("getAmount")) {
                int amount = client.getAmount(Account.from(Integer.parseInt(operation[1])))
                                   .getAsInt();
                System.out.println(">>amount: " + amount);
            }
            if (operation.length == 4 && operation[0].equals("transfer")) {
                var success = client.transfer(Account.from(Integer.parseInt(operation[1])),
                                              Account.from(Integer.parseInt(operation[2])),
                                              Integer.parseInt(operation[3]));
                System.out.println(">>success=" + success);
            }
        }
    }

}
