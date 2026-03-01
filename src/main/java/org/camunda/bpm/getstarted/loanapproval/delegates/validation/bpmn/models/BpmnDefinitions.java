package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

import java.util.List;

public record BpmnDefinitions(
        String id,
        String targetNamespace,
        Collaboration collaboration,
        List<ProcessDef> processes
) {}