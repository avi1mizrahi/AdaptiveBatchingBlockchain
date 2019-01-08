package App;

import Blockchain.Account;
import Blockchain.TxId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClientController {



    private static Logger log = Logger.getLogger(ClientController.class.getName());

    @PostMapping("/accounts")
    TxId newAccount() {
        return Application.server.createAccount();

    }

    @DeleteMapping("/accounts/{id}")
    TxId deleteAccount(@PathVariable int id) {
        return Application.server.deleteAccount(id);
    }

    @GetMapping("/accounts/{id}/amount")
    Amount getAmount(@PathVariable int id) {
        return new Amount(Application.server.getAmount(id));
    }

    @Data
    public class Amount {
        private final int amount;
        Amount(@JsonProperty(value = "amount", required = true) int amount) {
            this.amount = amount;
        }

        public int getAmount() {
            return amount;
        }
    }

    @PutMapping("/accounts/{id}/addAmount")
    TxId addAmount(@RequestBody Amount amount, @PathVariable int id) {
        return Application.server.addAmount(id, amount.getAmount());
    }

    @Data
    public class Transfer {
        private final int from;
        private final int to;
        private final int amount;
        Transfer(@JsonProperty(value = "from", required = true) int from,
                 @JsonProperty(value = "to", required = true) int to,
                 @JsonProperty(value = "amount", required = true) int amount) {
            this.from = from;
            this.to = to;
            this.amount = amount;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        public int getAmount() {
            return amount;
        }
    }

    @PostMapping("/transfers")
    TxId transfer(@RequestBody Transfer transfer) {
        return Application.server.transfer(transfer.getFrom(), transfer.getTo(), transfer.getAmount());
    }

    @GetMapping("/newAccounts/{serverId}/{txId}")
    Account getAccountStatus(@PathVariable int serverId, @PathVariable int txId) {
        return Application.server.getTxStatus(new TxId(serverId, txId)).getAccount();
    }

    @GetMapping("/txs/{serverId}/{txId}")
    Boolean getTxStatus(@PathVariable int serverId, @PathVariable int txId) {
        return Application.server.getTxStatus(new TxId(serverId, txId)).getIsCommitted();
    }
}