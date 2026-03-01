package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

import lombok.Builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
public record FlowNode(
        String id,
        String type,  // e.g., "userTask", "serviceTask", "startEvent", "endEvent", "exclusiveGateway", "boundaryEvent"
        String name,
        @Builder.Default
        Map<String, String> vendorAttributes, // vendor-specific attributes (e.g., "camunda:assignee" -> "alice")
        @Builder.Default
        List<DataInputAssociation> dataInputAssociations,  // inputs required for task execution
        @Builder.Default
        List<DataOutputAssociation> dataOutputAssociations,  // outputs produced when task finishes

        //for user tasks
        String resolvedActor,
        String resolvedFormRef,
        String resolvedFormOutputVariableName,

        //for service tasks
        String resolvedEmailConfigFileName,
        String resolvedEmailTemplateFileName,
        String resolvedRestCallFileName,

        //for events
        String iso8601time,

        //for business rule tasks
        String dmnRef,
        String dmnResultVariable
) {
    // Default values for @Builder.Default fields
    public FlowNode {
        if (vendorAttributes == null) {
            vendorAttributes = new HashMap<>();
        }
        if (dataInputAssociations == null) {
            dataInputAssociations = new ArrayList<>();
        }
        if (dataOutputAssociations == null) {
            dataOutputAssociations = new ArrayList<>();
        }
    }

    // Constructor with default empty collections for backward compatibility
    public FlowNode(String id, String type, String name) {
        this(id, type, name, new HashMap<>(), new ArrayList<>(), new ArrayList<>(), null, null, null, null, null, null, null, null, null);
    }

    // Custom toBuilder method for safe copying with modifications
    public FlowNodeBuilder toBuilder() {
        return FlowNode.builder()
                .id(this.id)
                .type(this.type)
                .name(this.name)
                .vendorAttributes(this.vendorAttributes)
                .dataInputAssociations(this.dataInputAssociations)
                .dataOutputAssociations(this.dataOutputAssociations)
                .resolvedActor(this.resolvedActor)
                .resolvedFormRef(this.resolvedFormRef)
                .resolvedFormOutputVariableName(this.resolvedFormOutputVariableName)
                .resolvedEmailConfigFileName(this.resolvedEmailConfigFileName)
                .resolvedEmailTemplateFileName(this.resolvedEmailTemplateFileName)
                .resolvedRestCallFileName(this.resolvedRestCallFileName)
                .iso8601time(this.iso8601time)
                .dmnRef(this.dmnRef)
                .dmnResultVariable(this.dmnResultVariable);
    }
}
