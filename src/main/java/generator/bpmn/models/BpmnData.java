package generator.bpmn.models;

import java.util.List;

public record BpmnData(
        String id,
        String targetNamespace,
        Collaboration collaboration,
        List<ProcessDef> processes
) {}