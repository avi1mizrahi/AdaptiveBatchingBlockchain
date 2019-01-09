import App.Application;
import App.ZooKeeperServer;
import Blockchain.*;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,classes={Application.class})
public class SpringTest {
    private static final int POLLING_DELAY_MS   = 100;
    private static final int POLLING_ITERATIONS = 10 * 5;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    int port;
    private ZooKeeperServer zkServer;

    @Before
    public void setUp() throws Exception {
        zkServer = new ZooKeeperServer();
        zkServer.start();
    }

    @After
    public void tearDown() throws Exception {
        zkServer.stop();
        zkServer.removeDataDir();
        zkServer = null;
    }

    @Test
    public void basicTest() {
        TxId tx1 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx2 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx3 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx4 = restTemplate.postForObject("/accounts", "",TxId.class);

        Account account1 = pollGetAccount(tx1);
        Account account2 = pollGetAccount(tx2);
        Account account3 = pollGetAccount(tx3);
        Account account4 = pollGetAccount(tx4);

        restTemplate.postForObject(String.format("/accounts/%d/addAmount", account1.getId()), new Amount(100), TxId.class);
        restTemplate.postForObject(String.format("/accounts/%d/addAmount", account3.getId()), new Amount(100), TxId.class);

        TxId transfer1 = restTemplate.postForObject("/transfers", new Transfer(account1, account2, 50), TxId.class);
        TxId transfer2 = restTemplate.postForObject("/transfers", new Transfer(account1, account3, 50), TxId.class);
        TxStatus tx1status = pollGetStatus(transfer1);
        TxStatus tx2status = pollGetStatus(transfer2);

        Amount amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", account1.getId()), Amount.class);
        Amount amount2 = restTemplate.getForObject(String.format("/accounts/%d/amount", account2.getId()), Amount.class);
        Amount amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", account3.getId()), Amount.class);
        Amount amount4 = restTemplate.getForObject(String.format("/accounts/%d/amount", account4.getId()), Amount.class);

        assertEquals(amount1.getAmount(), 0);
        assertEquals(amount2.getAmount(), 50);
        assertEquals(amount3.getAmount(), 150);
        assertEquals(amount4.getAmount(), 0);

        TxId transfer3 = restTemplate.postForObject("/transfers", new Transfer(account3, account1, 150), TxId.class);
        TxStatus tx3status = pollGetStatus(transfer3);

        amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", account1.getId()), Amount.class);
        amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", account3.getId()), Amount.class);

        assertEquals(amount1.getAmount(), 150);
        assertEquals(amount3.getAmount(), 0);

        restTemplate.delete(String.format("/accounts/%d", account1.getId()));
        restTemplate.delete(String.format("/accounts/%d", account2.getId()));
        restTemplate.delete(String.format("/accounts/%d", account3.getId()));
        restTemplate.delete(String.format("/accounts/%d", account4.getId()));
    }

