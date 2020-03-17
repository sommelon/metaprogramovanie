package mp.persistence;

import mp.persistence.util.UpsertBuilder;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReflectivePersistenceManager implements PersistenceManager {
    private Connection connection;

    public ReflectivePersistenceManager(Connection databaseConnection) {
        this.connection = databaseConnection;
    }

    @Override
    public void createTables(Class... classes) throws PersistenceException {
        for (Class aClass : classes) {
            if (!aClass.isAnnotationPresent(Entity.class)) {
                throw new PersistenceException("No Entity annotation for class " + aClass.getName());
            }

            List<String> foreignKeys = new ArrayList<>();
            List<String> referencedTables = new ArrayList<>();
            List<String> referencedFields = new ArrayList<>();

            StringBuilder queryStringBuilder = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
            queryStringBuilder.append(aClass.getSimpleName().toLowerCase()).append(" (");
            for (Field field : aClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) {
                    continue;
                }

                queryStringBuilder.append(field.getName()).append(" ");
                queryStringBuilder.append(getSQLType(field.getType().getSimpleName()));
                //TODO polymorfizmus
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    Field idField = getFirstAnnotatedField(field.getType().getDeclaredFields(), Id.class);

                    foreignKeys.add(field.getName());
                    referencedTables.add(field.getType().getSimpleName().toLowerCase()); //TODO field.get(o).getType?
                    referencedFields.add(idField.getName());
                }
                if (field.isAnnotationPresent(Id.class)) {
                    queryStringBuilder.append(" PRIMARY KEY");
                }
                queryStringBuilder.append(",");
            }
            queryStringBuilder.setLength(queryStringBuilder.length() - 1);

            for (int i = 0; i < foreignKeys.size(); i++) {
                queryStringBuilder.append(", FOREIGN KEY(").append(foreignKeys.get(i)).append(")")
                        .append(" REFERENCES ").append(referencedTables.get(i))
                        .append("(").append(referencedFields.get(i)).append(")");
            }

            queryStringBuilder.append(")");
            System.out.println(queryStringBuilder.toString());

            try {
                Statement statement = connection.createStatement();
                statement.executeUpdate(queryStringBuilder.toString());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public <T> List<T> getAll(Class<T> aClass) throws PersistenceException {
        if (!aClass.isAnnotationPresent(Entity.class)) {
            throw new PersistenceException("No Entity annotation for class " + aClass.getName());
        }

        String query = "SELECT * FROM " + aClass.getSimpleName().toLowerCase();

        List<T> objects;
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(query);
            objects = createObjectsFromResultSet(aClass, rs);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return objects;
    }

    @Override
    public <T> T get(Class<T> aClass, int id) throws PersistenceException {
        if (!aClass.isAnnotationPresent(Entity.class)) {
            throw new PersistenceException("No Entity annotation for class " + aClass.getName());
        }

        Field idField = getFirstAnnotatedField(aClass.getDeclaredFields(), Id.class);

        T object;
        try {
            object = getBy(aClass, idField.getName(), id).get(0);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }

        return object;
    }

    @Override
    public <T> List<T> getBy(Class<T> aClass, String fieldName, Object value) throws PersistenceException {
        if (!aClass.isAnnotationPresent(Entity.class)) {
            throw new PersistenceException("No Entity annotation for class " + aClass.getName());
        }

        String query = "SELECT * FROM " + aClass.getSimpleName().toLowerCase() + " WHERE " + fieldName.toLowerCase() + "= ?";

        List<T> objects;
        try {
            PreparedStatement ps = connection.prepareStatement(query);
            ps.setObject(1, value);
            ResultSet rs = ps.executeQuery();
            objects = createObjectsFromResultSet(aClass, rs);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return objects;
    }

    @Override
    public int save(Object object) throws PersistenceException {
        Class aClass = object.getClass();

        if (!aClass.isAnnotationPresent(Entity.class)) {
            throw new PersistenceException("No Entity annotation for class " + aClass.getName());
        }

        List<Object> fieldValues = new ArrayList<>();

        UpsertBuilder upsertBuilder = new UpsertBuilder(aClass.getSimpleName());
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
                upsertBuilder.addOnConflictColumnName(field.getName());
                //Kedze sa robi upsert, tak nemoze byt 0 ID, pretoze ho tam insertne ak to nie je duplikat
                if ((int) fieldValue == 0) {
                    continue;
                }
            }

            fieldValues.add(fieldValue);
            upsertBuilder.addColumnName(field.getName());
        }

        try {
            PreparedStatement ps = connection.prepareStatement(upsertBuilder.toString(), Statement.RETURN_GENERATED_KEYS);
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

    private Field getFirstAnnotatedField(Field[] fields, Class<? extends Annotation> annotationClass) throws PersistenceException {
        for (Field field : fields) {
            if (field.isAnnotationPresent(annotationClass)) {
                return field;
            }
        }
        throw new PersistenceException("No " + annotationClass + " annotation for " + fields[0].getType().getName());
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
