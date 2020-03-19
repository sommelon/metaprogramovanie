package mp.persistence.util.sql;

import java.util.ArrayList;
import java.util.List;

public class UpsertBuilder {
    String tableName;
    List<String> columnNames = new ArrayList<>();
    List<String> onConflictColumnNames = new ArrayList<>();

    public UpsertBuilder(String tableName) {
        this.tableName = tableName;
    }

    public UpsertBuilder addColumnName(String columnName){
        if (columnName != null && !columnName.equals("")) {
            columnNames.add(columnName);
        }
        return this;
    }

    public UpsertBuilder addOnConflictColumnName(String columnName){
        if (columnName != null && !columnName.equals("")) {
            onConflictColumnNames.add(columnName);
        }
        return this;
    }

    @Override
    public String toString(){
        if (tableName == null || tableName.equals("") || columnNames.size() == 0 || onConflictColumnNames.size() == 0){
            return null;
        }

        StringBuilder queryStringBuilder = new StringBuilder("INSERT INTO ");
        queryStringBuilder.append(tableName).append(" (");

        for (String columnName : columnNames) {
            queryStringBuilder.append(columnName).append(",");
        }
        queryStringBuilder.setLength(queryStringBuilder.length() - 1);
        queryStringBuilder.append(") VALUES (");

        for (int i = 0; i < columnNames.size(); i++){
            queryStringBuilder.append("?,");
        }
        queryStringBuilder.setLength(queryStringBuilder.length() - 1);
        queryStringBuilder.append(") ON CONFLICT (");

        for (String onConflictColumn : onConflictColumnNames) {
            queryStringBuilder.append(onConflictColumn).append(",");
        }
        queryStringBuilder.setLength(queryStringBuilder.length() - 1);
        queryStringBuilder.append(") DO UPDATE SET ");
        for (String columnName : columnNames) {
            queryStringBuilder.append(columnName).append("=excluded.").append(columnName).append(",");
        }
        queryStringBuilder.setLength(queryStringBuilder.length() - 1);

        System.out.println(queryStringBuilder);

        return queryStringBuilder.toString();
    }
}