package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Transfer {
    private final int from;
    private final int to;
    private final int amount;
    public Transfer(@JsonProperty(value = "from", required = true) int from,
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