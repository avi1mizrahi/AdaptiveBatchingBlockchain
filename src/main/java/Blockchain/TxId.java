package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TxId {
    private final int serverId;
    private final int txId;

    public TxId(@JsonProperty(value = "serverId", required = true) int serverId,
         @JsonProperty(value = "txId", required = true) int txId) {
        this.serverId = serverId;
        this.txId = txId;
    }

    int getServerId() {
        return serverId;
    }

    int getTxId() {
        return txId;
    }
}