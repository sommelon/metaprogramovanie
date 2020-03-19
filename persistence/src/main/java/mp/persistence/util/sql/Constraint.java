package mp.persistence.util.sql;

public enum Constraint {
    NOT_NULL("NOT NULL"),
    UNIQUE("UNIQUE"),
    PRIMARY_KEY("PRIMARY KEY"),
    ;

    private final String label;

    Constraint(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
