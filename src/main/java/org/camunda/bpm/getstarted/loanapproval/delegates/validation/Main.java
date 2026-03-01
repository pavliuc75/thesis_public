package org.camunda.bpm.getstarted.loanapproval.delegates.validation;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BonitaProcGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.CamundaBpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BonitaBpmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.BpmnValidator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.CamundaBpmnGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.BonitaBomGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlDiagram;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.BusinessObjectState;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.BusinessObjectStateHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn.CamundaDmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn.DmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn.BonitaDmnHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.email.CamundaEmailHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.email.EmailHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.form.BonitaFormGenerator;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.form.FormHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.ArchimateData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.ArchimateHelper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    // ------ Paths to various model files
    private final String bpmnFilePath = "src/main/resources/models/loan_approval.bpmn";
    private final List<String> formPaths = List.of("models/form/loan_request_intake.json",
            "models/form/review_loan_request.json");
    private final List<String> restActionPaths = List.of();
    private final List<String[]> emailConfigsPaths = new ArrayList<>();
    private final String organizationPath = "models/organization/organization.archimate";
    private final String processConfigPath = "src/main/resources/models/config.json";
    private final String businessObjectStatesPath = "src/main/resources/models/states.json";
    private final String businessObjectsPath = "src/main/resources/models/business_objects.puml";
    private final List<String> dmnPaths = List.of("models/dmn/loan_approval_dmn.dmn");
    String procFilePath = "src/main/resources/models/loan_approval-1.0.proc";

    // ------- In-memory data structures to hold loaded model data
    private final ArchimateData archimateData = new ArchimateData();
    private ConfigFile configFileData = new ConfigFile();
    private BusinessObjectState businessObjectStatesData = new BusinessObjectState();
    private PumlDiagram businessObjectData;
    private BpmnDefinitions bpmnDefinitions;

    private Map<String, String> processedEmailFiles; // Map of file names to processed JSON content
    private Map<String, String> processedRestFiles; // Map of file names to processed JSON content
    private Map<String, String> processedDmnFiles; // Map of file names to processed DMN content

    public void run() throws Exception {
        validateModels();
        loadDataFromModelsIntoMemory();
        crossValidateDataFromModels();
        resolvePreRuntimeExpressions();
        prepareArtifacts();

//        camundaGenerateArtifacts();
         bonitaGenerateArtifacts();

        System.out.println();
    }

    private void validateModels() {
        BpmnValidator.validate(new File(bpmnFilePath));
        formPaths.forEach(path -> {
            try {
                FormHelper.validate(path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        restActionPaths.forEach(path -> {
            try {
                RestHelper.validate(path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        emailConfigsPaths.forEach(config -> {
            try {
                EmailHelper.validate(config[0], config[1]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ArchimateHelper.validate(organizationPath);
        dmnPaths.forEach(path -> {
            DmnHelper.validate(path);
        });
    }

    private void loadDataFromModelsIntoMemory() throws Exception {
        archimateData.setArchimateRoles(ArchimateHelper.loadFromResources(organizationPath));
        configFileData = ProcessConfigHelper.loadConfigFile(processConfigPath);
        businessObjectStatesData = BusinessObjectStateHelper.loadDataFromFile(businessObjectStatesPath);
        businessObjectData = PumlHelper.loadFromFile(businessObjectsPath);
        bpmnDefinitions = BpmnHelper.parseBpmnFile(bpmnFilePath);

        System.out.println();
    }

    private void crossValidateDataFromModels() {
        // archimate <-> process config
        ProcessConfigHelper.validateLaneAssignees(configFileData, archimateData.getArchimateRoles());

        // process config user tasks <-> forms existence
        ProcessConfigHelper.validateTaskFormReferences(configFileData, formPaths);
        // process config user tasks having forms <-> data object output existence
        List<FormDataObjectPair> formDataObjectPairs = ProcessConfigHelper
                .validateTasksWithFormsHaveDataOutputs(configFileData, bpmnDefinitions);
        // data objects <-> puml classes
        ProcessConfigHelper.validateDataObjectTypesExistInPuml(bpmnDefinitions, businessObjectData);
        // data objects <-> states
        ProcessConfigHelper.validateDataObjectStatesExist(bpmnDefinitions, businessObjectStatesData);
        // states <-> fields exists in puml classes
        ProcessConfigHelper.validateStateFieldsExistInPumlClasses(businessObjectStatesData, businessObjectData);
        // forms <-> classes <-> data objects
        ProcessConfigHelper.validateFormsAlignWithPumlClasses(formDataObjectPairs, bpmnDefinitions, businessObjectData,
                "src/main/resources/");
        // forms <-> states <-> data objects (required fields)
        ProcessConfigHelper.validateStateRequiredFieldsInForms(formDataObjectPairs, bpmnDefinitions,
                businessObjectStatesData, "src/main/resources/");

        // smtpConfig should exists if emails will be sent
        ProcessConfigHelper.validateSmtpConfigExistsForEmailTasks(configFileData);

        // check for reserved keywords in business object classes
        PumlHelper.validateNoReservedKeywordsInBusiessObjectClasses(businessObjectData);

        System.out.println();
    }

    private void resolvePreRuntimeExpressions() {
        // resolve global variables in email templates (pre runtime)
        processedEmailFiles = EmailHelper.resolveGlobalVariablesInEmailConfigs(emailConfigsPaths, configFileData);
        // resolve expressions in rest actions (pre runtime)
        processedRestFiles = RestHelper.resolveGlobalVariablesInRestActions(restActionPaths, configFileData);
        // resolve global variables in bpmnDefinitions sequence flows
        bpmnDefinitions = BpmnHelper.resolveGlobalVariablesInSequenceFlows(bpmnDefinitions, configFileData);
        // resolve global variables in dmn files
        processedDmnFiles = DmnHelper.resolveGlobalVariablesInDmnFiles(dmnPaths, configFileData);
    }

    private void prepareArtifacts() {
        // todo camunda specific refactor needed
        // Update BPMN definitions with resolved Camunda assignees from config
        bpmnDefinitions = BpmnHelper.updateLanesWithAssignees(
                bpmnDefinitions,
                configFileData,
                archimateData.getArchimateRoles());

        // Propagate lane assignees to userTask nodes within those lanes
        bpmnDefinitions = BpmnHelper.updateUserTasksWithLaneAssignees(bpmnDefinitions);

        // todo camunda specific refactor needed
        // Add formRef attributes to user tasks with formRef
        bpmnDefinitions = BpmnHelper.updateUserTasksWithFormRefsAndResolvedFormOutputVariableName(bpmnDefinitions,
                configFileData);

        // todo camunda specific refactor needed
        // Add email delegate bean names for tasks with emailJsonRef
        bpmnDefinitions = BpmnHelper.updateTasksWithEmailDelegateBeanName(bpmnDefinitions, configFileData);

        // todo camunda specific refactor needed
        // Add vendor attributes from config (tasks, events, lanes) to FlowNodes
        bpmnDefinitions = BpmnHelper.updateFlowNodesWithVendorAttributes(bpmnDefinitions, configFileData);

        // todo camunda specific refactor needed
        // Add email delegate bean names for tasks with restCallRef
        bpmnDefinitions = BpmnHelper.updateTasksWithRestCallDelegateBeanName(bpmnDefinitions, configFileData);

        // Add dmnRef and dmnResultVariable to flow nodes
        bpmnDefinitions = BpmnHelper.updateFlowNodesWithDmnRefAndDmnResultVariable(bpmnDefinitions, configFileData);

        // todo implement other preparations
        System.out.println();
    }

    private void camundaGenerateArtifacts() {
        // convert dots to underscores in bpmnDefinitions sequence flows
        bpmnDefinitions = CamundaBpmnHelper.resolveSequenceFlowExpressions(bpmnDefinitions);

        // resolve businessRuleTask tasks attributes
        bpmnDefinitions = CamundaBpmnHelper.resolveBusinessRuleTasks(bpmnDefinitions);

        // convert dots to underscores in email expressions for Camunda
        processedEmailFiles = CamundaEmailHelper.resolveEmailExpressions(processedEmailFiles);

        // convert dots to underscores in REST action expressions for Camunda
        processedRestFiles = CamundaRestHelper.resolveRestActionExpressions(processedRestFiles);

        // convert dots to underscores in DMN files for Camunda
        processedDmnFiles = CamundaDmnHelper.resolveDmnExpressionsForCamunda(processedDmnFiles);

        // generate Camunda specific attributes in DMN files
        processedDmnFiles = CamundaDmnHelper.generateCamundaSpecificAttributesInDmnFiles(processedDmnFiles);

        // Copy BPMN file to output directory
        String outputBpmnPath = CamundaBpmnGenerator.copyBpmnFileToOutput(bpmnFilePath,
                "src/main/resources/out/camunda");

        // Parse the copied BPMN file
        Document bpmnDocument = CamundaBpmnGenerator.parseBpmnDocument(outputBpmnPath);

        CamundaBpmnGenerator.updateLanesWithResolvedActorInDocument(bpmnDocument, bpmnDefinitions);

        CamundaBpmnGenerator.updateUserTasksWithResolvedActorInDocument(bpmnDocument, bpmnDefinitions);

        // Update lanes with vendor attributes
        CamundaBpmnGenerator.updateLaneAttributesInDocument(bpmnDocument, bpmnDefinitions);

        // Update userTasks with formKey from resolvedFormRef
        CamundaBpmnGenerator.updateUserTasksFormKeyInDocument(bpmnDocument, bpmnDefinitions);

        // Update all flow nodes with vendor attributes (like assignee, class)
        CamundaBpmnGenerator.updateFlowNodesVendorAttributesInDocument(bpmnDocument, bpmnDefinitions);

        // Update tasks with email delegate expression
        CamundaBpmnGenerator.updateTasksWithEmailDelegateExpressionInDocument(bpmnDocument, bpmnDefinitions);

        // Update tasks with rest call delegate expression
        CamundaBpmnGenerator.updateTasksWithRestCallDelegateExpressionInDocument(bpmnDocument, bpmnDefinitions);

        // Update sequence flows expressions
        CamundaBpmnGenerator.updateSequenceFlowsExpressionsInDocument(bpmnDocument, bpmnDefinitions);

        // Update businessRuleTask tasks with dmn attributes
        CamundaBpmnGenerator.updateBusinessRuleTaskTasksWithDmnAttributesInDocument(bpmnDocument, bpmnDefinitions);

        // Update sendTask tasks
        CamundaBpmnGenerator.updateSendTasksInDocument(bpmnDocument, bpmnDefinitions);

        // Generate Camunda specific attributes
        CamundaBpmnGenerator.generateCamundaSpecificAttributes(bpmnDocument);

        // Write updated document back to file
        CamundaBpmnGenerator.writeBpmnDocument(bpmnDocument, outputBpmnPath);

        // todo also support for BEFORE/AFTER <bpmn:extensionElements>
        // <camunda:executionListener delegateExpression="${fetchManagerDataDelegate}"
        // event="start" />
        // </bpmn:extensionElements>

        // todo, rename rest of the files to camunda specific names

        // Save processed email JSON files and copy FTL templates
        EmailHelper.saveProcessedEmailFiles(processedEmailFiles, emailConfigsPaths, "src/main/resources/out/camunda");

        // Save processed REST JSON files
        RestHelper.saveProcessedRestFiles(processedRestFiles, "src/main/resources/out/camunda");

        // Generate HTML forms for all user tasks with formRef
        FormHelper.generateFormsFromConfigAndBpmn(configFileData, bpmnDefinitions, "src/main/resources/out/camunda");

        // Save processed DMN files
        DmnHelper.saveProcessedDmnFiles(processedDmnFiles, "src/main/resources/out/camunda");

        // Export the config file to the output directory
        ProcessConfigHelper.exportConfigFile(processConfigPath, "src/main/resources/out/camunda");

        // PLACEHOLDER: Export the sendEmailDelegate.java to output directory
        // todo maybe jar file
    }

    private void bonitaGenerateArtifacts() {
        // update bpmnDefinitions with resolved expressions (in Groovy for Bonita)
        bpmnDefinitions = BonitaBpmnHelper.resolveSequenceFlowExpressions(bpmnDefinitions);

        // convert REST action expressions to Groovy for Bonita
        processedRestFiles = BonitaRestHelper.resolveRestActionExpressions(processedRestFiles);

        // generate bonita organization file
        BonitaOrganizationGenerator.generateOrganizationFile(archimateData, "src/main/resources/out/bonita");

        // Generate forms
        BonitaFormGenerator.generateFormsFromConfigAndBpmn(configFileData, bpmnDefinitions,
                "src/main/resources/out/bonita", formPaths, "src/main/resources/models/_misc/assets");

        // resolve expressions in DMN for Bonita
        processedDmnFiles = BonitaDmnHelper.resolveExpressionsInDmn(processedDmnFiles);

        // bpmn to bonita bpmn file (initial)
        // todo manual for now: the user has to upload the bpmn file to bonita and then
        // download the .proc file (parser)

        // Copy Proc file to output directory
        String outputProcPath = BonitaProcGenerator.copyProcFileToOutput(procFilePath, "src/main/resources/out/bonita");

        // Parse the copied Proc file
        Document procDocument = BonitaProcGenerator.parseProcDocument(outputProcPath);

        // Add actors based on lane names found in proc document and get the map of lane
        // names to actor IDs
        Map<String, String> laneNameToActorId = BonitaProcGenerator.addActorsToDocument(procDocument);

        // Add actors to lanes in proc document
        BonitaProcGenerator.updateLanesWithActorsInDocument(procDocument, laneNameToActorId);

        // Set initiator for process
        BonitaProcGenerator.setInitiatorForProcess(procDocument);

        // add data to pool
        BonitaProcGenerator.addDataObjectsToPoolInDocument(procDocument, bpmnDefinitions);

        // update events with time
        BonitaProcGenerator.updateEventsWithTimeEventsInDocument(procDocument, bpmnDefinitions);

        // update sequence flows expressions
        BonitaProcGenerator.updateSequenceFlowsExpressionsInDocument(procDocument, bpmnDefinitions);

        // add contracts and operations to user tasks
        BonitaProcGenerator.addContractsAndOperationsToUserTasksInDocument(procDocument, bpmnDefinitions,
                businessObjectData);

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
        BonitaProcGenerator.updateFlowNodesVendorAttributesInDocument(procDocument, bpmnDefinitions);

        // update businessRuleTask tasks with dmn
        BonitaProcGenerator.updateBusinessRuleTaskTasksWithDmnReferencesInDocument(procDocument, bpmnDefinitions, processedDmnFiles);

        // update send tasks
        BonitaProcGenerator.updateSendTasksInDocument(procDocument, bpmnDefinitions);

        // Write updated document back to file
        BonitaProcGenerator.writeProcDocument(procDocument, outputProcPath);

        // Generate Bonita BOM file
        BonitaBomGenerator.generateBomFile(businessObjectData, "src/main/resources/out/bonita");

        // Generate Bonita Process Config file
        BonitaProcessConfigGenerator.generateBonitaProcessConfigFile(
                procDocument,
                bpmnDefinitions,
                "src/main/resources/out/bonita", archimateData);

        System.out.printf("");
    }

    public Main() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.run();
    }
}
