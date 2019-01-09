package Blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Data
public class Account {
    private final int id;

    Account(@JsonProperty(value = "id", required = true) int id) {
        this.id = id;
    }

    @NotNull
    @Contract("_ -> new")
    public static Account from(int id) {
        return new Account(id);
    }

    public int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Contract(value = "null -> false", pure = true)
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Account && ((Account) obj).id == id;
    }

    @Override
    public String toString() {
        return "Blockchain.Account" + id;
    }
}
