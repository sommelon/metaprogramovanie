package mp.persistence.util.sql;

public class ForeignKey {
    private String columnName;
    private String referencedTableName;
    private String referencedColumnName;

    public ForeignKey(String columnName, String referencedTableName, String referencedColumnName) {
        this.columnName = columnName;
        this.referencedTableName = referencedTableName;
        this.referencedColumnName = referencedColumnName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getReferencedTableName() {
        return referencedTableName;
    }

    public String getReferencedColumnName() {
        return referencedColumnName;
    }

    public boolean isValid() {
        return columnName != null && !columnName.equals("")
                && referencedTableName != null && !referencedTableName.equals("")
                && referencedColumnName != null && !referencedColumnName.equals("");
    }

    @Override
    public String toString() {
        return "FOREIGN KEY ("+ columnName +") REFERENCES "+ referencedTableName +"("+ referencedColumnName +")";
    }
}
