package mp.persistence;

import mp.persistence.util.HelperMethods;
import mp.persistence.util.sql.UpsertBuilder;

import javax.persistence.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    public void createTables() throws PersistenceException {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(TableCreationProcessor.PATH);

        if (inputStream == null) throw new PersistenceException(TableCreationProcessor.PATH + " not found");

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Statement statement = connection.createStatement();
                statement.executeUpdate(line);
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public <T> List<T> getAll(Class<T> aClass) throws PersistenceException {
        if (!aClass.isAnnotationPresent(Entity.class)) {
            throw new PersistenceException("No Entity annotation for class " + aClass.getName());
        }

        String query = "SELECT * FROM " + HelperMethods.getTableNameByClass(aClass);

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

        Field idField = HelperMethods.getFirstAnnotatedField(aClass.getDeclaredFields(), Id.class);

        T object = null;

        List<T> list = getBy(aClass, HelperMethods.getColumnNameByField(idField), id);
        if (!list.isEmpty()) {
            object = list.get(0);
        }

        return object;
    }

    @Override
    public <T> List<T> getBy(Class<T> aClass, String fieldName, Object value) throws PersistenceException {
        if (!aClass.isAnnotationPresent(Entity.class)) {
            throw new PersistenceException("No Entity annotation for class " + aClass.getName());
        }

        String query = "SELECT * FROM " + HelperMethods.getTableNameByClass(aClass) + " WHERE " + fieldName + "= ?";

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

        UpsertBuilder upsertBuilder = new UpsertBuilder(HelperMethods.getTableNameByClass(aClass));
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

            if (fieldValue != null && fieldValue.getClass().getPackageName().startsWith("org.aspectj")) {
                continue;
            }

            if (field.isAnnotationPresent(Id.class)) {
                upsertBuilder.addOnConflictColumnName(HelperMethods.getColumnNameByField(field));
                //Kedze sa robi upsert, tak nemoze byt ID 0, pretoze ho tam insertne ak to nie je duplikat.
                // Preto sa prerusi cyklus, aby sa nepridal ID stlpec do upsertu.
                if ((int) fieldValue == 0) {
                    continue;
                }
            }

            fieldValues.add(fieldValue);
            upsertBuilder.addColumnName(HelperMethods.getColumnNameByField(field));
        }

        System.out.println();

        try {
            PreparedStatement ps = connection.prepareStatement(upsertBuilder.toString(), Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < fieldValues.size(); i++) {
                Object fieldValue = fieldValues.get(i);

                if (fieldValue == null) {
                    ps.setNull(i + 1, Types.NULL);
                } else if (fieldValue.getClass().getPackageName().startsWith("java.lang")) {
                    ps.setObject(i + 1, fieldValue);
                } else {
                    Field idFieldOfAReferencedClass = HelperMethods.getFirstAnnotatedField(fieldValue.getClass().getDeclaredFields(), Id.class);

                    idFieldOfAReferencedClass.setAccessible(true);
                    if (idFieldOfAReferencedClass.getInt(fieldValue) == 0) {
                        int foreignKey = save(fieldValue);
                        ps.setInt(i + 1, foreignKey);
                    } else {
                        ps.setInt(i + 1, idFieldOfAReferencedClass.getInt(fieldValue));
                    }
                    idFieldOfAReferencedClass.setAccessible(false);
                }

            }
            System.out.println(upsertBuilder.toString());
            System.out.print("  ");
            fieldValues.forEach(fieldValue -> System.out.print(fieldValue +", "));
            System.out.println();

            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException | IllegalAccessException e) {
            e.printStackTrace();
            throw new PersistenceException(e.getMessage());
        }

        return 0;
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
                    if (field.isAnnotationPresent(Transient.class) ||
                            field.getType().getPackageName().startsWith("org.aspectj")) {
                        continue;
                    }

                    field.setAccessible(true);
                    Object fieldValue = rs.getObject(HelperMethods.getColumnNameByField(field));
                    if (field.isAnnotationPresent(ManyToOne.class)) {
                        ManyToOne annotation = field.getAnnotation(ManyToOne.class);

                        if (annotation.fetch() != FetchType.LAZY) {
                            Class classToLoad = field.getType();
                            if (annotation.targetEntity() != void.class){
                                classToLoad = annotation.targetEntity();
                            }
                            field.set(object, get(classToLoad, (Integer) fieldValue)); //predpoklada sa, ze ID je int
                        }
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

    public Connection getConnection() {
        return connection;
    }
}
