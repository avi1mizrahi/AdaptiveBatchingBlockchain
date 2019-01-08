package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TxId {
    private final int serverId;
    private final int blockIdx;
    private final int txIdx;

    public TxId(@JsonProperty(value = "serverId", required = true) int serverId,
                @JsonProperty(value = "blockIdx", required = true) int blockIdx,
                @JsonProperty(value = "txIdx", required = true) int txIdx) {
        this.serverId = serverId;
        this.blockIdx = blockIdx;
        this.txIdx = txIdx;
    }

    public static TxId from(String stringId) {
        String[] split = stringId.split(":");
        if (split.length != 3) throw new RuntimeException(stringId + " is not a valid TxId");
        return new TxId(Integer.valueOf(split[0]),
                        Integer.valueOf(split[1]),
                        Integer.valueOf(split[2]));
    }

    @Override
    public String toString() {
        return String.join(":",
                           String.valueOf(serverId),
                           String.valueOf(blockIdx),
                           String.valueOf(txIdx));
    }

    public int getServerId() {
        return serverId;
    }

    public int getBlockIdx() {
        return blockIdx;
    }

    public int getTxIdx() {
        return txIdx;
    }
}