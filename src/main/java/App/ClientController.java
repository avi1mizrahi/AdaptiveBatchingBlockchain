package App;

import Blockchain.Account;
import Blockchain.Amount;
import Blockchain.Transaction.NewAccountTx;
import Blockchain.Transaction.Transaction;
import Blockchain.Transfer;
import Blockchain.TxId;
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

    @PutMapping("/accounts/{id}/addAmount")
    TxId addAmount(@RequestBody Amount amount, @PathVariable int id) {
        return Application.server.addAmount(id, amount.getAmount());
    }

    @PostMapping("/transfers")
    TxId transfer(@RequestBody Transfer transfer) {
        return Application.server.transfer(transfer.getFrom(), transfer.getTo(), transfer.getAmount());
    }

    @GetMapping("/newAccounts/{serverId}/{txId}")
    Account getAccountStatus(@PathVariable int serverId, @PathVariable int txId) {
        Transaction.Result status = Application.server.getTxStatus(new TxId(serverId, txId));
        return ((NewAccountTx.Result)status).getNewAccount();
    }

    @GetMapping("/txs/{serverId}/{txId}")
    Boolean getTxStatus(@PathVariable int serverId, @PathVariable int txId) {
        return Application.server.getTxStatus(new TxId(serverId, txId)).isCommitted();
    }
}