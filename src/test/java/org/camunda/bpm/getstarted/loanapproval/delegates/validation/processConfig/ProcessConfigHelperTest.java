package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.*;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.BusinessData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.BusinessDataHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.BusinessDataStates;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.StatesHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.ClassType;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.State;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.FormDataObjectPair;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Process;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessConfigHelperTest {
    private static final String CONFIG_FILE_PATH = "src/main/resources/models/config.json";
    private static final String DIAGRAM_1_BPMN = "src/main/resources/diagram_1.bpmn";
    private static final String BUSINESS_OBJECTS_PUML = "src/main/resources/models/business_objects.puml";
    private static final String STATES_JSON = "src/main/resources/models/states.json";
    private static final String REQUEST_ABSENCE_FORM = "models/form/request_absence_form.json";
    private static final String APPROVE_ABSENCE_FORM = "models/form/approve_absence_form.json";
    private static final String PROVIDE_FURTHER_EXPLANATION_FORM = "models/form/provide_further_explanation_form.json";
    private static final String SOME_OTHER_FORM = "models/form/some-other-form.json";
    private static final String FILE_PREFIX = "${file:forms/";
    private static final String FILE_SUFFIX = "}";

    @Test
    void shouldLoadConfigFileSuccessfully() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        assertNotNull(configFile);
        assertNotNull(configFile.processes);
        assertFalse(configFile.processes.isEmpty());
    }

    @Test
    void shouldParseTasksFromConfig() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        assertNotNull(process);
        assertNotNull(process.config);
        assertNotNull(process.config.tasks);
        assertFalse(process.config.tasks.isEmpty());

        // Verify specific tasks exist
        assertTrue(process.config.tasks.stream().anyMatch(t -> "Request Absence".equals(t.name)));
        assertTrue(process.config.tasks.stream().anyMatch(t -> "Approve Absence".equals(t.name)));
        assertTrue(process.config.tasks.stream().anyMatch(t -> "Remind Line Manager".equals(t.name)));
        assertTrue(process.config.tasks.stream().anyMatch(t -> "Provide further Explanation".equals(t.name)));
        assertTrue(process.config.tasks.stream().anyMatch(t -> "Log Absence in System".equals(t.name)));
        assertTrue(process.config.tasks.stream().anyMatch(t -> "Notify Employee".equals(t.name)));
    }

    @Test
    void shouldParseEventsFromConfig() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        assertNotNull(process);
        assertNotNull(process.config);
        assertNotNull(process.config.events);
        assertFalse(process.config.events.isEmpty());

        // Verify specific events exist
        assertTrue(process.config.events.stream().anyMatch(e -> "employee_needs_time_off".equals(e.name)));
        assertTrue(process.config.events.stream().anyMatch(e -> "timer1".equals(e.name)));
    }

    @Test
    void shouldParseTaskWithFormRef() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node taskConfig = ProcessConfigHelper.findTaskByName(process, "Request Absence");

        assertNotNull(taskConfig);
        assertEquals("Request Absence", taskConfig.name);
        assertNotNull(taskConfig.formRef);
        assertEquals("${file:forms/request_absence_form.json}", taskConfig.formRef);
        assertNotNull(taskConfig.vendorSpecificAttributes);
    }

    @Test
    void shouldParseTaskWithSendEmailRef() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node taskConfig = ProcessConfigHelper.findTaskByName(process, "Remind Line Manager");

        assertNotNull(taskConfig);
        assertEquals("Remind Line Manager", taskConfig.name);
        assertNotNull(taskConfig.emailJsonRef);
        assertEquals("${file:emails/remind_line_manager_email.json}", taskConfig.emailJsonRef);
    }

    @Test
    void shouldParseTaskWithRestCallRef() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node taskConfig = ProcessConfigHelper.findTaskByName(process, "Notify Employee");

        assertNotNull(taskConfig);
        assertEquals("Notify Employee", taskConfig.name);
        assertNotNull(taskConfig.restCallRef);
        assertEquals("${file:rests/notify-employee.json}", taskConfig.restCallRef);
    }

    @Test
    void shouldParseTaskWithVendorSpecificAttributes() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node taskConfig = ProcessConfigHelper.findTaskByName(process, "Log Absence in System");

        assertNotNull(taskConfig);
        assertEquals("Log Absence in System", taskConfig.name);
        assertNotNull(taskConfig.vendorSpecificAttributes);
        assertTrue(taskConfig.vendorSpecificAttributes.containsKey("camunda:class"));
        assertEquals("org.camunda.bpm.getstarted.loanapproval.process.delegates.SomeServiceTaskDelegate",
                taskConfig.vendorSpecificAttributes.get("camunda:class"));
    }

    @Test
    void shouldFindTaskByName() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node taskConfig = ProcessConfigHelper.findTaskByName(process, "Approve Absence");

        assertNotNull(taskConfig);
        assertEquals("Approve Absence", taskConfig.name);
        assertEquals("${file:forms/approve_absence_form.json}", taskConfig.formRef);
    }

    @Test
    void shouldFindEventByName() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node eventConfig = ProcessConfigHelper.findEventByName(process, "employee_needs_time_off");

        assertNotNull(eventConfig);
        assertEquals("employee_needs_time_off", eventConfig.name);
        assertNotNull(eventConfig.vendorSpecificAttributes);
    }

    @Test
    void shouldFindFlowNodeByName() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");

        // Should find task
        Node taskConfig = ProcessConfigHelper.findFlowNodeByName(process, "Request Absence");
        assertNotNull(taskConfig);
        assertEquals("Request Absence", taskConfig.name);
        assertNotNull(taskConfig.formRef);

        // Should find event
        Node eventConfig = ProcessConfigHelper.findFlowNodeByName(process, "timer1");
        assertNotNull(eventConfig);
        assertEquals("timer1", eventConfig.name);
    }

    @Test
    void shouldReturnNullForNonExistentFlowNode() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        Node config = ProcessConfigHelper.findFlowNodeByName(process, "NonExistent Task");

        assertNull(config);
    }

    @Test
    void shouldParseAllLanes() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        Process process = ProcessConfigHelper.findProcessById(configFile, "absenceRequest");
        assertNotNull(process.config.lanes);
        assertEquals(2, process.config.lanes.size());

        // Verify lane details
        assertTrue(process.config.lanes.stream().anyMatch(lane -> "Employee".equals(lane.name)));
        assertTrue(process.config.lanes.stream().anyMatch(lane -> "Line Manager".equals(lane.name)));
    }

    @Test
    void shouldValidateTaskFormReferencesSuccessfully() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        // Create a list of available forms that includes the referenced forms
        java.util.List<String> availableForms = java.util.List.of(
                REQUEST_ABSENCE_FORM,
                APPROVE_ABSENCE_FORM,
                PROVIDE_FURTHER_EXPLANATION_FORM
        );

        // Should not throw exception when all forms are available
        assertDoesNotThrow(() -> ProcessConfigHelper.validateTaskFormReferences(configFile, availableForms));
    }

    @Test
    void shouldThrowExceptionWhenFormReferenceNotFound() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        // Create a list with missing forms
        java.util.List<String> availableForms = java.util.List.of(
                SOME_OTHER_FORM
        );

        // Should throw exception when form is not found
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateTaskFormReferences(configFile, availableForms)
        );

        assertTrue(exception.getMessage().contains("references form"));
        assertTrue(exception.getMessage().contains("but no such form exists"));
    }

    @Test
    void shouldHandleEmptyFormsList() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        java.util.List<String> emptyForms = java.util.List.of();

        // Should throw exception when forms list is empty
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateTaskFormReferences(configFile, emptyForms)
        );

        assertTrue(exception.getMessage().contains("Available forms list is empty"));
    }

    @Test
    void shouldSkipTasksWithoutFormReferences() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);

        // Even with a minimal forms list, tasks without formRef should be skipped
        // (Log Absence in System doesn't have formRef)
        java.util.List<String> availableForms = java.util.List.of(
                REQUEST_ABSENCE_FORM,
                APPROVE_ABSENCE_FORM,
                PROVIDE_FURTHER_EXPLANATION_FORM
        );

        // Should not throw exception - tasks without formRef are skipped
        assertDoesNotThrow(() -> ProcessConfigHelper.validateTaskFormReferences(configFile, availableForms));
    }

    @Test
    void shouldHandleInvalidFormRefFormat() {
        // Create a config with invalid formRef format
        ConfigFile configFile = new ConfigFile();
        configFile.processes = new java.util.ArrayList<>();

        Process process = new Process();
        process.id = "testProcess";
        process.config = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Config();
        process.config.tasks = new java.util.ArrayList<>();

        Node task = new Node();
        task.name = "Invalid Task";
        task.formRef = "invalid-format-no-prefix";
        process.config.tasks.add(task);

        configFile.processes.add(process);

        java.util.List<String> availableForms = java.util.List.of(SOME_OTHER_FORM);

        // Should throw exception about invalid format
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateTaskFormReferences(configFile, availableForms)
        );

        assertTrue(exception.getMessage().contains("invalid formRef format"));
        assertTrue(exception.getMessage().contains("Expected format"));
    }

    @Test
    void shouldValidateTasksWithFormsHaveDataOutputsSuccessfully() throws IOException {
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);
        BpmnData bpmnDefinitions = BpmnHelper.parseBpmnFile(DIAGRAM_1_BPMN);

        // Should not throw exception and should return list of form-dataobject pairs
        List<FormDataObjectPair> formDataObjectPairs = ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions);

        assertNotNull(formDataObjectPairs);
        assertFalse(formDataObjectPairs.isEmpty());

        // Verify that each pair has both formRef and dataObjectRefId
        for (FormDataObjectPair pair : formDataObjectPairs) {
            assertNotNull(pair.formRef());
            assertNotNull(pair.dataObjectRefId());
            assertFalse(pair.formRef().trim().isEmpty());
            assertFalse(pair.dataObjectRefId().trim().isEmpty());
        }

        // Verify that the returned list contains the expected pairs
        System.out.println("Form-DataObject pairs: " + formDataObjectPairs);
    }

    @Test
    void shouldThrowExceptionWhenTaskWithFormRefHasNoDataOutputAndReturnEmptyList() {
        // Create config with a task that has formRef
        ConfigFile configFile = new ConfigFile();
        configFile.processes = new ArrayList<>();

        Process configProcess = new Process();
        configProcess.id = "testProcess";
        configProcess.config = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Config();
        configProcess.config.tasks = new ArrayList<>();

        Node task = new Node();
        task.name = "Test Task";
        task.formRef = "${file:forms/test-form.json}";
        configProcess.config.tasks.add(task);

        configFile.processes.add(configProcess);

        // Create BPMN definitions with matching task but NO data outputs
        List<ProcessDef> processes = new ArrayList<>();
        Map<String, FlowNode> nodesById = new HashMap<>();

        FlowNode flowNode = new FlowNode(
                "task1",
                "userTask",
                "Test Task",
                new HashMap<>(),
                new ArrayList<>(),  // no data inputs
                new ArrayList<>(),  // no data outputs - this should cause validation to fail
                null, null,
                null,
                null,
                null,
                null, null, null, null);
        nodesById.put("task1", flowNode);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                nodesById,
                new HashMap<>(),
                new HashMap<>()
        );
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions)
        );

        assertTrue(exception.getMessage().contains("does not have any data output associations"));
        assertTrue(exception.getMessage().contains("Test Task"));
    }

    @Test
    void shouldThrowExceptionWhenTaskNotFoundInBpmn() {
        // Create config with a task
        ConfigFile configFile = new ConfigFile();
        configFile.processes = new ArrayList<>();

        Process configProcess = new Process();
        configProcess.id = "testProcess";
        configProcess.config = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Config();
        configProcess.config.tasks = new ArrayList<>();

        Node task = new Node();
        task.name = "Non-Existent Task";
        task.formRef = "${file:forms/test-form.json}";
        configProcess.config.tasks.add(task);

        configFile.processes.add(configProcess);

        // Create BPMN definitions WITHOUT the task
        List<ProcessDef> processes = new ArrayList<>();
        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),  // empty nodes
                new HashMap<>(),
                new HashMap<>()
        );
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions)
        );

        assertTrue(exception.getMessage().contains("does not exist in the BPMN diagram"));
        assertTrue(exception.getMessage().contains("Non-Existent Task"));
    }

    @Test
    void shouldThrowExceptionWhenTaskWithFormRefIsNotUserTask() {
        // Create config with a task that has formRef
        ConfigFile configFile = new ConfigFile();
        configFile.processes = new ArrayList<>();

        Process configProcess = new Process();
        configProcess.id = "testProcess";
        configProcess.config = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Config();
        configProcess.config.tasks = new ArrayList<>();

        Node task = new Node();
        task.name = "Service Task";
        task.formRef = "${file:forms/test-form.json}";
        configProcess.config.tasks.add(task);

        configFile.processes.add(configProcess);

        // Create BPMN definitions with matching task but type is serviceTask
        List<ProcessDef> processes = new ArrayList<>();
        Map<String, FlowNode> nodesById = new HashMap<>();

        FlowNode flowNode = new FlowNode(
                "task1",
                "serviceTask",  // Not a userTask!
                "Service Task"
        );
        nodesById.put("task1", flowNode);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                nodesById,
                new HashMap<>(),new HashMap<>()
        );
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions)
        );

        assertTrue(exception.getMessage().contains("is not a userTask"));
        assertTrue(exception.getMessage().contains("serviceTask"));
    }

    @Test
    void shouldSkipTasksWithoutFormRefAndReturnOnlyFormTaskOutputs() {
        // Create config with tasks, some with formRef and some without
        ConfigFile configFile = new ConfigFile();
        configFile.processes = new ArrayList<>();

        Process configProcess = new Process();
        configProcess.id = "testProcess";
        configProcess.config = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Config();
        configProcess.config.tasks = new ArrayList<>();

        // Task without formRef
        Node taskWithoutForm = new Node();
        taskWithoutForm.name = "Task Without Form";
        taskWithoutForm.formRef = null;
        configProcess.config.tasks.add(taskWithoutForm);

        // Task with formRef and data output
        Node taskWithForm = new Node();
        taskWithForm.name = "Task With Form";
        taskWithForm.formRef = "${file:forms/test-form.json}";
        configProcess.config.tasks.add(taskWithForm);

        configFile.processes.add(configProcess);

        // Create BPMN definitions
        List<ProcessDef> processes = new ArrayList<>();
        Map<String, FlowNode> nodesById = new HashMap<>();

        // Add task without form - has no data outputs but should be skipped
        FlowNode flowNode1 = new FlowNode(
                "task1",
                "serviceTask",
                "Task Without Form",
                new HashMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null, null,
                null,
                null,
                null,
                null,null, null, null);
        nodesById.put("task1", flowNode1);

        // Add task with form - has data outputs
        List<DataOutputAssociation> outputs = new ArrayList<>();
        outputs.add(new DataOutputAssociation("out1", "dataObj1"));

        FlowNode flowNode2 = new FlowNode(
                "task2",
                "userTask",
                "Task With Form",
                new HashMap<>(),
                new ArrayList<>(),
                outputs,
                null, null,
                null,
                null,
                null,
                null,null, null, null);
        nodesById.put("task2", flowNode2);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                nodesById,
                new HashMap<>(),new HashMap<>()
        );
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Should not throw exception - tasks without formRef are skipped
        List<FormDataObjectPair> formDataObjectPairs = ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions);

        assertNotNull(formDataObjectPairs);
        assertEquals(1, formDataObjectPairs.size());
        assertEquals("${file:forms/test-form.json}", formDataObjectPairs.get(0).formRef());
        assertEquals("dataObj1", formDataObjectPairs.get(0).dataObjectRefId());
    }

    @Test
    void shouldValidateDataObjectTypesExistInPumlSuccessfully() throws IOException {
        BpmnData bpmnDefinitions = BpmnHelper.parseBpmnFile(DIAGRAM_1_BPMN);
        BusinessData pumlDiagram = BusinessDataHelper.loadFromFile(BUSINESS_OBJECTS_PUML);

        // Should not throw exception when all data object types exist as PUML classes
        assertDoesNotThrow(() -> ProcessConfigHelper.validateDataObjectTypesExistInPuml(bpmnDefinitions, pumlDiagram));
    }

    @Test
    void shouldThrowExceptionWhenDataObjectTypeNotFoundInPuml() {
        // Create BPMN definitions with a data object that has a non-existent type
        Map<String, DataObjectReference> dataObjects = new HashMap<>();
        DataObjectReference dataObjRef = DataObjectReference.parse(
                "DataObjectReference_1",
                "req1: NonExistentClass\n[submitted]",
                "DataObject_1"
        );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create PUML diagram with different class
        Map<String, PumlClass> classes = new HashMap<>();
        classes.put("SomeOtherClass", new PumlClass("SomeOtherClass", new ArrayList<>()));

        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateDataObjectTypesExistInPuml(bpmnDefinitions, pumlDiagram)
        );

        assertTrue(exception.getMessage().contains("has type 'NonExistentClass'"));
        assertTrue(exception.getMessage().contains("but no class with name 'NonExistentClass' exists"));
    }

    @Test
    void shouldSkipDataObjectsWithoutTypeName() {
        // Create BPMN definitions with a data object that has no type name
        Map<String, DataObjectReference> dataObjects = new HashMap<>();
        DataObjectReference dataObjRef = new DataObjectReference(
                "DataObjectReference_1",
                "just_a_name",  // No proper format
                "DataObject_1",
                null,  // No variable name
                null,  // No type name
                null   // No state
        );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create empty PUML diagram
        BusinessData pumlDiagram = new BusinessData(new HashMap<>(), new HashMap<>(), new ArrayList<>());

        // Should not throw exception - data objects without type names are skipped
        assertDoesNotThrow(() -> ProcessConfigHelper.validateDataObjectTypesExistInPuml(bpmnDefinitions, pumlDiagram));
    }

    @Test
    void shouldValidateMultipleDataObjectsWithDifferentTypes() {
        // Create BPMN definitions with multiple data objects
        Map<String, DataObjectReference> dataObjects = new HashMap<>();

        DataObjectReference dataObj1 = DataObjectReference.parse(
                "DataObjectReference_1",
                "req1: AbsenceRequest\n[submitted]",
                "DataObject_1"
        );
        dataObjects.put("DataObjectReference_1", dataObj1);

        DataObjectReference dataObj2 = DataObjectReference.parse(
                "DataObjectReference_2",
                "mgr1: LineManager\n[]",
                "DataObject_2"
        );
        dataObjects.put("DataObjectReference_2", dataObj2);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects, new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create PUML diagram with matching classes
        Map<String, PumlClass> classes = new HashMap<>();
        classes.put("AbsenceRequest", new PumlClass("AbsenceRequest", new ArrayList<>()));
        classes.put("LineManager", new PumlClass("LineManager", new ArrayList<>()));

        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Should not throw exception
        assertDoesNotThrow(() -> ProcessConfigHelper.validateDataObjectTypesExistInPuml(bpmnDefinitions, pumlDiagram));
    }

    @Test
    void shouldValidateDataObjectStatesExistSuccessfully() throws Exception {
        BpmnData bpmnDefinitions = BpmnHelper.parseBpmnFile(DIAGRAM_1_BPMN);
        BusinessDataStates businessObjectStatesData = StatesHelper.loadDataFromFile(STATES_JSON);

        // Should not throw exception when all data object states exist
        assertDoesNotThrow(() -> ProcessConfigHelper.validateDataObjectStatesExist(bpmnDefinitions, businessObjectStatesData));
    }

    @Test
    void shouldThrowExceptionWhenStateNotFoundForClass() {
        // Create BPMN definitions with a data object that has a non-existent state
        Map<String, DataObjectReference> dataObjects = new HashMap<>();
        DataObjectReference dataObjRef = DataObjectReference.parse(
                "DataObjectReference_1",
                "req1: AbsenceRequest\n[nonExistentState]",
                "DataObject_1"
        );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create business object state with the class but different states
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "AbsenceRequest";
        classType.states = new ArrayList<>();

        State state1 = new State();
        state1.name = "submitted";
        state1.requiredFields = new ArrayList<>();
        classType.states.add(state1);

        businessObjectStatesData.classes.add(classType);

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateDataObjectStatesExist(bpmnDefinitions, businessObjectStatesData)
        );

        assertTrue(exception.getMessage().contains("has type 'AbsenceRequest' with state 'nonExistentState'"));
        assertTrue(exception.getMessage().contains("but state 'nonExistentState' is not defined"));
        assertTrue(exception.getMessage().contains("Available states"));
    }

    @Test
    void shouldThrowExceptionWhenClassNotFoundInBusinessObjectStates() {
        // Create BPMN definitions with a data object that has a state
        Map<String, DataObjectReference> dataObjects = new HashMap<>();
        DataObjectReference dataObjRef = DataObjectReference.parse(
                "DataObjectReference_1",
                "req1: NonExistentClass\n[someState]",
                "DataObject_1"
        );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create business object state with different class
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "SomeOtherClass";
        classType.states = new ArrayList<>();
        businessObjectStatesData.classes.add(classType);

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateDataObjectStatesExist(bpmnDefinitions, businessObjectStatesData)
        );

        assertTrue(exception.getMessage().contains("but no state configuration exists for class 'NonExistentClass'"));
    }

    @Test
    void shouldSkipDataObjectsWithoutStateName() {
        // Create BPMN definitions with a data object without state
        Map<String, DataObjectReference> dataObjects = new HashMap<>();
        DataObjectReference dataObjRef = DataObjectReference.parse(
                "DataObjectReference_1",
                "req1: AbsenceRequest\n[]",  // Empty state
                "DataObject_1"
        );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create empty business object state
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        // Should not throw exception - data objects without states are skipped
        assertDoesNotThrow(() -> ProcessConfigHelper.validateDataObjectStatesExist(bpmnDefinitions, businessObjectStatesData));
    }

    @Test
    void shouldValidateMultipleDataObjectsWithDifferentStates() {
        // Create BPMN definitions with multiple data objects
        Map<String, DataObjectReference> dataObjects = new HashMap<>();

        DataObjectReference dataObj1 = DataObjectReference.parse(
                "DataObjectReference_1",
                "req1: AbsenceRequest\n[submitted]",
                "DataObject_1"
        );
        dataObjects.put("DataObjectReference_1", dataObj1);

        DataObjectReference dataObj2 = DataObjectReference.parse(
                "DataObjectReference_2",
                "req2: AbsenceRequest\n[reviewed]",
                "DataObject_2"
        );
        dataObjects.put("DataObjectReference_2", dataObj2);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create business object state with matching states
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "AbsenceRequest";
        classType.states = new ArrayList<>();

        State state1 = new State();
        state1.name = "submitted";
        state1.requiredFields = new ArrayList<>();
        classType.states.add(state1);

        State state2 = new State();
        state2.name = "reviewed";
        state2.requiredFields = new ArrayList<>();
        classType.states.add(state2);

        businessObjectStatesData.classes.add(classType);

        // Should not throw exception
        assertDoesNotThrow(() -> ProcessConfigHelper.validateDataObjectStatesExist(bpmnDefinitions, businessObjectStatesData));
    }

    @Test
    void shouldValidateStateFieldsExistInPumlClassesSuccessfully() throws Exception {
        BusinessDataStates businessObjectStatesData = StatesHelper.loadDataFromFile(STATES_JSON);
        BusinessData pumlDiagram = BusinessDataHelper.loadFromFile(BUSINESS_OBJECTS_PUML);

        // Should not throw exception when all required fields exist in PUML classes
        assertDoesNotThrow(() -> ProcessConfigHelper.validateStateFieldsExistInPumlClasses(businessObjectStatesData, pumlDiagram));
    }

    @Test
    void shouldThrowExceptionWhenRequiredFieldNotFoundInPumlClass() {
        // Create business object state with required field
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "AbsenceRequest";
        classType.states = new ArrayList<>();

        State state = new State();
        state.name = "submitted";
        state.requiredFields = new ArrayList<>();
        state.requiredFields.add("requestId");
        state.requiredFields.add("nonExistentField");  // This field doesn't exist
        classType.states.add(state);

        businessObjectStatesData.classes.add(classType);

        // Create PUML diagram with class but missing field
        Map<String, PumlClass> classes = new HashMap<>();
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> fields = new ArrayList<>();
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("requestId", "int"));
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("employeeId", "int"));

        classes.put("AbsenceRequest", new PumlClass("AbsenceRequest", fields));
        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateStateFieldsExistInPumlClasses(businessObjectStatesData, pumlDiagram)
        );

        assertTrue(exception.getMessage().contains("State 'submitted' of class 'AbsenceRequest' requires field 'nonExistentField'"));
        assertTrue(exception.getMessage().contains("but field 'nonExistentField' does not exist"));
        assertTrue(exception.getMessage().contains("Available fields"));
    }

    @Test
    void shouldThrowExceptionWhenClassNotFoundInPumlDiagram() {
        // Create business object state
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "NonExistentClass";
        classType.states = new ArrayList<>();

        State state = new State();
        state.name = "someState";
        state.requiredFields = new ArrayList<>();
        state.requiredFields.add("someField");
        classType.states.add(state);

        businessObjectStatesData.classes.add(classType);

        // Create PUML diagram with different class
        Map<String, PumlClass> classes = new HashMap<>();
        classes.put("SomeOtherClass", new PumlClass("SomeOtherClass", new ArrayList<>()));
        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateStateFieldsExistInPumlClasses(businessObjectStatesData, pumlDiagram)
        );

        assertTrue(exception.getMessage().contains("Class 'NonExistentClass' is defined in business object states"));
        assertTrue(exception.getMessage().contains("but does not exist in PUML diagram"));
    }

    @Test
    void shouldSkipStatesWithNoRequiredFields() {
        // Create business object state with state that has no required fields
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "LineManager";
        classType.states = new ArrayList<>();

        State state = new State();
        state.name = "_default";
        state.requiredFields = new ArrayList<>();  // Empty required fields
        classType.states.add(state);

        businessObjectStatesData.classes.add(classType);

        // Create PUML diagram with class (even with no fields)
        Map<String, PumlClass> classes = new HashMap<>();
        classes.put("LineManager", new PumlClass("LineManager", new ArrayList<>()));
        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Should not throw exception - states with no required fields are skipped
        assertDoesNotThrow(() -> ProcessConfigHelper.validateStateFieldsExistInPumlClasses(businessObjectStatesData, pumlDiagram));
    }

    @Test
    void shouldValidateMultipleStatesWithDifferentFields() {
        // Create business object state with multiple states
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "AbsenceRequest";
        classType.states = new ArrayList<>();

        State state1 = new State();
        state1.name = "submitted";
        state1.requiredFields = new ArrayList<>();
        state1.requiredFields.add("requestId");
        state1.requiredFields.add("employeeId");
        classType.states.add(state1);

        State state2 = new State();
        state2.name = "reviewed";
        state2.requiredFields = new ArrayList<>();
        state2.requiredFields.add("requestId");
        state2.requiredFields.add("employeeId");
        state2.requiredFields.add("status");
        state2.requiredFields.add("managerId");
        classType.states.add(state2);

        businessObjectStatesData.classes.add(classType);

        // Create PUML diagram with all required fields
        Map<String, PumlClass> classes = new HashMap<>();
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> fields = new ArrayList<>();
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("requestId", "int"));
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("employeeId", "int"));
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("status", "Status"));
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("managerId", "int"));

        classes.put("AbsenceRequest", new PumlClass("AbsenceRequest", fields));
        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Should not throw exception
        assertDoesNotThrow(() -> ProcessConfigHelper.validateStateFieldsExistInPumlClasses(businessObjectStatesData, pumlDiagram));
    }

    @Test
    void shouldValidateFormsAlignWithPumlClassesSuccessfully() throws Exception {
        // Load actual data
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);
        BpmnData bpmnDefinitions = BpmnHelper.parseBpmnFile(DIAGRAM_1_BPMN);
        BusinessData pumlDiagram = BusinessDataHelper.loadFromFile(BUSINESS_OBJECTS_PUML);

        // Get form-dataobject pairs
        List<FormDataObjectPair> formDataObjectPairs = ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions);

        // Should not throw exception when all form properties exist in PUML classes
        assertDoesNotThrow(() -> ProcessConfigHelper.validateFormsAlignWithPumlClasses(
                formDataObjectPairs, bpmnDefinitions, pumlDiagram, "src/main/resources/"));
    }

    @Test
    void shouldThrowExceptionWhenFormPropertyNotInPumlClass() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:test_form.json}",  // Direct path, not under forms/
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: TestClass\n[submitted]",
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create PUML diagram with class but missing field
        Map<String, PumlClass> classes = new HashMap<>();
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> fields = new ArrayList<>();
        fields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("existingField", "string"));
        classes.put("TestClass", new PumlClass("TestClass", fields));
        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Create a test form file with property not in PUML class
        String testFormPath = "src/test/resources/test_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "TestClass",
                  "type": "object",
                  "properties": {
                    "existingField": { "type": "string" },
                    "nonExistentField": { "type": "string" }
                  }
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should throw exception
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> ProcessConfigHelper.validateFormsAlignWithPumlClasses(
                            pairs, bpmnDefinitions, pumlDiagram, "src/test/resources/")
            );

            assertTrue(exception.getMessage().contains("nonExistentField"));
            assertTrue(exception.getMessage().contains("TestClass"));
            assertTrue(exception.getMessage().contains("do not exist in the PUML class"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }

    @Test
    void shouldThrowExceptionWhenDataObjectNotFoundForForm() {
        // Create a form-dataobject pair with non-existent data object
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:forms/test_form.json}",
                "NonExistentDataObjectRef"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions without the data object
        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                new HashMap<>(),  // Empty data objects,
                new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        BusinessData pumlDiagram = new BusinessData(new HashMap<>(), new HashMap<>(), new ArrayList<>());

        // Should throw exception
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ProcessConfigHelper.validateFormsAlignWithPumlClasses(
                        pairs, bpmnDefinitions, pumlDiagram, "src/test/resources/")
        );

        assertTrue(exception.getMessage().contains("NonExistentDataObjectRef"));
        assertTrue(exception.getMessage().contains("not found in BPMN definitions"));
    }

    @Test
    void shouldSkipValidationWhenFormHasNoProperties() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:empty_form.json}",  // Direct path
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: TestClass\n[]",
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create PUML diagram
        Map<String, PumlClass> classes = new HashMap<>();
        classes.put("TestClass", new PumlClass("TestClass", new ArrayList<>()));
        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Create a test form file without properties
        String testFormPath = "src/test/resources/empty_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "TestClass",
                  "type": "object"
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should not throw exception - forms without properties are skipped
            assertDoesNotThrow(() -> ProcessConfigHelper.validateFormsAlignWithPumlClasses(
                    pairs, bpmnDefinitions, pumlDiagram, "src/test/resources/"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }

    @Test
    void shouldValidateNestedObjectsInForms() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:nested_form.json}",
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: ParentClass\n[]",
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create PUML diagram with parent and nested classes
        Map<String, PumlClass> classes = new HashMap<>();

        // Parent class with nested object field
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> parentFields = new ArrayList<>();
        parentFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("id", "int"));
        parentFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("nestedObject", "NestedClass"));
        classes.put("ParentClass", new PumlClass("ParentClass", parentFields));

        // Nested class
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> nestedFields = new ArrayList<>();
        nestedFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("name", "string"));
        nestedFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("value", "int"));
        classes.put("NestedClass", new PumlClass("NestedClass", nestedFields));

        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Create a test form with nested object
        String testFormPath = "src/test/resources/nested_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "ParentClass",
                  "type": "object",
                  "properties": {
                    "id": { "type": "integer" },
                    "nestedObject": {
                      "$ref": "#/$defs/NestedClass"
                    }
                  },
                  "$defs": {
                    "NestedClass": {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string" },
                        "value": { "type": "integer" }
                      }
                    }
                  }
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should not throw exception - nested objects are validated
            assertDoesNotThrow(() -> ProcessConfigHelper.validateFormsAlignWithPumlClasses(
                    pairs, bpmnDefinitions, pumlDiagram, "src/test/resources/"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }

    @Test
    void shouldThrowExceptionWhenNestedObjectMissingField() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:nested_invalid_form.json}",
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: ParentClass\n[]",
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create PUML diagram with parent and nested classes
        Map<String, PumlClass> classes = new HashMap<>();

        // Parent class with nested object field
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> parentFields = new ArrayList<>();
        parentFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("id", "int"));
        parentFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("nestedObject", "NestedClass"));
        classes.put("ParentClass", new PumlClass("ParentClass", parentFields));

        // Nested class - missing 'invalidField'
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField> nestedFields = new ArrayList<>();
        nestedFields.add(new org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField("name", "string"));
        classes.put("NestedClass", new PumlClass("NestedClass", nestedFields));

        BusinessData pumlDiagram = new BusinessData(classes, new HashMap<>(), new ArrayList<>());

        // Create a test form with nested object containing invalid field
        String testFormPath = "src/test/resources/nested_invalid_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "ParentClass",
                  "type": "object",
                  "properties": {
                    "id": { "type": "integer" },
                    "nestedObject": {
                      "$ref": "#/$defs/NestedClass"
                    }
                  },
                  "$defs": {
                    "NestedClass": {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string" },
                        "invalidField": { "type": "integer" }
                      }
                    }
                  }
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should throw exception - nested object has invalid field
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> ProcessConfigHelper.validateFormsAlignWithPumlClasses(
                            pairs, bpmnDefinitions, pumlDiagram, "src/test/resources/")
            );

            assertTrue(exception.getMessage().contains("invalidField"));
            assertTrue(exception.getMessage().contains("NestedClass"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }

    @Test
    void shouldValidateStateRequiredFieldsInFormsSuccessfully() throws Exception {
        // Load actual data
        ConfigFile configFile = ProcessConfigHelper.loadConfigFile(CONFIG_FILE_PATH);
        BpmnData bpmnDefinitions = BpmnHelper.parseBpmnFile(DIAGRAM_1_BPMN);
        BusinessDataStates businessObjectStatesData = StatesHelper.loadDataFromFile(STATES_JSON);

        // Get form-dataobject pairs
        List<FormDataObjectPair> formDataObjectPairs = ProcessConfigHelper.validateTasksWithFormsHaveDataOutputs(configFile, bpmnDefinitions);

        // Should not throw exception when all state-required fields are marked as required in forms
        assertDoesNotThrow(() -> ProcessConfigHelper.validateStateRequiredFieldsInForms(
                formDataObjectPairs, bpmnDefinitions, businessObjectStatesData, "src/main/resources/"));
    }

    @Test
    void shouldThrowExceptionWhenStateRequiredFieldNotMarkedRequiredInForm() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:test_required_form.json}",
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object in a specific state
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: TestClass\n[submitted]",
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create business object state with required fields
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "TestClass";
        classType.states = new ArrayList<>();

        State state = new State();
        state.name = "submitted";
        state.requiredFields = new ArrayList<>();
        state.requiredFields.add("field1");
        state.requiredFields.add("field2");
        state.requiredFields.add("field3");
        classType.states.add(state);

        businessObjectStatesData.classes.add(classType);

        // Create a form that only marks some fields as required (missing field3)
        String testFormPath = "src/test/resources/test_required_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "TestClass",
                  "type": "object",
                  "properties": {
                    "field1": { "type": "string" },
                    "field2": { "type": "string" },
                    "field3": { "type": "string" }
                  },
                  "required": ["field1", "field2"]
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should throw exception - field3 is required by state but not in form
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> ProcessConfigHelper.validateStateRequiredFieldsInForms(
                            pairs, bpmnDefinitions, businessObjectStatesData, "src/test/resources/")
            );

            assertTrue(exception.getMessage().contains("field3"));
            assertTrue(exception.getMessage().contains("submitted"));
            assertTrue(exception.getMessage().contains("does not mark fields"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }

    @Test
    void shouldSkipValidationWhenDataObjectHasNoState() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:test_nostate_form.json}",
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object WITHOUT state
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: TestClass",  // No state specified
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create business object state
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "TestClass";
        classType.states = new ArrayList<>();

        State state = new State();
        state.name = "submitted";
        state.requiredFields = new ArrayList<>();
        state.requiredFields.add("field1");
        classType.states.add(state);

        businessObjectStatesData.classes.add(classType);

        // Create a form without required fields
        String testFormPath = "src/test/resources/test_nostate_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "TestClass",
                  "type": "object",
                  "properties": {
                    "field1": { "type": "string" }
                  },
                  "required": []
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should not throw exception - data object has no state, so validation is skipped
            assertDoesNotThrow(() -> ProcessConfigHelper.validateStateRequiredFieldsInForms(
                    pairs, bpmnDefinitions, businessObjectStatesData, "src/test/resources/"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }

    @Test
    void shouldSkipValidationWhenStateHasNoRequiredFields() throws Exception {
        // Create a form-dataobject pair
        FormDataObjectPair pair = new FormDataObjectPair(
                "${file:test_norequired_form.json}",
                "DataObjectReference_1"
        );
        List<FormDataObjectPair> pairs = List.of(pair);

        // Create BPMN definitions with data object in a state
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjects = new HashMap<>();
        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef =
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference.parse(
                        "DataObjectReference_1",
                        "req1: TestClass\n[_default]",
                        "DataObject_1"
                );
        dataObjects.put("DataObjectReference_1", dataObjRef);

        ProcessDef processDef = new ProcessDef(
                "testProcess",
                "Test Process",
                true,
                null,
                new HashMap<>(),
                dataObjects,new HashMap<>()
        );

        List<ProcessDef> processes = new ArrayList<>();
        processes.add(processDef);

        BpmnData bpmnDefinitions = new BpmnData(
                "def1",
                "http://test",
                null,
                processes
        );

        // Create business object state with NO required fields
        BusinessDataStates businessObjectStatesData = new BusinessDataStates();
        businessObjectStatesData.classes = new ArrayList<>();

        ClassType classType = new ClassType();
        classType.name = "TestClass";
        classType.states = new ArrayList<>();

        State state = new State();
        state.name = "_default";
        state.requiredFields = new ArrayList<>();  // Empty list
        classType.states.add(state);

        businessObjectStatesData.classes.add(classType);

        // Create a form without required fields
        String testFormPath = "src/test/resources/test_norequired_form.json";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("src/test/resources"));
        String formContent = """
                {
                  "title": "TestClass",
                  "type": "object",
                  "properties": {
                    "field1": { "type": "string" }
                  },
                  "required": []
                }
                """;
        java.nio.file.Files.writeString(java.nio.file.Paths.get(testFormPath), formContent);

        try {
            // Should not throw exception - state has no required fields
            assertDoesNotThrow(() -> ProcessConfigHelper.validateStateRequiredFieldsInForms(
                    pairs, bpmnDefinitions, businessObjectStatesData, "src/test/resources/"));
        } finally {
            // Clean up test file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testFormPath));
        }
    }
}

