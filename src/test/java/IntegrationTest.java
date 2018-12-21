import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// TODO: add tests for basic scenarios
//
// optional:
// TODO: create a mock-server for client unittest (we can use "mockito" for mocking automation)
// TODO: somehow make the test run once with a mock-server (aka unittest)
//        and once with the real server which we haven't yet (aka integration test)


class IntegrationTest {
    public static final int PORT = 55555;
    public static final String LOCALHOST = "localhost";

    @Test
    void basic() {
        final Server[] server = new Server[1];
        assertDoesNotThrow(() -> {
            server[0] = new Server(PORT);
        });

        var client1  = new Client(LOCALHOST, PORT);
        var client2  = new Client(LOCALHOST, PORT);
        var account1 = client1.createAccount().get();
        var account2 = client1.createAccount().get();
        var account3 = client2.createAccount().get();
        var account4 = client2.createAccount().get();

        client1.addAmount(account1, 100);
        client1.addAmount(account3, 100);

        client2.transfer(account1, account2, 50);
        client2.transfer(account1, account3, 50);

        assertEquals(client1.getAmount(account1).getAsInt(), 0);
        assertEquals(client2.getAmount(account2).getAsInt(), 50);
        assertEquals(client1.getAmount(account3).getAsInt(), 150);
        assertEquals(client2.getAmount(account4).getAsInt(), 0);

        client1.transfer(account3, account1, 150);

        assertEquals(client2.getAmount(account1).getAsInt(), 150);
        assertEquals(client1.getAmount(account3).getAsInt(), 0);

        client1.deleteAccount(account1);
        client2.deleteAccount(account2);
        client1.deleteAccount(account3);
        client2.deleteAccount(account4);

        server[0].shutdown();
    }
}