import App.Application;
import App.ZooKeeperServer;
import Blockchain.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,classes={Application.class})
public class SpringTest {
    public static final int POLLING_DELAY = 100;
    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    public void basicTest() throws IOException, URISyntaxException, InterruptedException {
        ZooKeeperServer zkServer = new ZooKeeperServer();
        zkServer.start();

        TxId tx1 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx2 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx3 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx4 = restTemplate.postForObject("/accounts", "",TxId.class);

        Account account1 = pollGetAccount(tx1);
        Account account2 = pollGetAccount(tx2);
        Account account3 = pollGetAccount(tx3);
        Account account4 = pollGetAccount(tx4);

        restTemplate.put(String.format("/accounts/%d/addAmount", account1.getId()), new Amount(100));
        restTemplate.put(String.format("/accounts/%d/addAmount", account3.getId()), new Amount(100));

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

        zkServer.stop();
        zkServer.removeDataDir();
    }

    @NotNull
    private Account pollGetAccount(TxId tx1) {
        Account account = null;
        while (account == null) {
            account = restTemplate.getForObject(String.format("/newAccounts/%s", tx1.toString()),
                                                Account.class);
            try {
                Thread.sleep(POLLING_DELAY);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        restTemplate.delete(String.format("/newAccounts/%s", tx1.toString()), tx1);
        return account;
    }

    @NotNull
    private TxStatus pollGetStatus(TxId tx1) {
        TxStatus status = null;
        while (status == null) {
            status = restTemplate.getForObject(String.format("/txs/%s", tx1.toString()),
                    TxStatus.class);
            try {
                Thread.sleep(POLLING_DELAY);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        restTemplate.delete(String.format("/txs/%s", tx1.toString()), tx1);
        return status;
    }
}
