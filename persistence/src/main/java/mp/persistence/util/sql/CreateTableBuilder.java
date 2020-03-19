package mp.persistence.util.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateTableBuilder {
    private String tableName;
    private List<Column> columns = new ArrayList<>();
    private List<ForeignKey> foreignKeys = new ArrayList<>();
    private PrimaryKey primaryKey;

    public CreateTableBuilder(String tableName) {
        this.tableName = tableName;
    }

    public CreateTableBuilder(String tableName, List<Column> columns) {
        this.tableName = tableName;
        this.columns = columns;
    }

    public CreateTableBuilder(String tableName, List<Column> columns, List<ForeignKey> foreignKeys) {
        this.tableName = tableName;
        this.columns = columns;
        this.foreignKeys = foreignKeys;
    }

    public CreateTableBuilder(String tableName, List<Column> columns, List<ForeignKey> foreignKeys, PrimaryKey primaryKey) {
        this.tableName = tableName;
        this.columns = columns;
        this.foreignKeys = foreignKeys;
        this.primaryKey = primaryKey;
    }

    public CreateTableBuilder addColumn(Column column) {
        columns.add(column);
        return this;
    }

    public CreateTableBuilder addForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null && foreignKey.isValid()) {
            foreignKeys.add(foreignKey);
        }
        return this;
    }

    public void setPrimaryKey(PrimaryKey primaryKey) {
        this.primaryKey = primaryKey;
    }

    @Override
    public String toString() {
        if (tableName == null || tableName.equals("") || columns.size() == 0){
            return null; //TODO exception namiesto null?
        }

        StringBuilder queryStringBuilder = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        for (Column column : columns) {
            queryStringBuilder.append(column.toString()).append(",");
        }
        queryStringBuilder.setLength(queryStringBuilder.length() - 1);

        for (ForeignKey foreignKey : foreignKeys) {
            queryStringBuilder.append(",").append(foreignKey.toString());
        }

        if (primaryKey != null) {
            queryStringBuilder.append(",").append(primaryKey.toString());
        }
        queryStringBuilder.append(")");

        return queryStringBuilder.toString();
    }
}
