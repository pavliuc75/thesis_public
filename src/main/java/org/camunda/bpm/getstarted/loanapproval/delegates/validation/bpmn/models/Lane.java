package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Lane(
        String id,
        String name,
        List<String> flowNodeRefs, // list of node IDs (exactly like <flowNodeRef>)
        Map<String, String> vendorAttributes, // vendor-specific attributes
        String resolvedActor) {
    // Constructor with default empty map for vendorAttributes for backward compatibility
    public Lane(String id, String name, List<String> flowNodeRefs) {
        this(id, name, flowNodeRefs, new HashMap<>(), null);
    }
}
