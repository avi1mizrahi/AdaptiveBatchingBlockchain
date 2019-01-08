package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Objects;

@Data
public class TxId {
    public static final String SEPARATOR = "-";

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
        String[] split = stringId.split(SEPARATOR);
        if (split.length != 3) throw new RuntimeException(stringId + " is not a valid TxId");
        return new TxId(Integer.valueOf(split[0]),
                        Integer.valueOf(split[1]),
                        Integer.valueOf(split[2]));
    }

    public boolean equals(TxId other) {
        return this.serverId == other.serverId &&
                this.blockIdx == other.blockIdx &&
                this.txIdx == other.txIdx;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TxId && equals((TxId) obj);
    }

    @Override
    public String toString() {
        return String.join(SEPARATOR,
                           String.valueOf(serverId),
                           String.valueOf(blockIdx),
                           String.valueOf(txIdx));
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, blockIdx, txIdx);
    }
}