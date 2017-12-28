package com.hazelcast.simplemap.impl;

import com.hazelcast.simplemap.ProjectionInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class ProjectionCodeGenerator extends ScanCodeGenerator {

    private final ProjectionInfo projectionInfo;
    private final Class<?> extractionClass;

    public ProjectionCodeGenerator(String compiledQueryUuid,
                                   ProjectionInfo projectionInfo,
                                   RecordMetadata recordMetadata) {
        super("ProjectionScan_" + compiledQueryUuid, projectionInfo.getPredicate(), recordMetadata);

        this.projectionInfo = projectionInfo;
        try {
            this.extractionClass = ProjectionCodeGenerator.class.getClassLoader().loadClass(projectionInfo.getClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void generate() {
        append("import java.util.*;\n");
        append("public class " + className + " extends com.hazelcast.simplemap.impl.ProjectionScan {\n");

        generateRunMethod();
        generateBindFields();
        generateBindMethod();

        append("}\n");
    }

    private void generateRunMethod() {
        append("    public void run(){\n");
        append("       long offset=slabPointer;\n");
        append("       " + extractionClass.getName() + " object = new " + extractionClass.getName() + "();\n");
        append("       for(long l=0; l<recordIndex; l++){\n");
        append("           if(");
        toCode(predicate);
        append("){\n");

        for (Field field : extractedFields()) {
            append("               object.").append(field.getName()).append(" = ");
            generateGetField(field.getName());
            append(";\n");
        }
        append("               consumer.accept(object);\n");
        append("           }\n");
        append("           offset += recordDataSize;\n");
        append("        }\n");
        append("    }\n");
    }

    private Set<Field> extractedFields() {
        Set<Field> fields = new HashSet<Field>();

        for (Field f : extractionClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }

            Field recordField = recordMetadata.getField(f.getName());
            if (recordField == null) {
                throw new RuntimeException(
                        "Field '" + extractionClass.getName() + '.' + f.getName()
                                + "' is not found on value-class '" + recordMetadata.getValueClass() + "'");
            }

            if (!recordField.getType().equals(f.getType())) {
                throw new RuntimeException(
                        "Field '" + extractionClass.getName() + '.' + f.getName()
                                + "' has a different type compared to '" + recordMetadata.getValueClass() + "'");
            }

            fields.add(f);
        }

        return fields;
    }
}
