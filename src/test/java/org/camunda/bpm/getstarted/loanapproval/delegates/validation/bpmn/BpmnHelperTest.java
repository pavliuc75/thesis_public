package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class BpmnHelperTest {
    private static final String VALID_BPMN = "src/main/resources/_valid.bpmn";
    private static final String INVALID_BPMN = "src/main/resources/_invalid.bpmn";
    private static final String DIAGRAM_1_BPMN = "src/main/resources/diagram_1.bpmn";

    @Test
    void shouldValidateWhenValid() {
        File file = new File(VALID_BPMN);
        assertDoesNotThrow(() -> BpmnValidator.validate(file));
    }

    @Test
    void shouldThrowWhenInvalid() {
        File file = new File(INVALID_BPMN);
        assertThrows(Exception.class, () -> BpmnValidator.validate(file));
    }

    @Test
    void shouldParseDataObjectReferences() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        assertNotNull(definitions);
        assertFalse(definitions.processes().isEmpty());

        ProcessDef process = definitions.processes().get(0);
        assertNotNull(process.dataObjectsById());
        assertFalse(process.dataObjectsById().isEmpty());

        // Verify specific data object references exist
        assertTrue(process.dataObjectsById().containsKey("DataObjectReference_0zlxotf"));
        assertTrue(process.dataObjectsById().containsKey("DataObjectReference_0go77dt"));
        assertTrue(process.dataObjectsById().containsKey("DataObjectReference_0cjl98n"));
    }

    @Test
    void shouldParseDataObjectNamesCorrectly() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        DataObjectReference dataObjRef = process.dataObjectsById().get("DataObjectReference_0zlxotf");

        assertNotNull(dataObjRef);
        // Name should be "req1: AbsenceRequest\n[submitted]"
        assertEquals("req1", dataObjRef.variableName());
        assertEquals("AbsenceRequest", dataObjRef.typeName());
        assertEquals("submitted", dataObjRef.stateName());
        assertNotNull(dataObjRef.rawName()); // Original name string is preserved
    }

    @Test
    void shouldParseDataInputAssociations() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        
        // Check "Approve Absence" task (Activity_1f3770r)
        FlowNode approveTask = process.nodesById().get("Activity_1f3770r");
        assertNotNull(approveTask);
        assertNotNull(approveTask.dataInputAssociations());
        assertFalse(approveTask.dataInputAssociations().isEmpty());

        // Should have at least one data input association
        assertTrue(approveTask.dataInputAssociations().stream()
            .anyMatch(dia -> "DataObjectReference_0zlxotf".equals(dia.sourceRef())));
    }

    @Test
    void shouldParseDataOutputAssociations() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        
        // Check "Request Absence" task (Activity_1ktuyru)
        FlowNode requestTask = process.nodesById().get("Activity_1ktuyru");
        assertNotNull(requestTask);
        assertNotNull(requestTask.dataOutputAssociations());
        assertFalse(requestTask.dataOutputAssociations().isEmpty());

        // Should have output to DataObjectReference_0zlxotf
        assertTrue(requestTask.dataOutputAssociations().stream()
            .anyMatch(doa -> "DataObjectReference_0zlxotf".equals(doa.targetRef())));
    }

    @Test
    void shouldParseMultipleDataInputsForSingleTask() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        
        // Check "Remind Line Manager" task (Activity_1q9he4w) - has 2 data inputs
        FlowNode remindTask = process.nodesById().get("Activity_1q9he4w");
        assertNotNull(remindTask);
        assertNotNull(remindTask.dataInputAssociations());
        
        // Should have 2 data inputs
        assertEquals(2, remindTask.dataInputAssociations().size());
        
        // Verify both inputs
        assertTrue(remindTask.dataInputAssociations().stream()
            .anyMatch(dia -> "DataObjectReference_1itk3jv".equals(dia.sourceRef())));
        assertTrue(remindTask.dataInputAssociations().stream()
            .anyMatch(dia -> "DataObjectReference_0go77dt".equals(dia.sourceRef())));
    }

    @Test
    void shouldParseDataObjectWithReviewedState() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        DataObjectReference dataObjRef = process.dataObjectsById().get("DataObjectReference_0go77dt");

        assertNotNull(dataObjRef);
        assertEquals("req1", dataObjRef.variableName());
        assertEquals("AbsenceRequest", dataObjRef.typeName());
        assertEquals("reviewed", dataObjRef.stateName());
    }

    @Test
    void shouldParseDataObjectWithEmptyState() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        // LineManager data object has empty state: "mgr1:\nLineManager\n[]"
        DataObjectReference dataObjRef = process.dataObjectsById().get("DataObjectReference_1itk3jv");

        assertNotNull(dataObjRef);
        assertEquals("mgr1", dataObjRef.variableName());
        assertEquals("LineManager", dataObjRef.typeName());
        // State is empty brackets, should be null
        assertNull(dataObjRef.stateName());
    }

    @Test
    void shouldUseHelperMethodsToGetInputsAndOutputs() {
        String bpmnFilePath = DIAGRAM_1_BPMN;
        BpmnDefinitions definitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        ProcessDef process = definitions.processes().get(0);
        FlowNode approveTask = process.nodesById().get("Activity_1f3770r");

        // Test helper methods
        var inputs = BpmnHelper.getFlowNodeInputs(approveTask, process);
        var outputs = BpmnHelper.getFlowNodeOutputs(approveTask, process);

        assertFalse(inputs.isEmpty());
        assertFalse(outputs.isEmpty());

        // Verify we get the actual parsed data objects
        assertTrue(inputs.stream().anyMatch(input -> "req1".equals(input.variableName())));
        assertTrue(outputs.stream().anyMatch(output -> "req1".equals(output.variableName())));
    }
}