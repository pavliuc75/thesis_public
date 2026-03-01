package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

import java.util.Map;

public record ProcessDef(
        String id,
        String name,
        boolean isExecutable,
        LaneSet laneSet,
        Map<String, FlowNode> nodesById,         // StartEvent, Tasks, Gateways, EndEvent, BoundaryEvent
        Map<String, DataObjectReference> dataObjectsById,  // Data object references in the process
        Map<String, SequenceFlow> flowsById      // SequenceFlow graph edges
) {

}
