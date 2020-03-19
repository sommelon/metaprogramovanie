package mp.persistence.util.sql;

import java.util.ArrayList;
import java.util.List;

public class PrimaryKey {
    private List<String> columnNames = new ArrayList<>();

    public PrimaryKey(String columnName){
        columnNames.add(columnName);
    }

    public PrimaryKey(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public void addColumnNames(String columnName) {
        columnNames.add(columnName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PRIMARY KEY (");
        for (String columnName : columnNames) {
            sb.append(columnName).append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");

        return sb.toString();
    }
}
