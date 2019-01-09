package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Transfer {
    private final Account from;
    private final Account to;
    private final int amount;

    public Transfer(@JsonProperty(value = "from", required = true) Account from,
             @JsonProperty(value = "to", required = true) Account to,
             @JsonProperty(value = "amount", required = true) int amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public Account getFrom() {
        return from;
    }

    public Account getTo() {
        return to;
    }

    public int getAmount() {
        return amount;
    }
}