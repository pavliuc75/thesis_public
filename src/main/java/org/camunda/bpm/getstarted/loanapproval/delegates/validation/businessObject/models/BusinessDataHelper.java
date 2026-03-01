package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models;

import java.io.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.BusinessDataStates;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.ClassType;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.State;

public class BusinessDataHelper {
    
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
    
    public static BusinessData loadFromFile(String filePath) throws IOException {
        if (filePath == null) {return null;}

        try (InputStream in = new FileInputStream(filePath)) {
            return new SimplePlantUmlParser().parse(in);
        }
    }

    public static BusinessData loadFromResources(String resourcePath) throws IOException {
        try (InputStream in = BusinessDataHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
    public static void validateNoReservedKeywordsInBusiessObjectClasses(BusinessData pumlDiagram) {
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

    /**
     * Validates that all required fields defined in state configurations exist as attributes
     * in the corresponding PUML class definitions.
     * Ensures consistency between state requirements and actual class structures.
     *
     * @param businessObjectStatesData the loaded business object state data with required fields
     * @param pumlDiagram              the parsed PUML diagram containing class definitions
     * @throws IllegalStateException if any required field does not exist in its PUML class
     */
    public static void validateStatesExistInBusinessData(BusinessDataStates businessObjectStatesData,
                                                         BusinessData pumlDiagram) {
        if (businessObjectStatesData == null || businessObjectStatesData.classes == null) {
            return; // Nothing to validate
        }

        if (pumlDiagram == null || pumlDiagram.classes() == null) {
            throw new IllegalStateException("PUML diagram or classes map is null. Cannot validate state fields.");
        }

        for (ClassType classType : businessObjectStatesData.classes) {
            String className = classType.name;

            // Find corresponding PUML class
            PumlClass pumlClass = pumlDiagram.classes().get(className);

            if (pumlClass == null) {
                throw new IllegalStateException(
                        String.format("Class '%s' is defined in business object states but does not exist in PUML diagram. " +
                                        "Available PUML classes: %s",
                                className,
                                pumlDiagram.classes().keySet()));
            }

            // Check each state's required fields
            if (classType.states != null) {
                for (State state : classType.states) {

                    if (state.requiredFields == null || state.requiredFields.isEmpty()) {
                        continue; // No fields to validate for this state
                    }

                    // Get all field names from the PUML class
                    List<String> pumlFieldNames = pumlClass.fields() != null
                            ? pumlClass.fields().stream()
                                    .map(PumlField::name)
                                    .toList()
                            : new ArrayList<>();

                    // Check each required field exists in PUML class
                    for (String requiredField : state.requiredFields) {
                        if (!pumlFieldNames.contains(requiredField)) {
                            throw new IllegalStateException(
                                    String.format("State '%s' of class '%s' requires field '%s', " +
                                                    "but field '%s' does not exist in the PUML class definition. " +
                                                    "Available fields in '%s': %s",
                                            state.name,
                                            className,
                                            requiredField,
                                            requiredField,
                                            className,
                                            pumlFieldNames));
                        }
                    }
                }
            }
        }
    }
}
