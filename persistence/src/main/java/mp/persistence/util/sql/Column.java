package mp.persistence.util.sql;

import java.util.EnumSet;

public class Column {
    private String name;
    private SQLType sqlType;
    private int length;
    private EnumSet<Constraint> constraints = EnumSet.noneOf(Constraint.class);

    public Column(String name, SQLType sqlType) {
        this.name = name;
        this.sqlType = sqlType;
    }

    public Column(String name, SQLType sqlType, EnumSet<Constraint> constraints) {
        this.name = name;
        this.sqlType = sqlType;
        this.constraints = constraints;
    }

    public void setSqlType(SQLType sqlType) {
        this.sqlType = sqlType;
    }

    public void setLength(int length) {
        this.length = length;
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
