package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
@Data
public class Account {
    private final int id;

    Account(@JsonProperty(value = "id", required = true) int id) {
        this.id = id;
    }

    static Account from(int id) {
        return new Account(id);
    }

    int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Account && ((Account) obj).id == id;
    }

    @Override
    public String toString() {
        return "Blockchain.Account" + id;
    }
}
