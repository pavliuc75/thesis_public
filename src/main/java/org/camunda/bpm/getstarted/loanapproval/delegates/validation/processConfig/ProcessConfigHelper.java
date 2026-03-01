package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlDiagram;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.BusinessObjectState;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.ClassType;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.Role;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.FormDataObjectPair;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Lane;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessConfigHelper {
    public void checkAllLanesPresent(List<String> requiredLanes, List<Lane> lanes) {
        if (lanes == null) {
            throw new IllegalStateException("Lanes have not been loaded. Please load lanes from config first.");
        }

        for (String requiredLane : requiredLanes) {
            boolean found = lanes.stream().anyMatch(lane -> lane.name.equals(requiredLane));
            if (!found) {
                throw new IllegalStateException("Required lane '" + requiredLane + "' is missing in the process configuration.");
            }
        }
    }

    public static ConfigFile loadConfigFile(String configFilePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.readValue(new File(configFilePath), ConfigFile.class);
    }

    /**
     * Finds a process configuration by its ID.
     *
     * @param configFile the loaded config file
     * @param processId  the BPMN process ID to match
     * @return the Process config if found, null otherwise
     */
    public static Process findProcessById(ConfigFile configFile, String processId) {
        if (configFile == null || configFile.processes == null) {
            return null;
        }
        return configFile.processes.stream()
                .filter(p -> p.id != null && p.id.equals(processId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Validates that all lane assignees in the config file reference actors that exist in the archimate roles.
     * Extracts entity names from references like "${ref:organization.alice}" and verifies they exist as Actors.
     *
     * @param configFile     the loaded config file
     * @param archimateRoles the list of roles from archimate data
     * @throws IllegalStateException if any assignee reference cannot be resolved to an existing actor
     */
    public static void validateLaneAssignees(ConfigFile configFile, List<Role> archimateRoles) {
        if (configFile == null || configFile.processes == null) {
            return; // Nothing to validate
        }

        if (archimateRoles == null) {
            throw new IllegalStateException("Archimate roles have not been loaded. Cannot validate lane assignees.");
        }

        // Pattern to match references like "${ref:organization.alice}" and extract "alice"
        Pattern refPattern = Pattern.compile("\\$\\{ref:organization\\.([^}]+)\\}");

        for (Process process : configFile.processes) {
            if (process.config == null || process.config.lanes == null) {
                continue;
            }

            for (Lane lane : process.config.lanes) {
                if (lane.assignee == null || lane.assignee.trim().isEmpty()) {
                    continue; // Skip lanes without assignees
                }

                Matcher matcher = refPattern.matcher(lane.assignee);
                if (matcher.matches()) {
                    String entityName = matcher.group(1); // Extract "alice" from "${ref:organization.alice}"

                    // Check if this entity exists as an Actor in any Role
                    boolean found = archimateRoles.stream()
                            .flatMap(role -> role.getActors().stream())
                            .anyMatch(actor -> entityName.equals(actor.getName()) || entityName.equals(actor.getId()));

                    if (!found) {
                        throw new IllegalStateException(
                                String.format("Lane '%s' in process '%s' references assignee '%s' (from '${ref:organization.%s}'), " +
                                                "but no Actor with name or id '%s' exists in the archimate roles.",
                                        lane.name, process.id != null ? process.id : process.name, entityName, entityName, entityName));
                    }
                }
                // If assignee doesn't match the pattern, we skip it (might be a literal value or different format)
            }
        }
    }

    /**
     * Finds a task configuration by its name within a process.
     *
     * @param process  the process config
     * @param taskName the task name to find
     * @return the Node if found, null otherwise
     */
    public static Node findTaskByName(Process process, String taskName) {
        if (process == null || process.config == null || process.config.tasks == null) {
            return null;
        }
        return process.config.tasks.stream()
                .filter(task -> taskName.equals(task.name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds an event configuration by its name within a process.
     *
     * @param process   the process config
     * @param eventName the event name to find
     * @return the Node if found, null otherwise
     */
    public static Node findEventByName(Process process, String eventName) {
        if (process == null || process.config == null || process.config.events == null) {
            return null;
        }
        return process.config.events.stream()
                .filter(event -> eventName.equals(event.name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds a flow node configuration (task or event) by its name within a process.
     * Searches tasks first, then events.
     *
     * @param process      the process config
     * @param flowNodeName the flow node name to find
     * @return the Node if found, null otherwise
     */
    public static Node findFlowNodeByName(Process process, String flowNodeName) {
        // Try tasks first
        Node config = findTaskByName(process, flowNodeName);
        if (config != null) {
            return config;
        }
        // Try events
        return findEventByName(process, flowNodeName);
    }

    /**
     * Extracts the file path from a file reference string.
     * Expected format: "${file:forms/request-absence-v1.json}" -> "forms/request-absence-v1.json"
     *
     * @param fileRef the file reference string
     * @return the extracted path, or null if the format is invalid
     */
    private static String extractFilePathFromRef(String fileRef) {
        if (fileRef == null || !fileRef.startsWith("${file:") || !fileRef.endsWith("}")) {
            return null;
        }
        // Extract path between "${file:" and "}"
        return fileRef.substring(7, fileRef.length() - 1);
    }

    /**
     * Validates that all form references in tasks exist in the provided list of available forms.
     * Checks formRef property of each task and verifies the referenced form file is available.
     *
     * @param configFile     the loaded config file
     * @param availableForms list of available form file paths (e.g., "models/form/approve_absence_form.json")
     * @throws IllegalStateException if any form reference cannot be resolved to an existing form
     */
    public static void validateTaskFormReferences(ConfigFile configFile, List<String> availableForms) {
        if (configFile == null || configFile.processes == null) {
            return; // Nothing to validate
        }

        boolean hasFormRefs = false;
        for (Process process : configFile.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }
            for (Node task : process.config.tasks) {
                if (task.formRef != null && !task.formRef.trim().isEmpty()) {
                    hasFormRefs = true;
                    break;
                }
            }
            if (hasFormRefs) {
                break;
            }
        }

        if (!hasFormRefs && (availableForms == null || availableForms.isEmpty())) {
            return;
        }

        if (availableForms == null || availableForms.isEmpty()) {
            throw new IllegalStateException("Available forms list is empty. Cannot validate task form references.");
        }

        for (Process process : configFile.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }

            for (Node task : process.config.tasks) {
                if (task.formRef == null || task.formRef.trim().isEmpty()) {
                    continue; // Skip tasks without form references
                }

                // Extract path from formRef (e.g., "${file:forms/request-absence-v1.json}")
                String extractedPath = extractFilePathFromRef(task.formRef);
                if (extractedPath == null) {
                    throw new IllegalStateException(
                            String.format("Task '%s' in process '%s' has invalid formRef format: '%s'. " +
                                            "Expected format: ${file:path/to/form.json}",
                                    task.name, process.id != null ? process.id : process.name, task.formRef));
                }

                // Normalize path: "forms/..." -> "models/form/..." (handle singular/plural conversion)
                final String normalizedPath;
                if (extractedPath.startsWith("forms/")) {
                    normalizedPath = "models/form/" + extractedPath.substring(6);
                } else {
                    normalizedPath = extractedPath;
                }

                // Check if this form exists in available forms
                String fileName = extractedPath.substring(extractedPath.lastIndexOf('/') + 1);
                boolean found = availableForms.stream()
                        .anyMatch(formPath -> formPath.equals(normalizedPath) || formPath.endsWith("/" + fileName));

                if (!found) {
                    throw new IllegalStateException(
                            String.format("Task '%s' in process '%s' references form '%s' (normalized: '%s'), " +
                                            "but no such form exists in the available forms list: %s",
                                    task.name, process.id != null ? process.id : process.name,
                                    extractedPath, normalizedPath, availableForms));
                }
            }
        }
    }

    /**
     * Validates that all user tasks with formRef have at least one data output association in the BPMN diagram.
     * This ensures that tasks producing forms also define what data objects they output.
     * Returns a list of pairs containing form references and their associated data object reference IDs.
     *
     * @param configFile     the loaded config file
     * @param bpmnDefinitions the parsed BPMN definitions
     * @return list of FormDataObjectPair containing formRef and dataObjectRefId pairs
     * @throws IllegalStateException if any task with formRef lacks a data output association
     */
    public static List<FormDataObjectPair> validateTasksWithFormsHaveDataOutputs(ConfigFile configFile, BpmnDefinitions bpmnDefinitions) {
        List<FormDataObjectPair> formDataObjectPairs = new ArrayList<>();

        if (configFile == null || configFile.processes == null) {
            return formDataObjectPairs; // Nothing to validate
        }

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            throw new IllegalStateException("BPMN definitions are null. Cannot validate task data outputs.");
        }

        for (Process configProcess : configFile.processes) {
            if (configProcess.config == null || configProcess.config.tasks == null) {
                continue;
            }

            // Find matching BPMN process
            ProcessDef bpmnProcess = bpmnDefinitions.processes().stream()
                    .filter(p -> configProcess.id != null && configProcess.id.equals(p.id()))
                    .findFirst()
                    .orElse(null);

            if (bpmnProcess == null) {
                // Skip if no matching BPMN process found (might be validated elsewhere)
                continue;
            }

            // Check each task that has a formRef
            for (Node task : configProcess.config.tasks) {
                if (task.formRef == null || task.formRef.trim().isEmpty()) {
                    continue; // Skip tasks without form references
                }

                // Find matching flow node in BPMN by name
                FlowNode flowNode = bpmnProcess.nodesById().values().stream()
                        .filter(node -> task.name != null && task.name.equals(node.name()))
                        .findFirst()
                        .orElse(null);

                if (flowNode == null) {
                    throw new IllegalStateException(
                            String.format("Task '%s' with formRef '%s' in process '%s' is defined in config " +
                                            "but does not exist in the BPMN diagram.",
                                    task.name, task.formRef, configProcess.id));
                }

                // Check if task is a userTask
                if (!"userTask".equals(flowNode.type())) {
                    throw new IllegalStateException(
                            String.format("Task '%s' with formRef '%s' in process '%s' is not a userTask in the BPMN diagram " +
                                            "(found type: '%s'). Only userTasks can have formRef.",
                                    task.name, task.formRef, configProcess.id, flowNode.type()));
                }

                // Check if the flow node has at least one data output association
                if (flowNode.dataOutputAssociations() == null || flowNode.dataOutputAssociations().isEmpty()) {
                    throw new IllegalStateException(
                            String.format("UserTask '%s' with formRef '%s' in process '%s' does not have any data output associations " +
                                            "in the BPMN diagram. Tasks with forms must define what data objects they produce.",
                                    task.name, task.formRef, configProcess.id));
                }

                // Collect all data object references from output associations as pairs
                for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation outputAssoc : 
                        flowNode.dataOutputAssociations()) {
                    if (outputAssoc.targetRef() != null && !outputAssoc.targetRef().trim().isEmpty()) {
                        formDataObjectPairs.add(new FormDataObjectPair(task.formRef, outputAssoc.targetRef()));
                    }
                }
            }
        }

        return formDataObjectPairs;
    }

    /**
     * Validates that all data object type names in the BPMN diagram exist as classes in the PUML diagram.
     * Iterates through all data object references in all processes and checks their type names.
     *
     * @param bpmnDefinitions the parsed BPMN definitions containing data objects
     * @param pumlDiagram     the parsed PUML diagram containing class definitions
     * @throws IllegalStateException if any data object type name does not exist as a PUML class
     */
    public static void validateDataObjectTypesExistInPuml(BpmnDefinitions bpmnDefinitions, PumlDiagram pumlDiagram) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return; // Nothing to validate
        }

        if (pumlDiagram == null || pumlDiagram.classes() == null) {
            throw new IllegalStateException("PUML diagram or classes map is null. Cannot validate data object types.");
        }

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.dataObjectsById() == null || process.dataObjectsById().isEmpty()) {
                continue; // No data objects in this process
            }

            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef : 
                    process.dataObjectsById().values()) {
                
                String typeName = dataObjRef.typeName();
                
                // Skip data objects without a type name (might be poorly formatted or edge cases)
                if (typeName == null || typeName.trim().isEmpty()) {
                    continue;
                }

                // Check if this type name exists as a class in the PUML diagram
                if (!pumlDiagram.classes().containsKey(typeName)) {
                    throw new IllegalStateException(
                            String.format("Data object '%s' in process '%s' has type '%s', " +
                                            "but no class with name '%s' exists in the PUML diagram. " +
                                            "Available classes: %s",
                                    dataObjRef.id(), 
                                    process.id(), 
                                    typeName, 
                                    typeName,
                                    pumlDiagram.classes().keySet()));
                }
            }
        }
    }

    /**
     * Validates that all data objects with state names have corresponding states defined
     * in the BusinessObjectState data for their class type.
     * Iterates through all data object references and checks that their states exist.
     *
     * @param bpmnDefinitions         the parsed BPMN definitions containing data objects
     * @param businessObjectStatesData the loaded business object state data
     * @throws IllegalStateException if any data object state name does not exist for its class type
     */
    public static void validateDataObjectStatesExist(BpmnDefinitions bpmnDefinitions, 
                                                     BusinessObjectState businessObjectStatesData) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return; // Nothing to validate
        }

        if (businessObjectStatesData == null || businessObjectStatesData.classes == null) {
            throw new IllegalStateException("BusinessObjectState or classes list is null. Cannot validate data object states.");
        }

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.dataObjectsById() == null || process.dataObjectsById().isEmpty()) {
                continue; // No data objects in this process
            }

            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef : 
                    process.dataObjectsById().values()) {
                
                String typeName = dataObjRef.typeName();
                String stateName = dataObjRef.stateName();
                
                // Skip data objects without state names
                if (stateName == null || stateName.trim().isEmpty()) {
                    continue;
                }

                // Skip data objects without type names (can't validate without knowing the class)
                if (typeName == null || typeName.trim().isEmpty()) {
                    continue;
                }

                // Find the class type in business object states
                ClassType classType = businessObjectStatesData.classes.stream()
                        .filter(ct -> typeName.equals(ct.name))
                        .findFirst()
                        .orElse(null);

                if (classType == null) {
                    throw new IllegalStateException(
                            String.format("Data object '%s' in process '%s' has type '%s' with state '%s', " +
                                            "but no state configuration exists for class '%s' in the business object states. " +
                                            "Available classes: %s",
                                    dataObjRef.id(),
                                    process.id(),
                                    typeName,
                                    stateName,
                                    typeName,
                                    businessObjectStatesData.classes.stream()
                                            .map(ct -> ct.name)
                                            .toList()));
                }

                // Check if the state exists for this class
                boolean stateExists = classType.states != null && classType.states.stream()
                        .anyMatch(state -> stateName.equals(state.name));

                if (!stateExists) {
                    List<String> availableStates = classType.states != null 
                            ? classType.states.stream().map(s -> s.name).toList()
                            : new ArrayList<>();
                    
                    throw new IllegalStateException(
                            String.format("Data object '%s' in process '%s' has type '%s' with state '%s', " +
                                            "but state '%s' is not defined for class '%s'. " +
                                            "Available states for '%s': %s",
                                    dataObjRef.id(),
                                    process.id(),
                                    typeName,
                                    stateName,
                                    stateName,
                                    typeName,
                                    typeName,
                                    availableStates));
                }
            }
        }
    }

    /**
     * Validates that all required fields defined in state configurations exist as attributes
     * in the corresponding PUML class definitions.
     * Ensures consistency between state requirements and actual class structures.
     *
     * @param businessObjectStatesData the loaded business object state data with required fields
     * @param pumlDiagram              the parsed PUML diagram containing class definitions
     * @throws IllegalStateException if any required field does not exist in its PUML class
     */
    public static void validateStateFieldsExistInPumlClasses(BusinessObjectState businessObjectStatesData,
                                                             PumlDiagram pumlDiagram) {
        if (businessObjectStatesData == null || businessObjectStatesData.classes == null) {
            return; // Nothing to validate
        }

        if (pumlDiagram == null || pumlDiagram.classes() == null) {
            throw new IllegalStateException("PUML diagram or classes map is null. Cannot validate state fields.");
        }

        for (ClassType classType : businessObjectStatesData.classes) {
            String className = classType.name;
            
            // Find corresponding PUML class
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass pumlClass = 
                pumlDiagram.classes().get(className);

            if (pumlClass == null) {
                throw new IllegalStateException(
                        String.format("Class '%s' is defined in business object states but does not exist in PUML diagram. " +
                                        "Available PUML classes: %s",
                                className,
                                pumlDiagram.classes().keySet()));
            }

            // Check each state's required fields
            if (classType.states != null) {
                for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.State state : 
                        classType.states) {
                    
                    if (state.requiredFields == null || state.requiredFields.isEmpty()) {
                        continue; // No fields to validate for this state
                    }

                    // Get all field names from the PUML class
                    List<String> pumlFieldNames = pumlClass.fields() != null
                            ? pumlClass.fields().stream()
                                    .map(org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField::name)
                                    .toList()
                            : new ArrayList<>();

                    // Check each required field exists in PUML class
                    for (String requiredField : state.requiredFields) {
                        if (!pumlFieldNames.contains(requiredField)) {
                            throw new IllegalStateException(
                                    String.format("State '%s' of class '%s' requires field '%s', " +
                                                    "but field '%s' does not exist in the PUML class definition. " +
                                                    "Available fields in '%s': %s",
                                            state.name,
                                            className,
                                            requiredField,
                                            requiredField,
                                            className,
                                            pumlFieldNames));
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates that forms are aligned with their corresponding PUML classes.
     * For each form-dataobject pair, checks that:
     * 1. The data object's type corresponds to a PUML class
     * 2. All properties defined in the form exist as fields in the PUML class
     * 3. Nested objects (via $ref or inline) are recursively validated
     *
     * @param formDataObjectPairs list of form-dataobject pairs from user tasks
     * @param bpmnDefinitions     the parsed BPMN definitions containing data objects
     * @param pumlDiagram         the parsed PUML diagram containing class definitions
     * @param formsBasePath       base path to resolve form file paths (e.g., "src/main/resources/")
     * @throws IllegalStateException if any form property doesn't exist in the corresponding PUML class
     */
    public static void validateFormsAlignWithPumlClasses(
            List<FormDataObjectPair> formDataObjectPairs,
            BpmnDefinitions bpmnDefinitions,
            PumlDiagram pumlDiagram,
            String formsBasePath) {
        
        if (formDataObjectPairs == null || formDataObjectPairs.isEmpty()) {
            return; // Nothing to validate
        }

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            throw new IllegalStateException("BPMN definitions are null. Cannot validate forms.");
        }

        if (pumlDiagram == null || pumlDiagram.classes() == null) {
            throw new IllegalStateException("PUML diagram or classes map is null. Cannot validate forms.");
        }

        ObjectMapper mapper = new ObjectMapper();

        for (FormDataObjectPair pair : formDataObjectPairs) {
            String formRef = pair.formRef();
            String dataObjectRefId = pair.dataObjectRefId();

            // Extract form file path
            String formPath = extractFilePathFromRef(formRef);
            if (formPath == null) {
                throw new IllegalStateException(
                        String.format("Invalid formRef format: '%s'. Expected format: ${file:path/to/form.json}",
                                formRef));
            }

            // Normalize path to actual file location
            String fullFormPath;
            if (formPath.startsWith("forms/")) {
                fullFormPath = formsBasePath + "models/form/" + formPath.substring(6);
            } else {
                fullFormPath = formsBasePath + formPath;
            }

            // Find the data object reference in BPMN to get the type name
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef = null;
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.dataObjectsById() != null && process.dataObjectsById().containsKey(dataObjectRefId)) {
                    dataObjRef = process.dataObjectsById().get(dataObjectRefId);
                    break;
                }
            }

            if (dataObjRef == null) {
                throw new IllegalStateException(
                        String.format("Data object reference '%s' associated with form '%s' not found in BPMN definitions.",
                                dataObjectRefId, formRef));
            }

            String typeName = dataObjRef.typeName();
            if (typeName == null || typeName.trim().isEmpty()) {
                throw new IllegalStateException(
                        String.format("Data object reference '%s' does not have a type name. Cannot validate form alignment.",
                                dataObjectRefId));
            }

            // Find the PUML class
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass pumlClass = 
                pumlDiagram.classes().get(typeName);

            if (pumlClass == null) {
                throw new IllegalStateException(
                        String.format("Type '%s' from data object '%s' does not exist in PUML diagram. " +
                                        "Available classes: %s",
                                typeName, dataObjectRefId, pumlDiagram.classes().keySet()));
            }

            // Load and parse the form JSON schema
            JsonNode formSchema;
            try {
                formSchema = mapper.readTree(new File(fullFormPath));
            } catch (IOException e) {
                throw new IllegalStateException(
                        String.format("Failed to read form file '%s': %s", fullFormPath, e.getMessage()), e);
            }

            // Recursively validate the form schema against the PUML class
            validateFormSchemaAgainstPumlClass(formSchema, typeName, pumlClass, pumlDiagram, formRef, new java.util.HashSet<>());
        }
    }

    /**
     * Recursively validates a JSON schema object against a PUML class.
     * Handles nested objects, $ref references, and $defs.
     *
     * @param schemaNode     the JSON schema node to validate (can be full schema or nested object)
     * @param className      the name of the PUML class being validated against
     * @param pumlClass      the PUML class definition
     * @param pumlDiagram    the full PUML diagram for resolving nested classes
     * @param formRef        the original form reference (for error messages)
     * @param visitedClasses set of already visited class names to prevent infinite recursion
     */
    private static void validateFormSchemaAgainstPumlClass(
            JsonNode schemaNode,
            String className,
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass pumlClass,
            PumlDiagram pumlDiagram,
            String formRef,
            java.util.Set<String> visitedClasses) {

        // Prevent infinite recursion
        if (visitedClasses.contains(className)) {
            return;
        }
        visitedClasses.add(className);

        // Extract properties from the schema
        JsonNode properties = schemaNode.get("properties");
        if (properties == null || !properties.isObject()) {
            // No properties to validate
            return;
        }

        // Get all field names and their types from the PUML class
        java.util.Map<String, String> pumlFieldsMap = new java.util.HashMap<>();
        if (pumlClass.fields() != null) {
            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField field : pumlClass.fields()) {
                pumlFieldsMap.put(field.name(), field.type());
            }
        }

        // Check each form property
        List<String> missingFields = new ArrayList<>();
        java.util.Iterator<String> propertyNames = properties.fieldNames();
        
        while (propertyNames.hasNext()) {
            String propertyName = propertyNames.next();
            JsonNode propertyDef = properties.get(propertyName);

            // Check if property exists in PUML class
            if (!pumlFieldsMap.containsKey(propertyName)) {
                missingFields.add(propertyName);
                continue;
            }

            // Get the PUML field type for this property
            String pumlFieldType = pumlFieldsMap.get(propertyName);

            // Check if this property has a nested structure
            JsonNode resolvedPropertyDef = resolveSchemaRef(propertyDef, schemaNode);
            
            if (resolvedPropertyDef != null && resolvedPropertyDef.has("type")) {
                String jsonType = resolvedPropertyDef.get("type").asText();
                
                // If it's an object type, recursively validate
                if ("object".equals(jsonType)) {
                    // The PUML field type should correspond to a PUML class
                    if (pumlFieldType != null && !pumlFieldType.isEmpty()) {
                        org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass nestedPumlClass = 
                            pumlDiagram.classes().get(pumlFieldType);
                        
                        if (nestedPumlClass == null) {
                            // PUML field type doesn't correspond to a known class
                            // This might be okay (e.g., generic types), so we just skip deep validation
                            continue;
                        }

                        // Recursively validate the nested object
                        validateFormSchemaAgainstPumlClass(
                            resolvedPropertyDef,
                            pumlFieldType,
                            nestedPumlClass,
                            pumlDiagram,
                            formRef,
                            new java.util.HashSet<>(visitedClasses) // New set to allow the same class in different branches
                        );
                    }
                }
            }
        }

        if (!missingFields.isEmpty()) {
            throw new IllegalStateException(
                    String.format("Form '%s' (class '%s') contains properties %s " +
                                    "that do not exist in the PUML class '%s'. " +
                                    "Available fields in '%s': %s",
                            formRef,
                            className,
                            missingFields,
                            className,
                            className,
                            new ArrayList<>(pumlFieldsMap.keySet())));
        }
    }

    /**
     * Resolves a $ref reference in a JSON schema node.
     * Supports internal references like "#/$defs/ClassName".
     *
     * @param node       the JSON node that might contain a $ref
     * @param rootSchema the root schema node for resolving references
     * @return the resolved node, or the original node if no $ref exists
     */
    private static JsonNode resolveSchemaRef(JsonNode node, JsonNode rootSchema) {
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            
            // Handle internal references like "#/$defs/ClarificationMessage"
            if (ref.startsWith("#/")) {
                String[] pathParts = ref.substring(2).split("/");
                JsonNode resolved = rootSchema;
                
                for (String part : pathParts) {
                    resolved = resolved.get(part);
                    if (resolved == null) {
                        return null; // Reference not found
                    }
                }
                
                return resolved;
            }
            
            // For external references or other formats, return null (not supported yet)
            return null;
        }
        
        return node;
    }

    /**
     * Validates that fields required by business object states are also marked as required in forms.
     * For each form-dataobject pair, checks that:
     * 1. The data object's state defines required fields
     * 2. Those required fields are in the form's "required" array
     *
     * @param formDataObjectPairs      list of form-dataobject pairs from user tasks
     * @param bpmnDefinitions          the parsed BPMN definitions containing data objects
     * @param businessObjectStatesData the loaded business object state data
     * @param formsBasePath            base path to resolve form file paths (e.g., "src/main/resources/")
     * @throws IllegalStateException if any state-required field is not marked as required in the form
     */
    public static void validateStateRequiredFieldsInForms(
            List<FormDataObjectPair> formDataObjectPairs,
            BpmnDefinitions bpmnDefinitions,
            BusinessObjectState businessObjectStatesData,
            String formsBasePath) {
        
        if (formDataObjectPairs == null || formDataObjectPairs.isEmpty()) {
            return; // Nothing to validate
        }

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            throw new IllegalStateException("BPMN definitions are null. Cannot validate forms.");
        }

        if (businessObjectStatesData == null || businessObjectStatesData.classes == null) {
            throw new IllegalStateException("BusinessObjectState data is null. Cannot validate forms.");
        }

        ObjectMapper mapper = new ObjectMapper();

        for (FormDataObjectPair pair : formDataObjectPairs) {
            String formRef = pair.formRef();
            String dataObjectRefId = pair.dataObjectRefId();

            // Extract form file path
            String formPath = extractFilePathFromRef(formRef);
            if (formPath == null) {
                throw new IllegalStateException(
                        String.format("Invalid formRef format: '%s'. Expected format: ${file:path/to/form.json}",
                                formRef));
            }

            // Normalize path to actual file location
            String fullFormPath;
            if (formPath.startsWith("forms/")) {
                fullFormPath = formsBasePath + "models/form/" + formPath.substring(6);
            } else {
                fullFormPath = formsBasePath + formPath;
            }

            // Find the data object reference in BPMN to get the type name and state
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef = null;
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.dataObjectsById() != null && process.dataObjectsById().containsKey(dataObjectRefId)) {
                    dataObjRef = process.dataObjectsById().get(dataObjectRefId);
                    break;
                }
            }

            if (dataObjRef == null) {
                throw new IllegalStateException(
                        String.format("Data object reference '%s' associated with form '%s' not found in BPMN definitions.",
                                dataObjectRefId, formRef));
            }

            String typeName = dataObjRef.typeName();
            String stateName = dataObjRef.stateName();

            if (typeName == null || typeName.trim().isEmpty()) {
                // Can't validate without type name
                continue;
            }

            if (stateName == null || stateName.trim().isEmpty()) {
                // No state specified, skip validation
                continue;
            }

            // Find the class type in business object states
            ClassType classType = businessObjectStatesData.classes.stream()
                    .filter(ct -> typeName.equals(ct.name))
                    .findFirst()
                    .orElse(null);

            if (classType == null) {
                // Class not found in states, skip (this might be validated elsewhere)
                continue;
            }

            // Find the state
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.State state = null;
            if (classType.states != null) {
                state = classType.states.stream()
                        .filter(s -> stateName.equals(s.name))
                        .findFirst()
                        .orElse(null);
            }

            if (state == null || state.requiredFields == null || state.requiredFields.isEmpty()) {
                // No required fields defined for this state, skip
                continue;
            }

            // Load and parse the form JSON schema
            JsonNode formSchema;
            try {
                formSchema = mapper.readTree(new File(fullFormPath));
            } catch (IOException e) {
                throw new IllegalStateException(
                        String.format("Failed to read form file '%s': %s", fullFormPath, e.getMessage()), e);
            }

            // Get the required fields from the form schema
            JsonNode requiredNode = formSchema.get("required");
            List<String> formRequiredFields = new ArrayList<>();
            if (requiredNode != null && requiredNode.isArray()) {
                requiredNode.forEach(node -> formRequiredFields.add(node.asText()));
            }

            // Check that all state-required fields are also form-required
            List<String> missingRequiredFields = new ArrayList<>();
            for (String stateRequiredField : state.requiredFields) {
                if (!formRequiredFields.contains(stateRequiredField)) {
                    missingRequiredFields.add(stateRequiredField);
                }
            }

            if (!missingRequiredFields.isEmpty()) {
                throw new IllegalStateException(
                        String.format("Form '%s' (for data object '%s' of type '%s' in state '%s') " +
                                        "does not mark fields %s as required, but these fields are required " +
                                        "by the state definition. Form's required fields: %s, State's required fields: %s",
                                formRef,
                                dataObjectRefId,
                                typeName,
                                stateName,
                                missingRequiredFields,
                                formRequiredFields,
                                state.requiredFields));
            }
        }
    }

    /**
     * Validates that smtpConfig exists if there are any tasks with emailJsonRef.
     * If any task in any process has an emailJsonRef set, then smtpConfig must not be null.
     *
     * @param configFile the loaded config file
     * @throws IllegalStateException if email tasks exist but smtpConfig is null
     */
    public static void validateSmtpConfigExistsForEmailTasks(ConfigFile configFile) {
        if (configFile == null || configFile.processes == null) {
            return; // Nothing to validate
        }

        // Check if there are any tasks with emailJsonRef
        boolean hasEmailTasks = false;
        List<String> emailTaskNames = new ArrayList<>();

        for (Process process : configFile.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }

            for (Node task : process.config.tasks) {
                if (task.emailJsonRef != null && !task.emailJsonRef.trim().isEmpty()) {
                    hasEmailTasks = true;
                    String taskName = task.name != null ? task.name : "unnamed";
                    String processName = process.id != null ? process.id : (process.name != null ? process.name : "unnamed");
                    emailTaskNames.add(String.format("'%s' in process '%s'", taskName, processName));
                }
            }
        }

        // If there are email tasks but no smtpConfig, throw an exception
        if (hasEmailTasks && configFile.smtpConfig == null) {
            throw new IllegalStateException(
                    String.format("Email tasks found but smtpConfig is missing. " +
                                    "Tasks with emailJsonRef: %s. " +
                                    "Please provide smtpConfig in the configuration file.",
                            String.join(", ", emailTaskNames)));
        }
    }

    /**
     * Copies a config file to an output directory.
     *
     * @param sourceConfigFilePath the source config file path
     * @param outputDirPath        the output directory path
     * @throws RuntimeException if file operations fail
     */
    public static void exportConfigFile(String sourceConfigFilePath, String outputDirPath) {
        try {
            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            // Get source file name and create output path
            Path sourcePath = Paths.get(sourceConfigFilePath);
            String fileName = sourcePath.getFileName().toString();
            Path outputPath = outputDir.resolve(fileName);

            // Copy source file to output directory
            Files.copy(sourcePath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy config file: " + sourceConfigFilePath, e);
        }
    }
}
