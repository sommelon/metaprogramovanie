package mp.persistence;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReflectivePersistenceManager implements PersistenceManager {
    private Connection connection;

    public ReflectivePersistenceManager(Connection databaseConnection) {
        this.connection = databaseConnection;
    }

    @Override
    public void createTables(Class... classes) {
        for (Class aClass : classes) {
            if (!aClass.isAnnotationPresent(Entity.class)) {
                continue;
            }

            List<String> foreignKeys = new ArrayList<>();
            List<String> referencedTables = new ArrayList<>();
            List<String> referencedFields = new ArrayList<>();

            StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
            sb.append(aClass.getSimpleName().toLowerCase()).append(" (");
            for (Field field : aClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) {
                    continue;
                }

                sb.append(field.getName()).append(" ");
                sb.append(getSQLType(field.getType().getSimpleName()));
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    Field idField = getFirstAnnotatedField(field.getType().getDeclaredFields(), Id.class);

                    if (idField == null) {
                        throw new PersistenceException("No ID field set for class " + field.getType().getSimpleName());
                    }

                    foreignKeys.add(field.getName());
                    referencedTables.add(field.getType().getSimpleName().toLowerCase());
                    referencedFields.add(idField.getName());
                }
                if (field.isAnnotationPresent(Id.class)) {
                    sb.append(" PRIMARY KEY");
                }
                sb.append(",");
            }
            sb.setLength(sb.length() - 1);

            for (int i = 0; i < foreignKeys.size(); i++) {
                sb.append(", FOREIGN KEY(").append(foreignKeys.get(i)).append(")")
                        .append(" REFERENCES ").append(referencedTables.get(i))
                        .append("(").append(referencedFields.get(i)).append(")");
                ;
            }

            sb.append(")");
            System.out.println(sb.toString());

            try {
                Statement statement = connection.createStatement();
                statement.executeUpdate(sb.toString());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public <T> List<T> getAll(Class<T> aClass) throws PersistenceException {
        StringBuilder sb = new StringBuilder("SELECT * FROM ");
        sb.append(aClass.getSimpleName().toLowerCase());

        List<T> objects;
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sb.toString());
            objects = createObjectsFromResultSet(aClass, rs);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return objects;
    }

    @Override
    public <T> T get(Class<T> aClass, int id) throws PersistenceException {
        Field idField = getFirstAnnotatedField(aClass.getDeclaredFields(), Id.class);
        T object;

        try {
            object = aClass.getConstructor().newInstance();
            idField.setAccessible(true);
            idField.set(object, id);
            idField.setAccessible(false);
            object = getBy(aClass, idField.getName(), id).get(0);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException
                | IndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }

        return object;
    }

    private Field getFirstAnnotatedField(Field[] fields, Class<? extends Annotation> annotationClass) throws PersistenceException {
        for (Field field : fields) {
            if (field.isAnnotationPresent(annotationClass)) {
                return field;
            }
        }
        throw new PersistenceException("No " + annotationClass + " field for " + fields[0].getType().getSimpleName());
    }

    @Override
    public <T> List<T> getBy(Class<T> aClass, String fieldName, Object value) {
        StringBuilder query = new StringBuilder("SELECT * FROM ").append(aClass.getSimpleName().toLowerCase())
                .append(" WHERE ").append(fieldName.toLowerCase()).append("= ?");

        ResultSet rs;
        try {
            PreparedStatement ps = connection.prepareStatement(query.toString());
            ps.setObject(1, value); //TODO vymysliet ako to urobit
            rs = ps.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return createObjectsFromResultSet(aClass, rs);
    }

    private <T> List<T> createObjectsFromResultSet(Class<T> aClass, ResultSet rs) {
        if (aClass == null || rs == null) {
            return null;
        }

        List<T> objects = new ArrayList<>();

        try {
            while (rs.next()) {
                T object = aClass.getConstructor().newInstance();
                for (Field field : aClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Transient.class)) {
                        continue;
                    }

                    field.setAccessible(true);
                    Object fieldValue = rs.getObject(field.getName());
                    if (field.isAnnotationPresent(ManyToOne.class)) {
                        field.set(object, get(field.getType(), (Integer) fieldValue)); //predpoklada sa, ze ID je int
                    } else {
                        field.set(object, fieldValue);
                    }
                    field.setAccessible(false);
                }
                objects.add(object);
            }
        } catch (SQLException | InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }

        return objects;
    }

    @Override
    public int save(Object object) throws PersistenceException {
        Class aClass = object.getClass();

        if (!aClass.isAnnotationPresent(Entity.class)) {
            return 0;
        }

        Field idField = null;
        List<Object> fieldValues = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();

        StringBuilder sqlStringBuilder = new StringBuilder("INSERT INTO ");
        StringBuilder placeholdersStringBuilder = new StringBuilder();
        sqlStringBuilder.append(aClass.getSimpleName()).append(" (");
        for (Field field : aClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            Object fieldValue = null;
            try {
                field.setAccessible(true);
                fieldValue = field.get(object);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            if (field.isAnnotationPresent(Id.class)) {
                idField = field;
                if ((int)fieldValue == 0) {
                    continue;
                }
            }

            fieldValues.add(fieldValue);
            fieldNames.add(field.getName());
            sqlStringBuilder.append(field.getName()).append(",");
            placeholdersStringBuilder.append("?,");
        }

        sqlStringBuilder.setLength(sqlStringBuilder.length() - 1);
        placeholdersStringBuilder.setLength(placeholdersStringBuilder.length() - 1);
        sqlStringBuilder.append(") VALUES (");
        sqlStringBuilder.append(placeholdersStringBuilder.toString());
        sqlStringBuilder.append(") ON CONFLICT (");
        if (idField != null) {
            sqlStringBuilder.append(idField.getName());
        }
        sqlStringBuilder.append(") DO UPDATE SET ");
        for (String fieldName : fieldNames) {
            sqlStringBuilder.append(fieldName).append("=excluded.").append(fieldName).append(",");
        }
        sqlStringBuilder.setLength(sqlStringBuilder.length() - 1);

        System.out.println(sqlStringBuilder);

        try {
            PreparedStatement ps = connection.prepareStatement(sqlStringBuilder.toString(), Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            for (Object fieldValue : fieldValues) {
                if (fieldValue == null) {
                    ps.setNull(i, Types.NULL);
                } else if (fieldValue.getClass().getPackageName().startsWith("java.lang")) {
                    ps.setObject(i, fieldValue);
                } else {
                    Field field = getFirstAnnotatedField(fieldValue.getClass().getDeclaredFields(), Id.class);

                    try {
                        field.setAccessible(true);
                        if (field.getInt(fieldValue) == 0) {
                            ps.setInt(i, save(fieldValue));
                        } else {
                            ps.setInt(i, field.getInt(fieldValue));
                        }

                        field.setAccessible(false);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                System.out.print(fieldValue + ", ");
                i++;
            }
            System.out.println();

            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private String getSQLType(String type) {
        String SQLType = "INTEGER"; //Ak type nie je zakladny JAVA objekt, je to cudzi kluc a predpoklada sa, ze ID je int
        switch (type.toLowerCase()) {
            case "int":
            case "integer":
            case "short":
            case "long":
            case "byte":
                SQLType = "INTEGER";
                break;
            case "double":
            case "float":
                SQLType = "REAL";
                break;
            case "string":
            case "char":
            case "character":
            case "boolean":
                SQLType = "TEXT";
                break;
        }

        return SQLType;
    }
}
