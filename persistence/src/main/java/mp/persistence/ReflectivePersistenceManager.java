package mp.persistence;

import mp.persistence.util.sql.SQLType;
import mp.persistence.util.sql.*;

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
    public void createTables(Class... classes) throws PersistenceException {
        for (Class aClass : classes) {
            if (!aClass.isAnnotationPresent(Entity.class)) {
                throw new PersistenceException("No Entity annotation for class " + aClass.getName());
            }

            CreateTableBuilder createTableBuilder = new CreateTableBuilder(aClass.getSimpleName());
            for (Field field : aClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                Column column = new Column(field.getName(), SQLType.getSQLTypeFromClass((field.getType())));
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    createTableBuilder.addForeignKey(
                            new ForeignKey(field.getName(),
                                    field.getType().getSimpleName(),
                                    getFirstAnnotatedField(field.getType().getDeclaredFields(), Id.class).getName()
                            )
                    );
                }
                if (field.isAnnotationPresent(Id.class)) {
                    column.addConstraint(Constraint.NOT_NULL);
                    column.addConstraint(Constraint.PRIMARY_KEY);
                }
                createTableBuilder.addColumn(column);
            }

            System.out.println(createTableBuilder.toString());

            try {
                Statement statement = connection.createStatement();
                statement.executeUpdate(createTableBuilder.toString());
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

        String query = "SELECT * FROM " + aClass.getSimpleName();

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

        String query = "SELECT * FROM " + aClass.getSimpleName() + " WHERE " + fieldName + "= ?";

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
                //Kedze sa robi upsert, tak nemoze byt ID 0, pretoze ho tam insertne ak to nie je duplikat.
                // Preto sa prerusi cyklus, aby sa nepridal ID stlpec do upsertu.
                if ((int) fieldValue == 0) {
                    continue;
                }
            }

            fieldValues.add(fieldValue);
            upsertBuilder.addColumnName(field.getName());
        }
        System.out.println();
        try {
            PreparedStatement ps = connection.prepareStatement(upsertBuilder.toString(), Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < fieldValues.size(); i++) {
                if (fieldValues.get(i) == null) {
                    ps.setNull(i + 1, Types.NULL);
                } else if (fieldValues.get(i).getClass().getPackageName().startsWith("java.lang")) {
                    ps.setObject(i + 1, fieldValues.get(i));
                } else {
                    Field field = getFirstAnnotatedField(fieldValues.get(i).getClass().getDeclaredFields(), Id.class);

                    field.setAccessible(true);
                    if (field.getInt(fieldValues.get(i)) == 0) {
                        ps.setInt(i + 1, save(fieldValues.get(i)));
                    } else {
                        ps.setInt(i + 1, field.getInt(fieldValues.get(i)));
                    }

                    field.setAccessible(false);
                    break;
                }
                System.out.print(fieldValues.get(i) + ", ");
            }

            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException | IllegalAccessException e) {
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

}
