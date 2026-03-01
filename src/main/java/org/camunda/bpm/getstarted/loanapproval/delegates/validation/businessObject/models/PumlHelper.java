package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models;

import java.io.*;
import java.io.IOException;
import java.util.Set;

public class PumlHelper {
    
    // TODO: Expand this list with all Bonita/SQL reserved keywords
    // Common Bonita/SQL reserved keywords that should not be used as field names
    private static final Set<String> BONITA_RESERVED_KEYWORDS = Set.of(
        "timestamp",
        "date",
        "time",
        "datetime",
        "user",
        "group",
        "role",
        "order",
        "table",
        "index",
        "key",
        "value",
        "select",
        "insert",
        "update",
        "delete",
        "from",
        "where",
        "join",
        "left",
        "right",
        "inner",
        "outer",
        "on",
        "and",
        "or",
        "not",
        "null",
        "true",
        "false",
        "case",
        "when",
        "then",
        "else",
        "end"
    );
    
    public static PumlDiagram loadFromFile(String filePath) throws IOException {
        try (InputStream in = new FileInputStream(filePath)) {
            return new SimplePlantUmlParser().parse(in);
        }
    }

    public static PumlDiagram loadFromResources(String resourcePath) throws IOException {
        try (InputStream in = PumlHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);
            return new SimplePlantUmlParser().parse(in);
        }
    }

    /**
     * Validates that no field names in PUML classes use Bonita reserved keywords.
     * 
     * @param pumlDiagram the PUML diagram to validate
     * @throws IllegalStateException if any field uses a reserved keyword
     */
    public static void validateNoReservedKeywordsInBusiessObjectClasses(PumlDiagram pumlDiagram) {
        if (pumlDiagram == null || pumlDiagram.classes() == null) {
            return;
        }

        for (PumlClass pumlClass : pumlDiagram.classes().values()) {
            if (pumlClass.fields() == null) {
                continue;
            }

            for (PumlField field : pumlClass.fields()) {
                String fieldName = field.name();
                if (fieldName != null && BONITA_RESERVED_KEYWORDS.contains(fieldName.toLowerCase())) {
                    throw new IllegalStateException(
                        String.format("Class '%s' has field '%s' which is a Bonita reserved keyword. " +
                                    "Please rename this field to avoid conflicts. " +
                                    "Reserved keywords: %s",
                                pumlClass.name(),
                                fieldName,
                                BONITA_RESERVED_KEYWORDS));
                }
            }
        }
    }
}
