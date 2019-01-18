package App;

import Blockchain.*;
import Blockchain.Batch.TimedAdaptiveBatching;
import Blockchain.Transaction.NewAccountTx;
import Blockchain.Transaction.Transaction;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;

@RestController
public class ClientController {
    private final  Server server;

    ClientController(int id, String host) throws IOException {
        System.out.println("Client controller is starting:" + id + " host: " + host);
        server = new ServerBuilder().setId(id)
                                    .setServerAddress(SocketAddressFactory.from(host, 40000 + id))
                                    .setBatchingStrategy(new TimedAdaptiveBatching(Duration.ofMillis(500), 20))
                                    .createServer()
                                    .start();
    }

    @PostMapping("/accounts")
    TxId newAccount() {
        return server.createAccount();
    }

    @DeleteMapping("/accounts/{id}")
    TxId deleteAccount(@PathVariable int id) {
        return server.deleteAccount(Account.from(id));
    }

    @GetMapping("/accounts/{id}/amount")
    Amount getAmount(@PathVariable int id) {
        return new Amount(server.getAmount(Account.from(id)));
    }

    @PostMapping("/accounts/{id}/addAmount")
    TxId addAmount(@RequestBody Amount amount, @PathVariable int id) {
        return server.addAmount(Account.from(id), amount.getAmount());
    }

    @PostMapping("/transfers")
    TxId transfer(@RequestBody Transfer transfer) {
        return server.transfer(transfer.getFrom(), transfer.getTo(), transfer.getAmount());
    }

    @GetMapping("/newAccounts/{txId}")
    Account getAccountStatus(@PathVariable String txId) {
        Transaction.Result status = server.getTxStatus(TxId.from(txId));
        if (status == null) return null;
        return ((NewAccountTx.Result)status).getNewAccount();
    }

    @DeleteMapping("/newAccounts/{txId}")
    void deleteAccountStatus(@PathVariable String txId) {
        server.deleteTxStatus(TxId.from(txId));
    }

    @GetMapping("/txs/{txId}")
    TxStatus getTxStatus(@PathVariable String txId) {
        Transaction.Result status = server.getTxStatus(TxId.from(txId));
        if (status == null) return null;
        return new TxStatus(status.isCommitted());
    }
}