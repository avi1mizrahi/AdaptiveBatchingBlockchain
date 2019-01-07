import Blockchain.Client;
import Blockchain.Server;
import Blockchain.ServerBuilder;
import io.grpc.netty.shaded.io.netty.util.internal.ConcurrentSet;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// TODO: add tests for basic scenarios
//
// optional:
// TODO: create a mock-server for client unittest (we can use "mockito" for mocking automation)
// TODO: somehow make the test run once with a mock-server (aka unittest)
//        and once with the real server which we haven't yet (aka integration test)


class IntegrationTest {
    public static final int    CLIENT_PORT = 55555;
    public static final int    SERVER_PORT = 44444;
    public static final String LOCALHOST   = "localhost";

    @Test
    void basic() {
        final Server[] server = new Server[1];
        assertDoesNotThrow(() -> {
            server[0] = new ServerBuilder().setId(1)
                                           .setServerPort(SERVER_PORT)
                                           .setBlockWindow(Duration.ofMillis(30)) // TODO: 30 is just to accelerate the tests, don't know what is "good value"
                                           .createServer()
                                           .start();
        });

//        var client1  = new Client(LOCALHOST, CLIENT_PORT);
//        var client2  = new Client(LOCALHOST, CLIENT_PORT);
//        var account1 = client1.createAccount().get();
//        var account2 = client1.createAccount().get();
//        var account3 = client2.createAccount().get();
//        var account4 = client2.createAccount().get();
//
//        client1.addAmount(account1, 100);
//        client1.addAmount(account3, 100);
//
//        client2.transfer(account1, account2, 50);
//        client2.transfer(account1, account3, 50);
//
//        assertEquals(client1.getAmount(account1).getAsInt(), 0);
//        assertEquals(client2.getAmount(account2).getAsInt(), 50);
//        assertEquals(client1.getAmount(account3).getAsInt(), 150);
//        assertEquals(client2.getAmount(account4).getAsInt(), 0);
//
//        client1.transfer(account3, account1, 150);
//
//        assertEquals(client2.getAmount(account1).getAsInt(), 150);
//        assertEquals(client1.getAmount(account3).getAsInt(), 0);
//
//        client1.deleteAccount(account1);
//        client2.deleteAccount(account2);
//        client1.deleteAccount(account3);
//        client2.deleteAccount(account4);
//
//        client1.shutdown();
//        client2.shutdown();
//        server[0].shutdown();
    }

    @Test
    void async_independent() {
        final Server[] server = new Server[1];
        assertDoesNotThrow(() -> {
            server[0] = new ServerBuilder().setId(1)
                                           .setServerPort(SERVER_PORT)
                                           .setBlockWindow(Duration.ofMillis(30)) // TODO: 30 is just to accelerate the tests, don't know what is "good value"
                                           .createServer()
                                           .start();
        });

        final var       threads  = new ArrayList<Thread>();
        final var       clients  = new ConcurrentSet<Client>();
        final LongAdder finished = new LongAdder();
        final int       nThreads = 200;

//        for (int i = 0; i < nThreads; ++i) {
//            threads.add(new Thread(() -> {
//                var client1 = new Client(LOCALHOST, CLIENT_PORT);
//                var client2 = new Client(LOCALHOST, CLIENT_PORT);
//
//                clients.add(client1);
//                clients.add(client2);
//
//                var account1 = client1.createAccount().get();
//                addSchedulingNoise();
//                var account2 = client1.createAccount().get();
//                var account3 = client2.createAccount().get();
//                var account4 = client2.createAccount().get();
//
//                client1.addAmount(account1, 100);
//                client1.addAmount(account3, 100);
//
//                client2.transfer(account1, account2, 50);
//                client2.transfer(account1, account3, 50);
//
//                assertEquals(client1.getAmount(account1).getAsInt(), 0);
//                assertEquals(client2.getAmount(account2).getAsInt(), 50);
//                assertEquals(client1.getAmount(account3).getAsInt(), 150);
//                assertEquals(client2.getAmount(account4).getAsInt(), 0);
//
//                client1.transfer(account3, account1, 150);
//
//                assertEquals(client2.getAmount(account1).getAsInt(), 150);
//                assertEquals(client1.getAmount(account3).getAsInt(), 0);
//
//                int value1 = 150;
//
//                for (int j = 0; j < 10; ++j) {
//                    client1.addAmount(account1, 20);
//                    client2.addAmount(account1, 20);
//                    value1 += 40;
//                    assertEquals(client1.getAmount(account1).getAsInt(), value1);
//                }
//
//                client1.deleteAccount(account1);
//                client2.deleteAccount(account2);
//                client1.deleteAccount(account3);
//                client2.deleteAccount(account4);
//
//                finished.increment();
//            }));
//        }
//
//        threads.forEach(Thread::start);
//        threads.forEach(thread -> {
//            try {
//                thread.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//                assert false;
//            }
//        });
//
//        assertEquals(nThreads, finished.sum());
//
//        clients.forEach(Client::shutdown);
//
//        server[0].shutdown();
    }

    private static void addSchedulingNoise() {
        final int delay = ThreadLocalRandom.current().nextInt(1000);
        if (delay < 200) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
        }
    }
}