    @Test
    public void asyncIndependentTest() {
        final var       threads  = new ArrayList<Thread>();
        final LongAdder finished = new LongAdder();

        final int nThreads  = 150;
        final int nAccounts = 10;
        final int nRepeat   = 10;

        for (int i = 0; i < nThreads; ++i) {
            threads.add(new Thread(() -> {

                List<TxId> newAccountTxs = new ArrayList<>();

                for (int j = 0; j < nAccounts; j++) {
                    var txId = restTemplate.postForObject("/accounts", "", TxId.class);
                    newAccountTxs.add(txId);
                }

                List<Account> accounts = pollGetAccounts(newAccountTxs);

                restTemplate.postForObject(String.format("/accounts/%d/addAmount", accounts.get(1).getId()), new Amount(100), TxId.class);
                restTemplate.postForObject(String.format("/accounts/%d/addAmount", accounts.get(3).getId()), new Amount(100), TxId.class);

                TxId transfer1 = restTemplate.postForObject("/transfers", new Transfer(accounts.get(1), accounts.get(2), 50), TxId.class);
                TxId transfer2 = restTemplate.postForObject("/transfers", new Transfer(accounts.get(1), accounts.get(3), 50), TxId.class);

                pollGetStatuses(List.of(transfer1, transfer2));

                Amount amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(1).getId()), Amount.class);
                Amount amount2 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(2).getId()), Amount.class);
                Amount amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(3).getId()), Amount.class);
                Amount amount4 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(4).getId()), Amount.class);

                assertEquals(0  , amount1.getAmount());
                assertEquals(50 , amount2.getAmount());
                assertEquals(150, amount3.getAmount());
                assertEquals(0  , amount4.getAmount());

                TxId transfer3 = restTemplate.postForObject("/transfers", new Transfer(accounts.get(3), accounts.get(1), 150), TxId.class);
                TxStatus tx3status = pollGetStatus(transfer3);

                amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(1).getId()), Amount.class);
                amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(3).getId()), Amount.class);

                assertEquals(150, amount1.getAmount());
                assertEquals(0  , amount3.getAmount());

                int value1 = 150;
                int value3 = 0;

                for (int j = 0; j < nRepeat; ++j) {
                    restTemplate.postForObject(String.format("/accounts/%d/addAmount", accounts.get(1).getId()), new Amount(5), TxId.class);
                    restTemplate.postForObject(String.format("/accounts/%d/addAmount", accounts.get(3).getId()), new Amount(15), TxId.class);
                    value1 += 5;
                    value3 += 15;

                    int polled = 0;
                    do {
                        amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(1).getId()), Amount.class);
                        amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", accounts.get(3).getId()), Amount.class);

                        assertEquals(50, amount2.getAmount());
                        assertEquals(0, amount4.getAmount());

                        try {
                            assertNotEquals(POLLING_ITERATIONS, polled++);
                            Thread.sleep(POLLING_DELAY_MS);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    } while (amount1.getAmount() != value1 || amount3.getAmount() != value3);
                }

                restTemplate.delete(String.format("/accounts/%d", accounts.get(1).getId()));
                restTemplate.delete(String.format("/accounts/%d", accounts.get(2).getId()));
                restTemplate.delete(String.format("/accounts/%d", accounts.get(3).getId()));
                restTemplate.delete(String.format("/accounts/%d", accounts.get(4).getId()));

                finished.increment();
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                assert false;
            }
        });

        assertEquals(nThreads, finished.sum());
    }

    @NotNull
    private Account pollGetAccount(TxId tx1) {
        Account account = null;
        int polled = 0;
        while (account == null) {
            account = restTemplate.getForObject(String.format("/newAccounts/%s", tx1.toString()),
                                                Account.class);
            try {
                assertNotEquals(POLLING_ITERATIONS, polled++);
                Thread.sleep(POLLING_DELAY_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        restTemplate.delete(String.format("/newAccounts/%s", tx1.toString()), tx1);
        return account;
    }

    @NotNull
    private List<Account> pollGetAccounts(List<TxId> txs) {
        List<Account> accounts = new ArrayList<>();
        int polled = 0;

        while (!txs.isEmpty()) {
            ListIterator<?> iter = txs.listIterator();
            while(iter.hasNext()){
                @NotNull Account account;
                Object tx = iter.next();

                account = restTemplate.getForObject(String.format("/newAccounts/%s", tx.toString()), Account.class);

                if (account != null) {
                    accounts.add(account);
                    restTemplate.delete(String.format("/newAccounts/%s", tx.toString()), tx);
                    iter.remove();
                }
            }

            try {
                assertNotEquals(POLLING_ITERATIONS, polled++);
                Thread.sleep(POLLING_DELAY_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return accounts;
    }

    @NotNull
    private TxStatus pollGetStatus(TxId tx1) {
        TxStatus status = null;
        int polled = 0;
        while (status == null) {
            status = restTemplate.getForObject(String.format("/txs/%s", tx1.toString()),
                    TxStatus.class);
            try {
                assertNotEquals(POLLING_ITERATIONS, polled++);
                Thread.sleep(POLLING_DELAY_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        restTemplate.delete(String.format("/txs/%s", tx1.toString()), tx1);
        return status;
    }

    @NotNull
    private List<TxStatus> pollGetStatuses(List<TxId> transactions) {
        List<TxStatus> statuses = new ArrayList<>();

        List<TxId> txs = new LinkedList<>(transactions);

        int polled = 0;

        while (!txs.isEmpty()) {
            ListIterator<?> iter = txs.listIterator();
            while(iter.hasNext()) {
                @NotNull TxStatus status;
                Object tx = iter.next();

                status = restTemplate.getForObject(String.format("/txs/%s", tx.toString()),
                                                   TxStatus.class);

                if (status != null) {
                    statuses.add(status);
                    restTemplate.delete(String.format("/newAccounts/%s", tx.toString()), tx);
                    iter.remove();
                }
            }

            try {
                assertNotEquals(POLLING_ITERATIONS, polled++);
                Thread.sleep(POLLING_DELAY_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return statuses;
    }
}
