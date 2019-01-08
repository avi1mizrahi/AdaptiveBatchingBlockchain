package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Amount {
    private final int amount;
    public Amount(@JsonProperty(value = "amount", required = true) int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}