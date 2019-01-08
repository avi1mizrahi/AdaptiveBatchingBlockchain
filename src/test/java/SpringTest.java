import App.Application;
import Blockchain.Account;
import Blockchain.TxId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

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
        Account account1 = this.restTemplate.getForObject("/newAccounts/" + tx1.getServerId().toString() +"/" + tx1.getTxId().toString(),Account.class);
        System.out.println(account1);
    }

}
