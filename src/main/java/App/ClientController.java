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
        return Application.server.deleteAccount(Account.from(id));
    }

    @GetMapping("/accounts/{id}/amount")
    Amount getAmount(@PathVariable int id) {
        return new Amount(Application.server.getAmount(Account.from(id)));
    }

    @PutMapping("/accounts/{id}/addAmount")
    TxId addAmount(@RequestBody Amount amount, @PathVariable int id) {
        return Application.server.addAmount(Account.from(id), amount.getAmount());
    }

    @PostMapping("/transfers")
    TxId transfer(@RequestBody Transfer transfer) {
        return Application.server.transfer(transfer.getFrom(), transfer.getTo(), transfer.getAmount());
    }

    @GetMapping("/newAccounts/{txId}")
    Account getAccountStatus(@PathVariable String txId) {
        Transaction.Result status = Application.server.getTxStatus(TxId.from(txId));
        if (status == null) return null;
        return ((NewAccountTx.Result)status).getNewAccount();
    }

    @DeleteMapping("/newAccounts/{txId}")
    void deleteAccountStatus(@PathVariable String txId) {
        Application.server.deleteTxStatus(TxId.from(txId));
    }

    @GetMapping("/txs/{txId}")
    Boolean getTxStatus(@PathVariable int serverId, @PathVariable String txId) {
        return Application.server.getTxStatus(TxId.from(txId)).isCommitted();
    }
}