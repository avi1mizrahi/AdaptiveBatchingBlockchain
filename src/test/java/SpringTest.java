import App.Application;
import Blockchain.Account;
import Blockchain.Amount;
import Blockchain.Transfer;
import Blockchain.TxId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,classes={Application.class})
public class SpringTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    public void basicTest() {
        TxId tx1 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx2 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx3 = restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx4 = restTemplate.postForObject("/accounts", "",TxId.class);

        Account account1;
        Account account2;
        Account account3;
        Account account4;
        do {
            account1 = restTemplate.getForObject(String.format("/newAccounts/%s", tx1.toString()), Account.class);
            account2 = restTemplate.getForObject(String.format("/newAccounts/%s", tx2.toString()), Account.class);
            account3 = restTemplate.getForObject(String.format("/newAccounts/%s", tx3.toString()), Account.class);
            account4 = restTemplate.getForObject(String.format("/newAccounts/%s", tx4.toString()), Account.class);
        } while (account1 == null || account2 == null || account3 == null || account4 == null);

        restTemplate.put(String.format("/accounts/%d/addAmount", account1.getId()), new Amount(100));
        restTemplate.put(String.format("/accounts/%d/addAmount", account3.getId()), new Amount(100));


        TxId transfer1 = restTemplate.postForObject("/transfers", new Transfer(account1.getId(), account2.getId(), 50), TxId.class);
        TxId transfer2 = restTemplate.postForObject("/transfers", new Transfer(account1.getId(), account3.getId(), 50), TxId.class);

        Amount amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", account1.getId()), Amount.class);
        Amount amount2 = restTemplate.getForObject(String.format("/accounts/%d/amount", account2.getId()), Amount.class);
        Amount amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", account3.getId()), Amount.class);
        Amount amount4 = restTemplate.getForObject(String.format("/accounts/%d/amount", account4.getId()), Amount.class);

        assertEquals(amount1.getAmount(), 0);
        assertEquals(amount2.getAmount(), 50);
        assertEquals(amount3.getAmount(), 150);
        assertEquals(amount4.getAmount(), 0);

        TxId transfer3 = restTemplate.postForObject("/transfers", new Transfer(account3.getId(), account1.getId(), 150), TxId.class);

        amount1 = restTemplate.getForObject(String.format("/accounts/%d/amount", account1.getId()), Amount.class);
        amount3 = restTemplate.getForObject(String.format("/accounts/%d/amount", account3.getId()), Amount.class);

        assertEquals(amount1.getAmount(), 150);
        assertEquals(amount3.getAmount(), 0);

        restTemplate.delete(String.format("/accounts/%d", account1.getId()));
        restTemplate.delete(String.format("/accounts/%d", account2.getId()));
        restTemplate.delete(String.format("/accounts/%d", account3.getId()));
        restTemplate.delete(String.format("/accounts/%d", account4.getId()));
    }

}
