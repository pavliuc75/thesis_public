package generator.bpmn.models;

/**
 * Represents a BPMN data output association.
 * Links a task output to a data object reference.
 * 
 * Example from BPMN:
 * <bpmn:dataOutputAssociation id="DataOutputAssociation_032pdfk">
 *   <bpmn:targetRef>DataObjectReference_0zlxotf</bpmn:targetRef>
 * </bpmn:dataOutputAssociation>
 */
public record DataOutputAssociation(
        String id,
        String targetRef  // ID of the DataObjectReference
) {
}

