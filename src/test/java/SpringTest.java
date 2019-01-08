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
        TxId tx1 = this.restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx2 = this.restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx3 = this.restTemplate.postForObject("/accounts", "",TxId.class);
        TxId tx4 = this.restTemplate.postForObject("/accounts", "",TxId.class);
        Account account1 = this.restTemplate.getForObject(String.format("/newAccounts/%d/%d", tx1.getServerId(), tx1.getTxId()), Account.class);
        Account account2 = this.restTemplate.getForObject(String.format("/newAccounts/%d/%d", tx2.getServerId(), tx2.getTxId()), Account.class);
        Account account3 = this.restTemplate.getForObject(String.format("/newAccounts/%d/%d", tx3.getServerId(), tx3.getTxId()), Account.class);
        Account account4 = this.restTemplate.getForObject(String.format("/newAccounts/%d/%d", tx4.getServerId(), tx4.getTxId()), Account.class);

        this.restTemplate.put(String.format("/accounts/%d/addAmount", account1.getId()), new Amount(100));
        this.restTemplate.put(String.format("/accounts/%d/addAmount", account3.getId()), new Amount(100));


        TxId transfer1 = this.restTemplate.postForObject("/transfers", new Transfer(account1.getId(), account2.getId(), 50), TxId.class);
        TxId transfer2 = this.restTemplate.postForObject("/transfers", new Transfer(account1.getId(), account3.getId(), 50), TxId.class);

        Amount amount1 = this.restTemplate.getForObject(String.format("/accounts/%d/amount", account1.getId()), Amount.class);
        Amount amount2 = this.restTemplate.getForObject(String.format("/accounts/%d/amount", account2.getId()), Amount.class);
        Amount amount3 = this.restTemplate.getForObject(String.format("/accounts/%d/amount", account3.getId()), Amount.class);
        Amount amount4 = this.restTemplate.getForObject(String.format("/accounts/%d/amount", account4.getId()), Amount.class);

        assertEquals(amount1.getAmount(), 0);
        assertEquals(amount2.getAmount(), 50);
        assertEquals(amount3.getAmount(), 150);
        assertEquals(amount4.getAmount(), 0);

        TxId transfer3 = this.restTemplate.postForObject("/transfers", new Transfer(account3.getId(), account1.getId(), 150), TxId.class);

        amount1 = this.restTemplate.getForObject(String.format("/accounts/%d/amount", account1.getId()), Amount.class);
        amount3 = this.restTemplate.getForObject(String.format("/accounts/%d/amount", account3.getId()), Amount.class);

        assertEquals(amount1.getAmount(), 150);
        assertEquals(amount3.getAmount(), 0);

        this.restTemplate.delete(String.format("/accounts/%d", account1.getId()));
        this.restTemplate.delete(String.format("/accounts/%d", account2.getId()));
        this.restTemplate.delete(String.format("/accounts/%d", account3.getId()));
        this.restTemplate.delete(String.format("/accounts/%d", account4.getId()));
    }

}
