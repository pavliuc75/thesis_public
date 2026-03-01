package org.camunda.bpm.getstarted.loanapproval.delegates.validation;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BonitaProcGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.CamundaBpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BonitaBpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BpmnValidator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.CamundaBpmnGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.BonitaBomGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.BusinessData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.BusinessDataHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.BusinessDataStates;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.StatesHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn.CamundaDmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn.DmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn.BonitaDmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.email.CamundaEmailHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.email.EmailHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.form.BonitaFormGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.form.FormHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.OrganizationData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.OrganizationDataHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.BonitaOrganizationGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.BonitaProcessConfigGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.ProcessConfigHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.FormDataObjectPair;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.rest.BonitaRestHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.rest.CamundaRestHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.rest.RestHelper;
import org.w3c.dom.Document;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Main {
    // ------ Paths to various model files
    private final String bpmnFilePath = "src/main/resources/models/secondStep.bpmn";
    private final List<String> formPaths = List.of();
    private final List<String> restActionPaths = List.of();
    private final List<String[]> emailConfigsPaths = List.<String[]>of(
            new String[]{"models/email/kickoff_email.ftl", "models/email/kickoff_email.json"});
    private final String organizationPath = "models/organization/organization.archimate";
    private final String processConfigPath = "src/main/resources/models/config.json";
    private final String statesPath = null;
    private final String businessDataPath = null;
    private final List<String> dmnPaths = List.of();
    private final String procFilePath = "src/main/resources/models/secondStep-1.0.proc";

    // ------- In-memory data structures to hold loaded model data
    private OrganizationData organizationData;
    private ConfigFile configFileData;
    private BusinessDataStates statesData;
    private BusinessData businessData;
    private BpmnData bpmnData;

    // ------- misc data structures for processed content
    private Map<String, String> processedEmailFiles; // Map of file names to processed JSON content
    private Map<String, String> processedRestFiles; // Map of file names to processed JSON content
    private Map<String, String> processedDmnFiles; // Map of file names to processed DMN content
    private String outputProcPath;
    private Document procDocument;


    private String targetBPMS = "bonita"; // or "bonita"

    public void run() throws Exception {
        validateModelsSolo();
        parseModelsToInternalDataStructures();
        crossValidateModels();

        enrichInternalRepresentationsOfModels();
        switch (targetBPMS) {
            case "camunda" -> camundaGenerateArtifacts();
            case "bonita" -> bonitaGenerateArtifacts();
        }
    }

    private void validateModelsSolo() {
        BpmnValidator.validate(new File(bpmnFilePath));
        ProcessConfigHelper.validateConfigFile(processConfigPath);
        StatesHelper.validateStatesFile(statesPath);
        formPaths.forEach(path -> validateOrThrow(() -> FormHelper.validate(path)));
        OrganizationDataHelper.validate(organizationPath);
        dmnPaths.forEach(DmnHelper::validate);
        emailConfigsPaths.forEach(config -> validateOrThrow(() -> EmailHelper.validate(config[0], config[1])));
        restActionPaths.forEach(path -> validateOrThrow(() -> RestHelper.validate(path)));
    }

    private void parseModelsToInternalDataStructures() throws Exception {
        organizationData = OrganizationDataHelper.loadFromFile(organizationPath);
        configFileData = ProcessConfigHelper.loadConfigFile(processConfigPath);
        statesData = StatesHelper.loadDataFromFile(statesPath);
        businessData = BusinessDataHelper.loadFromFile(businessDataPath);
        bpmnData = BpmnHelper.parseBpmnFile(bpmnFilePath);
    }

    private void crossValidateModels() {
        // archimate <-> process config
        ProcessConfigHelper.validateLaneAssignees(configFileData, organizationData);
        // process config user tasks <-> forms existence
        ProcessConfigHelper.validateTaskFormReferences(configFileData, formPaths);
        // process config tasks <-> REST config existence
        ProcessConfigHelper.validateRestCallReferences(configFileData, restActionPaths);
        // process config tasks/events <-> DMN existence
        ProcessConfigHelper.validateDmnReferences(configFileData, dmnPaths);
        // process config user tasks having forms <-> data object output existence
        List<FormDataObjectPair> formDataObjectPairs = ProcessConfigHelper
                .validateTasksWithFormsHaveDataOutputs(configFileData, bpmnData);
        // data objects <-> puml classes
        ProcessConfigHelper.validateDataObjectTypesExistInPuml(bpmnData, businessData);
        // data objects <-> states
        ProcessConfigHelper.validateDataObjectStatesExist(bpmnData, statesData);
        // states <-> fields exists in puml classes
        BusinessDataHelper.validateStatesExistInBusinessData(statesData, businessData);
        // forms <-> classes <-> data objects
        ProcessConfigHelper.validateFormsAlignWithPumlClasses(formDataObjectPairs, bpmnData, businessData,
                "src/main/resources/");
        // forms <-> states <-> data objects (required fields)
        ProcessConfigHelper.validateStateRequiredFieldsInForms(formDataObjectPairs, bpmnData,
                statesData, "src/main/resources/");
        // smtpConfig should exists if emails will be sent
        ProcessConfigHelper.validateSmtpConfigExistsForEmailTasks(configFileData);
        // check for reserved keywords in business object classes
        BusinessDataHelper.validateNoReservedKeywordsInBusiessObjectClasses(businessData);
    }

//    private void resolvePreRuntimeExpressions() {
//        // resolve global variables in email templates (pre runtime)
//        processedEmailFiles = EmailHelper.resolveGlobalVariablesInEmailConfigs(emailConfigsPaths, configFileData);
//        // resolve expressions in rest actions (pre runtime)
//        processedRestFiles = RestHelper.resolveGlobalVariablesInRestActions(restActionPaths, configFileData);
//        // resolve global variables in bpmnDefinitions sequence flows
//        bpmnDefinitions = BpmnHelper.resolveGlobalVariablesInSequenceFlows(bpmnDefinitions, configFileData);
//        // resolve global variables in dmn files
//        processedDmnFiles = DmnHelper.resolveGlobalVariablesInDmnFiles(dmnPaths, configFileData);
//    }

    private void enrichInternalRepresentationsOfModels() {
        // resolve global variables in email templates (pre runtime)
        processedEmailFiles = EmailHelper.resolveGlobalVariablesInEmailConfigs(emailConfigsPaths, configFileData);
        // resolve expressions in rest actions (pre runtime)
        processedRestFiles = RestHelper.resolveGlobalVariablesInRestActions(restActionPaths, configFileData);
        // resolve global variables in bpmnDefinitions sequence flows
        bpmnData = BpmnHelper.resolveGlobalVariablesInSequenceFlows(bpmnData, configFileData);
        // resolve global variables in dmn files
        processedDmnFiles = DmnHelper.resolveGlobalVariablesInDmnFiles(dmnPaths, configFileData);

        // Update BPMN definitions with resolved assignees from config
        bpmnData = BpmnHelper.updateLanesWithAssignees(bpmnData, configFileData, organizationData);
        // Propagate lane assignees to userTask nodes within those lanes
        bpmnData = BpmnHelper.updateUserTasksWithLaneAssignees(bpmnData);


        // Add formRef attributes to user tasks with formRef
        bpmnData = BpmnHelper.updateUserTasksWithFormRefsAndResolvedFormOutputVariableName(bpmnData, configFileData);
        // Add email delegate bean names for tasks with emailJsonRef
        bpmnData = BpmnHelper.updateTasksWithEmailDelegateBeanName(bpmnData, configFileData);
        // Add vendor attributes from config (tasks, events, lanes) to FlowNodes
        bpmnData = BpmnHelper.updateFlowNodesWithVendorAttributes(bpmnData, configFileData);
        // Add email delegate bean names for tasks with restCallRef
        bpmnData = BpmnHelper.updateTasksWithRestCallDelegateBeanName(bpmnData, configFileData);
        // Add dmnRef and dmnResultVariable to flow nodes
        bpmnData = BpmnHelper.updateFlowNodesWithDmnRefAndDmnResultVariable(bpmnData, configFileData);
    }

    private void camundaGenerateArtifacts() {
        // convert to camunda expressions
        bpmnData = CamundaBpmnHelper.resolveSequenceFlowExpressions(bpmnData);
        // resolve businessRuleTask tasks attributes
        bpmnData = CamundaBpmnHelper.resolveBusinessRuleTasks(bpmnData);

        // convert to camunda expressions
        processedEmailFiles = CamundaEmailHelper.resolveEmailExpressions(processedEmailFiles);
        // convert to camunda expressions
        processedRestFiles = CamundaRestHelper.resolveRestActionExpressions(processedRestFiles);
        // convert to camunda expressions
        processedDmnFiles = CamundaDmnHelper.resolveDmnExpressionsForCamunda(processedDmnFiles);
        // generate Camunda specific attributes in DMN files
        processedDmnFiles = CamundaDmnHelper.generateCamundaSpecificAttributesInDmnFiles(processedDmnFiles);

        // Copy BPMN file to output directory
        String outputBpmnPath = CamundaBpmnGenerator.copyBpmnFileToOutput(bpmnFilePath, "src/main/resources/out/camunda");
        // Parse the copied BPMN file
        Document bpmnDocument = CamundaBpmnGenerator.parseBpmnDocument(outputBpmnPath);
        // Update lanes and user tasks with resolved actors from config
        CamundaBpmnGenerator.updateLanesWithResolvedActorInDocument(bpmnDocument, bpmnData);
        // Propagate lane resolved actors to userTask nodes within those lanes
        CamundaBpmnGenerator.updateUserTasksWithResolvedActorInDocument(bpmnDocument, bpmnData);
        // Update lanes with vendor attributes
        CamundaBpmnGenerator.updateLaneAttributesInDocument(bpmnDocument, bpmnData);
        // Update userTasks with formKey from resolvedFormRef
        CamundaBpmnGenerator.updateUserTasksFormKeyInDocument(bpmnDocument, bpmnData);
        // Update all flow nodes with vendor attributes (like assignee, class)
        CamundaBpmnGenerator.updateFlowNodesVendorAttributesInDocument(bpmnDocument, bpmnData);
        // Update tasks with email delegate expression
        CamundaBpmnGenerator.updateTasksWithEmailDelegateExpressionInDocument(bpmnDocument, bpmnData);
        // Update tasks with rest call delegate expression
        CamundaBpmnGenerator.updateTasksWithRestCallDelegateExpressionInDocument(bpmnDocument, bpmnData);
        // Update sequence flows expressions
        CamundaBpmnGenerator.updateSequenceFlowsExpressionsInDocument(bpmnDocument, bpmnData);
        // Update businessRuleTask tasks with dmn attributes
        CamundaBpmnGenerator.updateBusinessRuleTaskTasksWithDmnAttributesInDocument(bpmnDocument, bpmnData);
        // Update sendTask tasks
        CamundaBpmnGenerator.updateSendTasksInDocument(bpmnDocument, bpmnData);
        // Generate Camunda specific attributes
        CamundaBpmnGenerator.generateCamundaSpecificAttributes(bpmnDocument);
        // Write updated document back to file
        CamundaBpmnGenerator.writeBpmnDocument(bpmnDocument, outputBpmnPath);


        // Save processed email JSON files and copy FTL templates
        EmailHelper.saveProcessedEmailFiles(processedEmailFiles, emailConfigsPaths, "src/main/resources/out/camunda");
        // Save processed REST JSON files
        RestHelper.saveProcessedRestFiles(processedRestFiles, "src/main/resources/out/camunda");
        // Generate HTML forms for all user tasks with formRef
        FormHelper.generateFormsFromConfigAndBpmn(configFileData, bpmnData, "src/main/resources/out/camunda");
        // Save processed DMN files
        DmnHelper.saveProcessedDmnFiles(processedDmnFiles, "src/main/resources/out/camunda");
        // Export the config file to the output directory
        ProcessConfigHelper.exportConfigFile(processConfigPath, "src/main/resources/out/camunda");

        // PLACEHOLDER: Export the sendEmailDelegate.java to output directory
        // todo maybe jar file
    }

    private void bonitaGenerateArtifacts() {
        // update bpmnDefinitions with resolved expressions (in Groovy for Bonita)
        bpmnData = BonitaBpmnHelper.resolveSequenceFlowExpressions(bpmnData);
        // convert REST action expressions to Groovy for Bonita
        processedRestFiles = BonitaRestHelper.resolveRestActionExpressions(processedRestFiles);
        // generate bonita organization file
        BonitaOrganizationGenerator.generateOrganizationFile(organizationData, "src/main/resources/out/bonita");
        // resolve expressions in DMN for Bonita
        processedDmnFiles = BonitaDmnHelper.resolveExpressionsInDmn(processedDmnFiles);
        // Copy Proc file to output directory
        outputProcPath = BonitaProcGenerator.copyProcFileToOutput(procFilePath, "src/main/resources/out/bonita");
        // Parse the copied Proc file
        procDocument = BonitaProcGenerator.parseProcDocument(outputProcPath);
        // Add actors based on lane names found in proc document and get the map of lane names to actor IDs
        Map<String, String> laneNameToActorId = BonitaProcGenerator.addActorsToDocument(procDocument);
        // Add actors to lanes in proc document
        BonitaProcGenerator.updateLanesWithActorsInDocument(procDocument, laneNameToActorId);
        // Set initiator for process
        BonitaProcGenerator.setInitiatorForProcess(procDocument);
        // add data to pool
        BonitaProcGenerator.addDataObjectsToPoolInDocument(procDocument, bpmnData);
        // update events with time
        BonitaProcGenerator.updateEventsWithTimeEventsInDocument(procDocument, bpmnData);
        // update sequence flows expressions
        BonitaProcGenerator.updateSequenceFlowsExpressionsInDocument(procDocument, bpmnData);
        // add contracts and operations to user tasks
        BonitaProcGenerator.addContractsAndOperationsToUserTasksInDocument(procDocument, bpmnData, businessData);
        // Generate forms and .index.json used by updateUserTasksWithFormRefsInDocument
        BonitaFormGenerator.generateFormsFromConfigAndBpmn(configFileData, bpmnData, procDocument,
                "src/main/resources/out/bonita", formPaths, "src/main/resources/models/_misc/assets");
        // add form refs to user tasks
        BonitaProcGenerator.updateUserTasksWithFormRefsInDocument(procDocument, configFileData,
                "src/main/resources/out/bonita/.index.json");
        // update service tasks with rest connectors
        BonitaProcGenerator.updateServiceTasksWithRestConnectorsInDocument(procDocument, processedRestFiles,
                configFileData);
        // update service tasks with email connectors
        BonitaProcGenerator.updateServiceTasksWithEmailConnectorsInDocument(procDocument, processedEmailFiles,
                configFileData);
        // update flow nodes with vendor attributes
        BonitaProcGenerator.updateFlowNodesVendorAttributesInDocument(procDocument, bpmnData);
        // update businessRuleTask tasks with dmn
        BonitaProcGenerator.updateBusinessRuleTaskTasksWithDmnReferencesInDocument(procDocument, bpmnData, processedDmnFiles);
        // update send tasks
        BonitaProcGenerator.updateSendTasksInDocument(procDocument, bpmnData);
        // Write updated document back to file
        BonitaProcGenerator.writeProcDocument(procDocument, outputProcPath);
        // Generate Bonita BOM file
        BonitaBomGenerator.generateBomFile(businessData, "src/main/resources/out/bonita");
        // Generate Bonita Process Config file
        BonitaProcessConfigGenerator.generateBonitaProcessConfigFile(procDocument, bpmnData, "src/main/resources/out/bonita", organizationData);
    }

    @FunctionalInterface
    private interface ValidationRunnable {
        void run() throws Exception;
    }

    private static void validateOrThrow(ValidationRunnable validation) {
        try {
            validation.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.run();
    }
}
