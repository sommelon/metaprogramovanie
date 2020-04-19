package mp.persistence.util;

import mp.persistence.PersistenceException;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.persistence.Entity;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public class HelperMethods {
    public static Field getFirstAnnotatedField(Field[] fields, Class<? extends Annotation> annotationClass) throws PersistenceException {
        for (Field field : fields) {
            if (field.isAnnotationPresent(annotationClass)) {
                return field;
            }
        }
        throw new PersistenceException("No " + annotationClass + " annotation for " + fields[0].getType().getName());
    }

    public static String getColumnNameByField(Field field){
        String columnName = "";
        if(field.isAnnotationPresent(javax.persistence.Column.class)) {
            columnName = ((javax.persistence.Column) field.getAnnotation(javax.persistence.Column.class)).name();
        }
        if(columnName.isEmpty()){
            columnName = field.getName();
        }
        return columnName;
    }

    public static String getTableNameByClass(Class aClass){
        String tableName = ((Entity) aClass.getAnnotation(Entity.class)).name();
        if(tableName.isEmpty()){
            tableName = aClass.getSimpleName();
        }
        return tableName;
    }


    public static String getColumnNameByVariableElement(VariableElement variableElement){
        String columnName = "";
        javax.persistence.Column annotation = variableElement.getAnnotation(javax.persistence.Column.class);
        if(annotation != null) {
            columnName = annotation.name();
        }
        if(columnName.isEmpty()){
            columnName = variableElement.getSimpleName().toString();
        }
        return columnName;
    }

    public static String getTableNameByTypeElement(TypeElement typeElement){
        String tableName = ((Entity) typeElement.getAnnotation(Entity.class)).name();
        if(tableName.isEmpty()){
            tableName = typeElement.getSimpleName().toString();
        }
        return tableName;
    }
}
