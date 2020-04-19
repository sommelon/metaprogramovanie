package mp.persistence;

import mp.persistence.util.HelperMethods;
import mp.persistence.util.sql.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;
import java.util.Set;

@SupportedAnnotationTypes("javax.persistence.Entity")
@SupportedSourceVersion(SourceVersion.RELEASE_10)
public class TableCreationProcessor extends AbstractProcessor {
    public static final String PATH = "META-INF/services/CreateTables.sql";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            FileObject fo = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                    "", PATH);
            Writer writer = fo.openWriter();

            roundEnv.getElementsAnnotatedWith(Entity.class)
                    .forEach(entityClass -> {
                        try {
                            writer.write(processEntityClass(entityClass));
                            writer.write("\n");
                        } catch (IOException e) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                        }
                    });

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String processEntityClass(Element entityClass) throws IOException {
        TypeElement typeElement = (TypeElement) entityClass;
        CreateTableBuilder createTableBuilder = new CreateTableBuilder(HelperMethods.getTableNameByTypeElement(typeElement));

        typeElement.getEnclosedElements().stream()
                .filter(enclosedElement -> enclosedElement.getKind() == ElementKind.FIELD)
                .filter(field -> field.getAnnotation(Transient.class) == null)
                .forEach(persistentField -> {
                    processField((VariableElement) persistentField, createTableBuilder);
                });

        return createTableBuilder.toString() + ";";
    }

    private void processField(VariableElement field, CreateTableBuilder createTableBuilder) {
        Column column = new Column(HelperMethods.getColumnNameByVariableElement(field), SQLType.getSQLTypeFromClass(field.getClass()));
        javax.persistence.Column columnAnnotation = field.getAnnotation(javax.persistence.Column.class);
        if (columnAnnotation != null) {
            column.setLength(columnAnnotation.length());
            if (!field.getAnnotation(javax.persistence.Column.class).nullable()) {
                column.addConstraint(Constraint.NOT_NULL);
            }
        }

        ManyToOne manyToOneAnnotation = field.getAnnotation(ManyToOne.class);
        if (manyToOneAnnotation != null) {
            TypeElement fieldType = (TypeElement) processingEnv.getTypeUtils().asElement(field.asType());
            VariableElement idField = ((VariableElement) fieldType.getEnclosedElements().stream()
                    .filter(enclosedElement -> enclosedElement.getKind() == ElementKind.FIELD)
                    .filter(f -> f.getAnnotation(Id.class) != null)
                    .findFirst().get());

            createTableBuilder.addForeignKey(
                    new ForeignKey(HelperMethods.getColumnNameByVariableElement(field),
                            HelperMethods.getTableNameByTypeElement(fieldType),
                            HelperMethods.getColumnNameByVariableElement(idField)
                    )
            );
        }

        if (field.getAnnotation(Id.class) != null) {
            column.addConstraint(Constraint.NOT_NULL);
            column.addConstraint(Constraint.PRIMARY_KEY);
        }

        createTableBuilder.addColumn(column);
    }
}
