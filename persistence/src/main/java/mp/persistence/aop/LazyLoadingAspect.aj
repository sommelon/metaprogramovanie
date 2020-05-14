package mp.persistence.aop;

import mp.persistence.ReflectivePersistenceManager;
import mp.persistence.util.HelperMethods;

import javax.persistence.Id;
import java.lang.reflect.Field;

public aspect LazyLoadingAspect {

    pointcut getManyToOneField():
            get(* *) &&
            @annotation(javax.persistence.ManyToOne);

    Object around(): getManyToOneField() {
        Object value = proceed();
        if (value == null) {
            String fieldName = thisJoinPoint.getSignature().getName();
            Object obj = thisJoinPoint.getThis();

            Field field = null;
            try {
                ReflectivePersistenceManager persistenceManager = ReflectivePersistenceManagerAspect.aspectOf().getPersistenceManager();
                field = obj.getClass().getDeclaredField(fieldName);
                Field idField = HelperMethods.getFirstAnnotatedField(obj.getClass().getDeclaredFields(), Id.class);

                idField.setAccessible(true);
                value = persistenceManager.get(field.getType(), (Integer) idField.get(obj));
                idField.setAccessible(false);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return value;
    }
}
