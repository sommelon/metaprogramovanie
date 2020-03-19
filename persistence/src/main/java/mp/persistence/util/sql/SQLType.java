package mp.persistence.util.sql;

public enum SQLType {
    INTEGER("INTEGER"),
    REAL("REAL"),
    TEXT("TEXT")
    ;

    private final String label;

    SQLType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }

    public static SQLType getSQLTypeFromClass(Class type) {
        SQLType sqlType;
        switch (type.getSimpleName().toLowerCase()) {
            case "double":
            case "float":
                sqlType = SQLType.REAL;
                break;
            case "string":
            case "char":
            case "character":
            case "boolean":
                sqlType = SQLType.TEXT;
                break;
            default: sqlType = SQLType.INTEGER;
        }

        return sqlType;
    }
}
