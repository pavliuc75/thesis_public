package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

/**
 * Represents a BPMN data input association.
 * Links a data object reference to a task as input.
 * 
 * Example from BPMN:
 * <bpmn:dataInputAssociation id="DataInputAssociation_154nttf">
 *   <bpmn:sourceRef>DataObjectReference_0zlxotf</bpmn:sourceRef>
 *   <bpmn:targetRef>Property_1c735ia</bpmn:targetRef>
 * </bpmn:dataInputAssociation>
 */
public record DataInputAssociation(
        String id,
        String sourceRef,  // ID of the DataObjectReference
        String targetRef   // Usually a property placeholder
) {
}

