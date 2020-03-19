package mp.persistence.util.sql;

import java.util.ArrayList;
import java.util.List;

public class Column {
    private String name;
    private SQLType sqlType;
    private List<Constraint> constraints = new ArrayList<>();

    public Column(String name, SQLType sqlType) {
        this.name = name;
        this.sqlType = sqlType;
    }

    public Column(String name, SQLType sqlType, List<Constraint> constraints) {
        this.name = name;
        this.sqlType = sqlType;
        this.constraints = constraints;
    }

    public void setSqlType(SQLType sqlType) {
        this.sqlType = sqlType;
    }

    public void addConstraint(Constraint constraint){
        constraints.add(constraint);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name +" "+ sqlType.toString());
        for (Constraint constraint : constraints) {
            sb.append(" ").append(constraint.toString());
        }

        return sb.toString();
    }
}
