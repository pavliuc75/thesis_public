package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

/**
 * Represents a BPMN data object reference with parsed components.
 * 
 * Example from BPMN:
 * <bpmn:dataObjectReference id="DataObjectReference_0zlxotf" 
 *                           name="req1: AbsenceRequest&#10;[submitted]" 
 *                           dataObjectRef="DataObject_0w432tm" />
 * 
 * This parses to:
 * - variableName: "req1"
 * - typeName: "AbsenceRequest"
 * - stateName: "submitted"
 */
public record DataObjectReference(
        String id,
        String rawName,        // Original name string from BPMN (e.g., "req1: AbsenceRequest\n[submitted]")
        String dataObjectRef,
        String variableName,   // Parsed variable name (e.g., "req1")
        String typeName,       // Parsed type name (e.g., "AbsenceRequest")
        String stateName       // Parsed state name (e.g., "submitted"), can be null or empty
) {
    /**
     * Factory method to create DataObjectReference by parsing the name string.
     * Expected format: "variableName: TypeName\n[stateName]"
     * 
     * @param id the data object reference ID
     * @param name the raw name string from BPMN
     * @param dataObjectRef the reference to the data object definition
     * @return parsed DataObjectReference
     */
    public static DataObjectReference parse(String id, String name, String dataObjectRef) {
        String variableName = null;
        String typeName = null;
        String stateName = null;

        if (name != null && !name.trim().isEmpty()) {
            // Parse variable name (before colon)
            if (name.contains(":")) {
                variableName = name.substring(0, name.indexOf(":")).trim();
                
                // Parse type name (after colon, before newline or bracket)
                String afterColon = name.substring(name.indexOf(":") + 1).trim();
                
                if (afterColon.contains("\n")) {
                    typeName = afterColon.substring(0, afterColon.indexOf("\n")).trim();
                } else if (afterColon.contains("[")) {
                    typeName = afterColon.substring(0, afterColon.indexOf("[")).trim();
                } else {
                    typeName = afterColon;
                }
            }
            
            // Parse state name (inside brackets)
            if (name.contains("[") && name.contains("]")) {
                int startIdx = name.indexOf("[") + 1;
                int endIdx = name.indexOf("]");
                if (endIdx > startIdx) {
                    String state = name.substring(startIdx, endIdx).trim();
                    stateName = state.isEmpty() ? null : state;
                }
            }
        }

        return new DataObjectReference(id, name, dataObjectRef, variableName, typeName, stateName);
    }
}

