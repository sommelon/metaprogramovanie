package mp.persistence.aop;

import mp.persistence.ReflectivePersistenceManager;
import mp.persistence.util.HelperMethods;

import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public aspect LazyLoadingAspect {
    private ReflectivePersistenceManager persistenceManager;

    pointcut getManyToOneField(Object obj):
            get(* *) &&
            @annotation(javax.persistence.ManyToOne)
            && this(obj);

    Object around(Object value): getManyToOneField(value) {
        String fieldName = thisJoinPoint.getSignature().getName();

        try {
            Field field = value.getClass().getDeclaredField(fieldName);

            field.setAccessible(true);
            if (field.get(value) == null) {
                persistenceManager = ReflectivePersistenceManagerAspect.aspectOf().getPersistenceManager();

                ManyToOne annotation = field.getAnnotation(ManyToOne.class);
                if (annotation.fetch() == FetchType.LAZY) {
                    Field idField = HelperMethods.getFirstAnnotatedField(value.getClass().getDeclaredFields(), Id.class);

                    idField.setAccessible(true);
                    int foreignKeyId = getForeignKeyId(field, idField, (Integer) idField.get(value));
                    idField.setAccessible(false);

                    Class<?> classToLoad = field.getType();
                    if (annotation.targetEntity() != void.class){
                        classToLoad = annotation.targetEntity();
                    }

                    Object loadedObject = persistenceManager.get(classToLoad, foreignKeyId);
                    field.set(value, loadedObject);
                    System.out.println("LAZY LOADED "+ loadedObject);
                }
            }
            field.setAccessible(false);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        return proceed(value);
    }

    private int getForeignKeyId(Field fieldToLoad, Field idField, int idValue){
        int id = 0;

        Connection connection = persistenceManager.getConnection();
        try {
            String foreignKeyColumnName = HelperMethods.getColumnNameByField(fieldToLoad);
            String tableName = HelperMethods.getTableNameByClass(fieldToLoad.getDeclaringClass());
            String idColumnName = HelperMethods.getColumnNameByField(idField);

            PreparedStatement ps = connection.prepareStatement(
                    "SELECT "+ foreignKeyColumnName +" FROM "+ tableName +" WHERE "+ idColumnName +" = ?");
            ps.setInt(1, idValue);
            ResultSet rs = ps.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return id;
    }
}
