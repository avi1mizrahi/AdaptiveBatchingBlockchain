package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TxStatus {
    private final boolean isCommitted;

    public TxStatus(@JsonProperty(value = "isCommitted", required = true) boolean isCommitted) {
        this.isCommitted = isCommitted;
    }

    public boolean getIsCommitted() {
            return isCommitted;
    }
}