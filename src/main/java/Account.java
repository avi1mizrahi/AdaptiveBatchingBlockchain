class Account {
    private final int id;

    Account(int id) {
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
        return obj != null
                && Account.class.isAssignableFrom(obj.getClass())
                && ((Account) obj).id == id;
    }

    @Override
    public String toString() {
        return "Account" + id;
    }
}
