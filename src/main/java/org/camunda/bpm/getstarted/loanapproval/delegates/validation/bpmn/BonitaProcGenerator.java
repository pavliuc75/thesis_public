package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlDiagram;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Process;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BonitaProcGenerator {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern EMAIL_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");

    public static String copyProcFileToOutput(String sourceProcFilePath, String outputDirPath) {
        try {
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            Path sourcePath = Paths.get(sourceProcFilePath);
            String fileName = sourcePath.getFileName().toString();
            Path outputPath = outputDir.resolve(fileName);

            Files.copy(sourcePath, outputPath, StandardCopyOption.REPLACE_EXISTING);

            return outputPath.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy Proc file: " + sourceProcFilePath, e);
        }
    }

    public static Document parseProcDocument(String procFilePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new File(procFilePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Proc document: " + procFilePath, e);
        }
    }

    /**
     * Adds actors based on lane names found in the proc document.
     * Searches for all Lane elements in the document and creates actors for each unique lane name.
     * Actors are added as children of the Pool element.
     * Returns a map of lane name (lowercase) to actor ID for later reference.
     *
     * @param doc the proc Document
     * @return map of lane name (lowercase) to actor ID
     */
    public static Map<String, String> addActorsToDocument(Document doc) {
        Map<String, String> laneNameToActorId = new HashMap<>();
        
        // Find the Pool element (xmi:type="process:Pool")
        NodeList allElements = doc.getElementsByTagName("elements");
        Element poolElement = null;
        java.util.Set<String> uniqueLaneNames = new java.util.HashSet<>();

        // Find pool and collect lane names in one pass
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String xmiType = element.getAttribute("xmi:type");
            
            if ("process:Pool".equals(xmiType)) {
                poolElement = element;
            } else if ("process:Lane".equals(xmiType)) {
                String laneName = element.getAttribute("name");
                if (laneName != null && !laneName.isEmpty()) {
                    uniqueLaneNames.add(laneName);
                }
            }
        }

        if (poolElement == null) {
            throw new RuntimeException("No Pool element found in proc document");
        }

        // Add actors for each unique lane name
        for (String laneName : uniqueLaneNames) {
            String actorId = generateUniqueId();
            
            Element actorElement = doc.createElement("actors");
            actorElement.setAttribute("xmi:type", "process:Actor");
            actorElement.setAttribute("xmi:id", actorId);
            actorElement.setAttribute("name", laneName);

            // Add actor as child of pool
            poolElement.appendChild(actorElement);
            
            // Store mapping for later use
            laneNameToActorId.put(laneName, actorId);
        }
        
        return laneNameToActorId;
    }

    /**
     * Generates a unique ID similar to Bonita's format: _xzGm8O8fEfCoIMCqMDCGog
     *
     * @return a unique ID string
     */
    private static String generateUniqueId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder id = new StringBuilder("_");

        for (int i = 0; i < 22; i++) {
            id.append(chars.charAt(random.nextInt(chars.length())));
        }

        return id.toString();
    }

    /**
     * Updates Lane elements in the proc document with actor attribute references.
     * Assigns actor IDs to lanes based on their names using the provided map.
     *
     * @param doc               the proc Document
     * @param laneNameToActorId map of lane name to actor ID
     */
    public static void updateLanesWithActorsInDocument(Document doc, Map<String, String> laneNameToActorId) {
        if (doc == null || laneNameToActorId == null || laneNameToActorId.isEmpty()) {
            return;
        }

        // Find all Lane elements and update them with actor attribute
        NodeList allElements = doc.getElementsByTagName("elements");
        
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String xmiType = element.getAttribute("xmi:type");
            
            if ("process:Lane".equals(xmiType)) {
                String laneName = element.getAttribute("name");
                
                if (laneName == null || laneName.isEmpty()) {
                    continue;
                }

                // Find the actor ID for this lane name
                String actorId = laneNameToActorId.get(laneName);
                
                if (actorId != null) {
                    // Set the actor attribute on the lane
                    element.setAttribute("actor", actorId);
                }
            }
        }
    }

    /**
     * Updates timer events in the proc document with time conditions from BpmnDefinitions.
     * Converts ISO8601 durations to milliseconds and adds condition elements to timer events.
     *
     * @param doc             the proc Document
     * @param bpmnDefinitions the BPMN definitions containing flow nodes with iso8601time
     */
    public static void updateEventsWithTimeEventsInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (doc == null || bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // Collect all events with iso8601time from BPMN definitions (by name)
        Map<String, String> eventNameToIso8601Time = new HashMap<>();
        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() != null) {
                for (FlowNode node : process.nodesById().values()) {
                    if (node.iso8601time() != null && !node.iso8601time().isEmpty() && 
                        node.name() != null && !node.name().isEmpty()) {
                        eventNameToIso8601Time.put(node.name().toLowerCase(), node.iso8601time());
                    }
                }
            }
        }

        if (eventNameToIso8601Time.isEmpty()) {
            return;
        }

        // Find all boundary timer events in the proc document
        findAndUpdateBoundaryTimerEvents(doc, eventNameToIso8601Time);
    }

    /**
     * Updates sequence flow conditions in the proc document with resolvedExpression values.
     * Matches sequence flows by name, since Bonita sequence flow IDs differ from BPMN IDs.
     *
     * @param doc             the proc Document
     * @param bpmnDefinitions the BPMN definitions containing sequence flows with resolved expressions
     */
    public static void updateSequenceFlowsExpressionsInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (doc == null || bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        Map<String, DataObjectInfo> dataObjectsByName = collectBusinessObjectDataByName(doc);

        Map<String, SequenceFlow> flowsByName = new HashMap<>();
        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.flowsById() == null || process.flowsById().isEmpty()) {
                continue;
            }

            for (SequenceFlow flow : process.flowsById().values()) {
                if (flow.name() == null || flow.name().isEmpty()) {
                    continue;
                }
                if (flow.resolvedExpression() == null || flow.resolvedExpression().trim().isEmpty()) {
                    continue;
                }
                flowsByName.put(flow.name().toLowerCase(), flow);
            }
        }

        if (flowsByName.isEmpty()) {
            return;
        }

        NodeList connectionNodes = doc.getElementsByTagName("connections");
        for (int i = 0; i < connectionNodes.getLength(); i++) {
            Element connectionEl = (Element) connectionNodes.item(i);
            if (!"process:SequenceFlow".equals(connectionEl.getAttribute("xmi:type"))) {
                continue;
            }

            String flowName = connectionEl.getAttribute("name");
            if (flowName == null || flowName.trim().isEmpty()) {
                continue;
            }

            SequenceFlow sequenceFlow = flowsByName.get(flowName.trim().toLowerCase());
            if (sequenceFlow == null) {
                continue;
            }

            String resolvedExpression = sequenceFlow.resolvedExpression();
            if (resolvedExpression == null || resolvedExpression.trim().isEmpty()) {
                continue;
            }

            NodeList conditionNodes = connectionEl.getElementsByTagName("condition");
            Element conditionEl;
            if (conditionNodes.getLength() > 0) {
                conditionEl = (Element) conditionNodes.item(0);
            } else {
                conditionEl = doc.createElement("condition");
                conditionEl.setAttribute("xmi:type", "expression:Expression");
                conditionEl.setAttribute("xmi:id", generateUniqueId());
                connectionEl.appendChild(conditionEl);
            }

            conditionEl.setAttribute("name", resolvedExpression);
            conditionEl.setAttribute("content", resolvedExpression);
            conditionEl.setAttribute("interpreter", "GROOVY");
            conditionEl.setAttribute("type", "TYPE_READ_ONLY_SCRIPT");
            conditionEl.setAttribute("returnType", "java.lang.Boolean");
            conditionEl.setAttribute("returnTypeFixed", "true");
            conditionEl.setAttribute("automaticDependencies", "false");

            removeChildElementsByName(conditionEl, "referencedElements");
            for (Map.Entry<String, DataObjectInfo> entry : dataObjectsByName.entrySet()) {
                String variableName = entry.getKey();
                if (!containsVariableReference(resolvedExpression, variableName)) {
                    continue;
                }
                DataObjectInfo info = entry.getValue();
                Element referencedEl = doc.createElement("referencedElements");
                referencedEl.setAttribute("xmi:type", "process:BusinessObjectData");
                referencedEl.setAttribute("xmi:id", generateUniqueId());
                referencedEl.setAttribute("name", variableName);
                referencedEl.setAttribute("dataType", info.dataType);
                referencedEl.setAttribute("className", info.className);
                conditionEl.appendChild(referencedEl);
            }
        }
    }

    /**
     * Updates flow node elements in the proc document with Bonita vendor attributes.
     * Matches flow nodes by name (case-insensitive).
     */
    public static void updateFlowNodesVendorAttributesInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (doc == null || bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        Map<String, Element> procElementsByName = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String name = element.getAttribute("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            procElementsByName.putIfAbsent(name.trim().toLowerCase(), element);
        }

        if (procElementsByName.isEmpty()) {
            return;
        }

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                continue;
            }
            for (FlowNode flowNode : process.nodesById().values()) {
                if (flowNode == null || flowNode.name() == null || flowNode.name().trim().isEmpty()) {
                    continue;
                }
                if (flowNode.vendorAttributes() == null || flowNode.vendorAttributes().isEmpty()) {
                    continue;
                }

                Element procElement = procElementsByName.get(flowNode.name().trim().toLowerCase());
                if (procElement == null) {
                    continue;
                }

                for (Map.Entry<String, String> attr : flowNode.vendorAttributes().entrySet()) {
                    String attrName = attr.getKey();
                    String attrValue = attr.getValue();

                    if (attrName == null || !attrName.contains("bonita")) {
                        continue;
                    }

                    if (attrValue == null || attrValue.trim().isEmpty()) {
                        continue;
                    }

                    appendXmlChildren(procElement, doc, attrValue);
                }
            }
        }
    }

    /**
     * Updates businessRuleTask tasks in the proc document with DMN logic in Groovy format.
     * Finds businessRuleTask nodes by name, resolves their DMN file from processedDmnFiles,
     * and injects a single operation that sets the DMN result variable on the business object.
     */
    public static void updateBusinessRuleTaskTasksWithDmnReferencesInDocument(
            Document doc,
            BpmnDefinitions bpmnDefinitions,
            Map<String, String> processedDmnFiles) {
        if (doc == null || bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }
        if (processedDmnFiles == null || processedDmnFiles.isEmpty()) {
            return;
        }

        Map<String, Element> procElementsByName = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String name = element.getAttribute("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            procElementsByName.putIfAbsent(name.trim().toLowerCase(), element);
        }

        if (procElementsByName.isEmpty()) {
            return;
        }

        Map<String, DataObjectInfo> dataObjectsByName = collectBusinessObjectDataByName(doc);
        String businessObjectTypeId = findBusinessObjectTypeId(doc);

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null || process.nodesById().isEmpty()) {
                continue;
            }

            for (FlowNode node : process.nodesById().values()) {
                if (!"businessRuleTask".equals(node.type())) {
                    continue;
                }
                if (node.name() == null || node.name().trim().isEmpty()) {
                    continue;
                }

                String dmnFileKey = resolveDmnFileKey(node.dmnRef(), processedDmnFiles);
                if (dmnFileKey == null) {
                    continue;
                }

                String dmnXml = processedDmnFiles.get(dmnFileKey);
                if (dmnXml == null || dmnXml.trim().isEmpty()) {
                    continue;
                }

                DmnDecisionTable table = parseDmnDecisionTable(dmnXml);
                if (table == null || table.inputs.isEmpty() || table.rules.isEmpty()) {
                    continue;
                }

                String outputVar = normalizeDmnResultVariable(node.dmnResultVariable());
                if (outputVar == null || outputVar.trim().isEmpty()) {
                    outputVar = table.output != null ? table.output.name : null;
                }
                if (outputVar == null || outputVar.trim().isEmpty()) {
                    continue;
                }

                String outputVarTrimmed = outputVar.trim();
                String rootVariable = extractRootVariableFromExpression(outputVarTrimmed);
                String outputField = extractOutputField(outputVarTrimmed);
                if (rootVariable == null || rootVariable.isEmpty()) {
                    rootVariable = extractFirstRootVariable(table.inputs);
                }
                if (rootVariable == null || rootVariable.isEmpty()) {
                    continue;
                }

                DataObjectInfo dataInfo = dataObjectsByName.get(rootVariable);
                if (dataInfo == null) {
                    continue;
                }

                String outputType = mapDmnTypeRefToJava(table.output != null ? table.output.typeRef : null);
                String groovyScript = buildGroovyScript(table, outputType);

                Element taskEl = procElementsByName.get(node.name().trim().toLowerCase());
                if (taskEl == null) {
                    continue;
                }

                removeChildElementsByName(taskEl, "operations");

                Element operationEl = doc.createElement("operations");
                operationEl.setAttribute("xmi:type", "expression:Operation");
                operationEl.setAttribute("xmi:id", generateUniqueId());

                Element leftOperand = createLeftOperandForBusinessObject(doc, rootVariable, dataInfo, businessObjectTypeId);
                Element rightOperand = createGroovyScriptOperand(doc, groovyScript, outputType, rootVariable, dataInfo);
                Element operator = createOperatorForField(doc, outputField, outputType);

                operationEl.appendChild(leftOperand);
                operationEl.appendChild(rightOperand);
                operationEl.appendChild(operator);

                Element operationsAnchor = findOperationsInsertionAnchor(taskEl);
                if (operationsAnchor != null) {
                    taskEl.insertBefore(operationEl, operationsAnchor);
                } else {
                    taskEl.appendChild(operationEl);
                }
            }
        }
    }

    private static void appendXmlChildren(Element parent, Document doc, String xmlFragment) {
        if (xmlFragment == null || xmlFragment.trim().isEmpty()) {
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            String wrapped = "<wrapper "
                    + "xmlns:xmi=\"http://www.omg.org/XMI\" "
                    + "xmlns:process=\"http://www.bonitasoft.org/ns/bpm/process\" "
                    + "xmlns:expression=\"http://www.bonitasoft.org/ns/bpm/expression\" "
                    + "xmlns:connectorconfiguration=\"http://www.bonitasoft.org/model/connector/configuration\""
                    + ">"
                    + xmlFragment
                    + "</wrapper>";
            Document fragmentDoc = builder.parse(new java.io.ByteArrayInputStream(
                    wrapped.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Element wrapper = fragmentDoc.getDocumentElement();
            NodeList children = wrapper.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                org.w3c.dom.Node imported = doc.importNode(children.item(i), true);
                parent.appendChild(imported);
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to append Bonita vendor XML fragment: " + e.getMessage());
        }
    }

    /**
     * Finds and updates timer events with condition elements.
     * Searches all elements and matches events by name.
     */
    private static void findAndUpdateBoundaryTimerEvents(Document doc, Map<String, String> eventNameToIso8601Time) {
        // Search all elements in the document
        NodeList allElements = doc.getElementsByTagName("*");

        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String eventName = element.getAttribute("name");
            String xmiType = element.getAttribute("xmi:type");

            // Check if this element has a name, is a timer event, and matches our BPMN events
            if (xmiType != null && xmiType.contains("TimerEvent") && 
                eventName != null && !eventName.isEmpty() && 
                eventNameToIso8601Time.containsKey(eventName.toLowerCase())) {
                String iso8601Duration = eventNameToIso8601Time.get(eventName.toLowerCase());

                // Convert ISO8601 duration to milliseconds
                long milliseconds = convertISO8601DurationToMillis(iso8601Duration);

                // Convert milliseconds to human-readable time (HH:MM:SS)
                String readableTime = convertMillisToReadableTime(milliseconds);

                // Create condition element
                Element conditionElement = doc.createElement("condition");
                conditionElement.setAttribute("xmi:type", "expression:Expression");
                conditionElement.setAttribute("xmi:id", generateUniqueId());
                conditionElement.setAttribute("name", readableTime);
                conditionElement.setAttribute("content", milliseconds + "L");
                conditionElement.setAttribute("interpreter", "GROOVY");
                conditionElement.setAttribute("type", "TYPE_READ_ONLY_SCRIPT");
                conditionElement.setAttribute("returnType", "java.lang.Long");

                // Add condition as child of the timer event
                element.appendChild(conditionElement);
            }
        }
    }

    private static Map<String, DataObjectInfo> collectBusinessObjectDataByName(Document doc) {
        Map<String, DataObjectInfo> dataObjects = new HashMap<>();
        NodeList dataNodes = doc.getElementsByTagName("data");
        for (int i = 0; i < dataNodes.getLength(); i++) {
            Element dataEl = (Element) dataNodes.item(i);
            if (!"process:BusinessObjectData".equals(dataEl.getAttribute("xmi:type"))) {
                continue;
            }
            String name = dataEl.getAttribute("name");
            String dataType = dataEl.getAttribute("dataType");
            String className = dataEl.getAttribute("className");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (dataType == null || dataType.trim().isEmpty()) {
                continue;
            }
            if (className == null || className.trim().isEmpty()) {
                continue;
            }
            dataObjects.putIfAbsent(name.trim(), new DataObjectInfo(className, dataType));
        }
        return dataObjects;
    }

    private static boolean containsVariableReference(String expression, String variableName) {
        if (expression == null || expression.isEmpty() || variableName == null || variableName.isEmpty()) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(variableName) + "\\b");
        return pattern.matcher(expression).find();
    }

    /**
     * Adds data objects to the pool in the proc document based on BPMN data object references.
     * Matches by variable name and uses the BusinessObjectType data type from the proc document.
     *
     * @param doc             the proc Document
     * @param bpmnDefinitions the BPMN definitions containing data object references
     */
    public static void addDataObjectsToPoolInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (doc == null || bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        Map<String, String> variableNameToTypeName = new HashMap<>();
        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.dataObjectsById() == null || process.dataObjectsById().isEmpty()) {
                continue;
            }
            for (DataObjectReference dataObjectRef : process.dataObjectsById().values()) {
                String variableName = dataObjectRef.variableName();
                String typeName = dataObjectRef.typeName();
                if (variableName == null || variableName.trim().isEmpty()) {
                    continue;
                }
                if (typeName == null || typeName.trim().isEmpty()) {
                    continue;
                }
                variableNameToTypeName.putIfAbsent(variableName.trim(), typeName.trim());
            }
        }

        if (variableNameToTypeName.isEmpty()) {
            return;
        }

        Element poolElement = null;
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if ("process:Pool".equals(element.getAttribute("xmi:type"))) {
                poolElement = element;
                break;
            }
        }

        if (poolElement == null) {
            return;
        }

        String businessObjectTypeId = null;
        NodeList dataTypes = doc.getElementsByTagName("datatypes");
        for (int i = 0; i < dataTypes.getLength(); i++) {
            Element dataTypeEl = (Element) dataTypes.item(i);
            if ("process:BusinessObjectType".equals(dataTypeEl.getAttribute("xmi:type"))) {
                businessObjectTypeId = dataTypeEl.getAttribute("xmi:id");
                if (businessObjectTypeId != null && !businessObjectTypeId.isEmpty()) {
                    break;
                }
            }
        }

        if (businessObjectTypeId == null || businessObjectTypeId.isEmpty()) {
            return;
        }

        java.util.Set<String> existingNames = new java.util.HashSet<>();
        NodeList dataNodes = poolElement.getElementsByTagName("data");
        for (int i = 0; i < dataNodes.getLength(); i++) {
            Element dataEl = (Element) dataNodes.item(i);
            String name = dataEl.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                existingNames.add(name);
            }
        }

        for (Map.Entry<String, String> entry : variableNameToTypeName.entrySet()) {
            String variableName = entry.getKey();
            if (existingNames.contains(variableName)) {
                continue;
            }

            Element dataElement = doc.createElement("data");
            dataElement.setAttribute("xmi:type", "process:BusinessObjectData");
            dataElement.setAttribute("xmi:id", generateUniqueId());
            dataElement.setAttribute("name", variableName);
            dataElement.setAttribute("dataType", businessObjectTypeId);
            dataElement.setAttribute("className", "com.company.model." + entry.getValue());

            Element defaultValueEl = doc.createElement("defaultValue");
            defaultValueEl.setAttribute("xmi:type", "expression:Expression");
            defaultValueEl.setAttribute("xmi:id", generateUniqueId());
            defaultValueEl.setAttribute("name", "");
            defaultValueEl.setAttribute("content", "");
            defaultValueEl.setAttribute("interpreter", "GROOVY");
            defaultValueEl.setAttribute("type", "TYPE_READ_ONLY_SCRIPT");
            defaultValueEl.setAttribute("returnType", "java.lang.Object");

            dataElement.appendChild(defaultValueEl);
            poolElement.appendChild(dataElement);
        }
    }

    /**
     * Adds contracts and operations to user tasks in the proc document based on BPMN data output associations.
     * Matches user tasks by name and builds contract inputs from PUML class definitions.
     *
     * @param doc                the proc Document
     * @param bpmnDefinitions    the BPMN definitions containing user tasks and data output associations
     * @param businessObjectData the PUML diagram with business object classes
     */
    public static void addContractsAndOperationsToUserTasksInDocument(Document doc, BpmnDefinitions bpmnDefinitions,
                                                                      PumlDiagram businessObjectData) {
        if (doc == null || bpmnDefinitions == null || bpmnDefinitions.processes() == null || businessObjectData == null) {
            return;
        }

        Map<String, Element> procUserTasksByName = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String xmiType = element.getAttribute("xmi:type");
            if (!isProcUserTaskType(xmiType)) {
                continue;
            }
            String name = element.getAttribute("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            procUserTasksByName.putIfAbsent(name.trim().toLowerCase(), element);
        }

        if (procUserTasksByName.isEmpty()) {
            return;
        }

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null || process.nodesById().isEmpty()) {
                continue;
            }
            if (process.dataObjectsById() == null || process.dataObjectsById().isEmpty()) {
                continue;
            }

            for (FlowNode node : process.nodesById().values()) {
                if (!"userTask".equals(node.type())) {
                    continue;
                }
                if (node.name() == null || node.name().trim().isEmpty()) {
                    continue;
                }
                if (node.dataOutputAssociations() == null || node.dataOutputAssociations().isEmpty()) {
                    continue;
                }

                Element procTaskEl = procUserTasksByName.get(node.name().trim().toLowerCase());
                if (procTaskEl == null) {
                    continue;
                }

                String dataObjectRefId = node.dataOutputAssociations().get(0).targetRef();
                if (dataObjectRefId == null || dataObjectRefId.trim().isEmpty()) {
                    continue;
                }

                DataObjectReference dataObjectRef = process.dataObjectsById().get(dataObjectRefId.trim());
                if (dataObjectRef == null) {
                    continue;
                }

                String variableName = dataObjectRef.variableName();
                String typeName = dataObjectRef.typeName();
                if (variableName == null || variableName.trim().isEmpty()) {
                    continue;
                }
                if (typeName == null || typeName.trim().isEmpty()) {
                    continue;
                }

                if (businessObjectData.classes() == null || !businessObjectData.classes().containsKey(typeName)) {
                    continue;
                }

                String businessObjectTypeId = findBusinessObjectTypeId(doc);
                if (businessObjectTypeId == null) {
                    continue;
                }

                Element contractEl = getOrCreateContractElement(doc, procTaskEl);
                clearElementChildren(contractEl);

                Element topInputEl = createContractInputElement(doc, variableName.trim() + "Input");
                topInputEl.setAttribute("type", "COMPLEX");
                topInputEl.setAttribute("dataReference", variableName.trim());
                contractEl.appendChild(topInputEl);

                addContractInputsForClass(doc, topInputEl, typeName.trim(), businessObjectData, new HashSet<>());

                removeChildElementsByName(procTaskEl, "operations");
                addOperationsForClass(doc, procTaskEl, contractEl, variableName.trim(), typeName.trim(),
                        businessObjectTypeId, businessObjectData);
            }
        }
    }

    public static void updateUserTasksWithFormRefsInDocument(Document doc, ConfigFile configFileData,
                                                             String indexFilePath) {
        if (doc == null || configFileData == null || configFileData.processes == null) {
            return;
        }

        Map<String, String> formIdToUuid = loadFormIndex(indexFilePath);
        if (formIdToUuid.isEmpty()) {
            return;
        }

        Map<String, Element> procUserTasksByName = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!isProcUserTaskType(element.getAttribute("xmi:type"))) {
                continue;
            }
            String name = element.getAttribute("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            procUserTasksByName.putIfAbsent(name.trim().toLowerCase(), element);
        }

        if (procUserTasksByName.isEmpty()) {
            return;
        }

        for (Process process : configFileData.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }
            for (Node task : process.config.tasks) {
                if (task.formRef == null || task.formRef.trim().isEmpty()) {
                    continue;
                }
                if (task.name == null || task.name.trim().isEmpty()) {
                    continue;
                }

                String formPath = extractFormPathFromRef(task.formRef);
                if (formPath == null) {
                    continue;
                }

                String formFileName = Paths.get(formPath).getFileName().toString();
                String formId = toFormId(stripExtension(formFileName));
                String uuid = formIdToUuid.get(formId);
                if (uuid == null || uuid.isEmpty()) {
                    System.out.println("Warning: No form UUID found for form '" + formId + "'.");
                    continue;
                }

                Element taskEl = procUserTasksByName.get(task.name.trim().toLowerCase());
                if (taskEl == null) {
                    continue;
                }

                removeChildElementsByName(taskEl, "formMapping");

                Element formMappingEl = doc.createElement("formMapping");
                formMappingEl.setAttribute("xmi:type", "process:FormMapping");
                formMappingEl.setAttribute("xmi:id", generateUniqueId());

                Element targetFormEl = doc.createElement("targetForm");
                targetFormEl.setAttribute("xmi:type", "expression:Expression");
                targetFormEl.setAttribute("xmi:id", generateUniqueId());
                targetFormEl.setAttribute("name", formId);
                targetFormEl.setAttribute("content", uuid);
                targetFormEl.setAttribute("type", "FORM_REFERENCE_TYPE");
                targetFormEl.setAttribute("returnTypeFixed", "true");
                formMappingEl.appendChild(targetFormEl);

                Element contractEl = findDirectChild(taskEl, "contract");
                if (contractEl != null) {
                    taskEl.insertBefore(formMappingEl, contractEl);
                } else {
                    taskEl.appendChild(formMappingEl);
                }
            }
        }
    }

    public static void updateServiceTasksWithRestConnectorsInDocument(Document doc,
                                                                      Map<String, String> processedRestFiles,
                                                                      ConfigFile configFileData) {
        if (doc == null || processedRestFiles == null || processedRestFiles.isEmpty()) {
            return;
        }
        if (configFileData == null || configFileData.processes == null) {
            return;
        }

        Map<String, Element> procServiceTasksByName = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!"process:ServiceTask".equals(element.getAttribute("xmi:type"))) {
                continue;
            }
            String name = element.getAttribute("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            procServiceTasksByName.putIfAbsent(name.trim().toLowerCase(), element);
        }

        if (procServiceTasksByName.isEmpty()) {
            return;
        }

        Map<String, DataObjectInfo> dataObjectsByName = collectBusinessObjectDataByName(doc);

        for (Process process : configFileData.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }

            for (Node task : process.config.tasks) {
                if (task.restCallRef == null || task.restCallRef.trim().isEmpty()) {
                    continue;
                }
                if (task.name == null || task.name.trim().isEmpty()) {
                    continue;
                }

                Element taskEl = procServiceTasksByName.get(task.name.trim().toLowerCase());
                if (taskEl == null) {
                    continue;
                }

                String restFileName = resolveRestFileNameForTask(task, processedRestFiles);
                if (restFileName == null) {
                    continue;
                }

                String restJson = processedRestFiles.get(restFileName);
                if (restJson == null || restJson.trim().isEmpty()) {
                    continue;
                }

                JsonNode restConfig;
                try {
                    restConfig = mapper.readTree(restJson);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to parse REST config '" + restFileName + "': " + e.getMessage());
                    continue;
                }

                JsonNode requestNode = restConfig.get("request");
                if (requestNode == null || !requestNode.isObject()) {
                    continue;
                }

                String method = requestNode.has("method") ? requestNode.get("method").asText() : "POST";
                String url = requestNode.has("url") ? requestNode.get("url").asText() : null;
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }

                String definitionId = resolveRestConnectorDefinitionId(method);
                String connectorName = resolveConnectorName(restConfig, task.name);

                removeChildElementsByName(taskEl, "connectors");

                Element connectorEl = doc.createElement("connectors");
                connectorEl.setAttribute("xmi:type", "process:Connector");
                connectorEl.setAttribute("xmi:id", generateUniqueId());
                connectorEl.setAttribute("name", connectorName);
                connectorEl.setAttribute("definitionId", definitionId);
                connectorEl.setAttribute("event", "ON_ENTER");
                connectorEl.setAttribute("ignoreErrors", "true");
                connectorEl.setAttribute("definitionVersion", "1.5.0");

                Element configurationEl = doc.createElement("configuration");
                configurationEl.setAttribute("xmi:type", "connectorconfiguration:ConnectorConfiguration");
                configurationEl.setAttribute("xmi:id", generateUniqueId());
                configurationEl.setAttribute("definitionId", definitionId);
                configurationEl.setAttribute("version", "1.5.0");
                configurationEl.setAttribute("modelVersion", "9");

                configurationEl.appendChild(createConnectorParameter(doc, "url",
                        createSimpleExpression(doc, url)));

                JsonNode headersNode = requestNode.get("headers");
                String contentType = extractHeaderValue(headersNode, "Content-Type");
                String charset = null;
                String normalizedContentType = null;
                if (contentType != null && !contentType.isEmpty()) {
                    charset = extractCharset(contentType);
                    normalizedContentType = stripCharset(contentType);
                }
                if (normalizedContentType == null || normalizedContentType.isEmpty()) {
                    normalizedContentType = "application/json";
                }
                configurationEl.appendChild(createConnectorParameter(doc, "contentType",
                        createSimpleExpression(doc, normalizedContentType)));
                if (charset == null || charset.isEmpty()) {
                    charset = "UTF-8";
                }
                configurationEl.appendChild(createConnectorParameter(doc, "charset",
                        createSimpleExpression(doc, charset)));

                JsonNode bodyNode = requestNode.get("body");
                String bodyContent = serializeBody(bodyNode);
                if (bodyContent != null && !bodyContent.isEmpty()) {
                    Element bodyExpression = createPatternExpression(doc, bodyContent, dataObjectsByName);
                    configurationEl.appendChild(createConnectorParameter(doc, "body", bodyExpression));
                }

                appendDefaultRestConnectorParameters(doc, configurationEl);

                connectorEl.appendChild(configurationEl);

                Element anchor = findConnectorInsertionAnchor(taskEl);
                if (anchor != null) {
                    taskEl.insertBefore(connectorEl, anchor);
                } else {
                    taskEl.appendChild(connectorEl);
                }
            }
        }
    }

    public static void updateServiceTasksWithEmailConnectorsInDocument(Document doc,
                                                                       Map<String, String> processedEmailFiles,
                                                                       ConfigFile configFileData) {
        if (doc == null || processedEmailFiles == null || processedEmailFiles.isEmpty()) {
            return;
        }
        if (configFileData == null || configFileData.processes == null || configFileData.smtpConfig == null) {
            return;
        }

        Map<String, Element> procServiceTasksByName = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!"process:ServiceTask".equals(element.getAttribute("xmi:type"))) {
                continue;
            }
            String name = element.getAttribute("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            procServiceTasksByName.putIfAbsent(name.trim().toLowerCase(), element);
        }

        if (procServiceTasksByName.isEmpty()) {
            return;
        }

        Map<String, DataObjectInfo> dataObjectsByName = collectBusinessObjectDataByName(doc);

        for (Process process : configFileData.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }

            for (Node task : process.config.tasks) {
                if (task.emailJsonRef == null || task.emailJsonRef.trim().isEmpty()) {
                    continue;
                }
                if (task.name == null || task.name.trim().isEmpty()) {
                    continue;
                }

                Element taskEl = procServiceTasksByName.get(task.name.trim().toLowerCase());
                if (taskEl == null) {
                    continue;
                }

                String emailFileName = resolveEmailFileNameForTask(task, processedEmailFiles);
                if (emailFileName == null) {
                    continue;
                }

                String emailJson = processedEmailFiles.get(emailFileName);
                if (emailJson == null || emailJson.trim().isEmpty()) {
                    continue;
                }

                JsonNode emailConfig;
                try {
                    emailConfig = mapper.readTree(emailJson);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to parse email config '" + emailFileName + "': " + e.getMessage());
                    continue;
                }

                JsonNode headersNode = emailConfig.path("headers");
                String from = normalizeEmailExpressions(readOptionalText(headersNode, "from"));
                String to = normalizeEmailExpressions(readOptionalText(headersNode, "to"));
                String subject = normalizeEmailExpressions(readOptionalText(headersNode, "subject"));
                String cc = normalizeEmailExpressions(readOptionalText(headersNode, "cc"));
                String bcc = normalizeEmailExpressions(readOptionalText(headersNode, "bcc"));
                String replyTo = normalizeEmailExpressions(readOptionalText(headersNode, "replyTo"));

                JsonNode bodyNode = emailConfig.path("body");
                String templateRef = readOptionalText(bodyNode, "templateRef");
                Map<String, String> templateParams = readEmailTemplateParameters(bodyNode);
                String templatePath = resolveTemplatePathFromRef(task.emailFtlRef);
                if (templatePath == null) {
                    templatePath = resolveTemplatePathFromRef(templateRef);
                }
                String templateContent = readTemplateContent(templatePath);
                if (templateContent == null) {
                    System.err.println("Warning: Email template not found for task '" + task.name + "'.");
                    templateContent = "";
                }
                String message = applyTemplateParameters(templateContent, templateParams);
                message = normalizeEmailExpressions(message);

                String connectorName = resolveEmailConnectorName(emailConfig, task.name);

                removeChildElementsByName(taskEl, "connectors");

                Element connectorEl = doc.createElement("connectors");
                connectorEl.setAttribute("xmi:type", "process:Connector");
                connectorEl.setAttribute("xmi:id", generateUniqueId());
                connectorEl.setAttribute("name", connectorName);
                connectorEl.setAttribute("definitionId", "email");
                connectorEl.setAttribute("event", "ON_FINISH");
                connectorEl.setAttribute("ignoreErrors", "true");
                connectorEl.setAttribute("definitionVersion", "1.3.0");

                Element configurationEl = doc.createElement("configuration");
                configurationEl.setAttribute("xmi:type", "connectorconfiguration:ConnectorConfiguration");
                configurationEl.setAttribute("xmi:id", generateUniqueId());
                configurationEl.setAttribute("definitionId", "email");
                configurationEl.setAttribute("version", "1.3.0");
                configurationEl.setAttribute("modelVersion", "9");

                configurationEl.appendChild(createConnectorParameter(doc, "smtpHost",
                        createSimpleExpression(doc, safeString(configFileData.smtpConfig.host))));
                configurationEl.appendChild(createConnectorParameter(doc, "smtpPort",
                        createIntegerExpression(doc, configFileData.smtpConfig.port > 0
                                ? configFileData.smtpConfig.port
                                : null)));
                configurationEl.appendChild(createConnectorParameter(doc, "sslSupport",
                        createBooleanExpression(doc, false)));
                configurationEl.appendChild(createConnectorParameter(doc, "starttlsSupport",
                        createBooleanExpression(doc, true)));
                configurationEl.appendChild(createConnectorParameter(doc, "trustCertificate",
                        createBooleanExpression(doc, false)));
                configurationEl.appendChild(createConnectorParameter(doc, "authType",
                        createSimpleExpression(doc, "Basic authentication")));
                configurationEl.appendChild(createConnectorParameter(doc, "userName",
                        createSimpleExpression(doc, safeString(configFileData.smtpConfig.username))));
                configurationEl.appendChild(createConnectorParameter(doc, "password",
                        createSimpleExpression(doc, safeString(configFileData.smtpConfig.password))));
                configurationEl.appendChild(createConnectorParameter(doc, "oauth2AccessToken",
                        createEmptyExpression(doc)));

                configurationEl.appendChild(createConnectorParameter(doc, "from",
                        createEmailValueExpression(doc,
                                from == null || from.isEmpty()
                                        ? safeString(configFileData.smtpConfig.username)
                                        : from,
                                dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "returnPath",
                        createEmptyExpression(doc)));
                configurationEl.appendChild(createConnectorParameter(doc, "to",
                        createEmailValueExpression(doc, to, dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "bcc",
                        createEmailValueExpression(doc, bcc, dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "cc",
                        createEmailValueExpression(doc, cc, dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "subject",
                        createEmailValueExpression(doc, subject, dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "html",
                        createBooleanExpression(doc, isHtmlMessage(message))));
                configurationEl.appendChild(createConnectorParameter(doc, "message",
                        createEmailMessageExpression(doc, message, dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "headers",
                        createTableExpression(doc)));
                configurationEl.appendChild(createConnectorParameter(doc, "charset",
                        createSimpleExpression(doc, "UTF-8")));
                configurationEl.appendChild(createConnectorParameter(doc, "replyTo",
                        createEmailValueExpression(doc, replyTo, dataObjectsByName)));
                configurationEl.appendChild(createConnectorParameter(doc, "attachments",
                        createListExpression(doc)));

                connectorEl.appendChild(configurationEl);

                Element anchor = findConnectorInsertionAnchor(taskEl);
                if (anchor != null) {
                    taskEl.insertBefore(connectorEl, anchor);
                } else {
                    taskEl.appendChild(connectorEl);
                }
            }
        }
    }

    private static void addContractInputsForClass(Document doc, Element parentInputEl, String className,
                                                  PumlDiagram businessObjectData, Set<String> visitedClasses) {
        if (businessObjectData.classes() == null || className == null || className.isEmpty()) {
            return;
        }

        if (visitedClasses.contains(className)) {
            return;
        }
        visitedClasses.add(className);

        PumlClass pumlClass = businessObjectData.classes().get(className);
        if (pumlClass == null || pumlClass.fields() == null) {
            visitedClasses.remove(className);
            return;
        }

        for (PumlField field : pumlClass.fields()) {
            if (field.name() == null || field.name().trim().isEmpty()) {
                continue;
            }

            Element fieldInputEl = createContractInputElement(doc, field.name().trim());
            String fieldType = field.type();

            if (isClassReference(fieldType, businessObjectData)) {
                fieldInputEl.setAttribute("type", "COMPLEX");
                parentInputEl.appendChild(fieldInputEl);
                addContractInputsForClass(doc, fieldInputEl, fieldType, businessObjectData, visitedClasses);
            } else {
                String contractType = mapPumlTypeToContractType(fieldType, businessObjectData);
                if (!"TEXT".equals(contractType)) {
                    fieldInputEl.setAttribute("type", contractType);
                }
                parentInputEl.appendChild(fieldInputEl);
            }
        }

        visitedClasses.remove(className);
    }

    private static void addOperationsForClass(Document doc, Element taskEl, Element contractEl, String variableName,
                                              String className, String businessObjectTypeId,
                                              PumlDiagram businessObjectData) {
        PumlClass pumlClass = businessObjectData.classes() != null ? businessObjectData.classes().get(className) : null;
        if (pumlClass == null || pumlClass.fields() == null) {
            return;
        }

        for (PumlField field : pumlClass.fields()) {
            if (field.name() == null || field.name().trim().isEmpty()) {
                continue;
            }

            Element operationEl = doc.createElement("operations");
            operationEl.setAttribute("xmi:type", "expression:Operation");
            operationEl.setAttribute("xmi:id", generateUniqueId());

            Element leftOperand = createLeftOperandExpression(doc, variableName, className, businessObjectTypeId);
            Element rightOperand = createRightOperandExpression(doc, variableName, field, businessObjectData,
                    className, businessObjectTypeId);
            Element operator = createOperatorElement(doc, field, businessObjectData);

            operationEl.appendChild(leftOperand);
            operationEl.appendChild(rightOperand);
            operationEl.appendChild(operator);

            Element operationsAnchor = findOperationsInsertionAnchor(taskEl);
            if (operationsAnchor != null) {
                taskEl.insertBefore(operationEl, operationsAnchor);
            } else if (contractEl != null && contractEl.getParentNode() == taskEl) {
                taskEl.insertBefore(operationEl, contractEl);
            } else {
                taskEl.appendChild(operationEl);
            }
        }
    }

    private static Element createLeftOperandExpression(Document doc, String variableName, String className,
                                                       String businessObjectTypeId) {
        Element leftOperand = doc.createElement("leftOperand");
        leftOperand.setAttribute("xmi:type", "expression:Expression");
        leftOperand.setAttribute("xmi:id", generateUniqueId());
        leftOperand.setAttribute("name", variableName);
        leftOperand.setAttribute("content", variableName);
        leftOperand.setAttribute("type", "TYPE_VARIABLE");
        leftOperand.setAttribute("returnType", "com.company.model." + className);

        Element referencedEl = doc.createElement("referencedElements");
        referencedEl.setAttribute("xmi:type", "process:BusinessObjectData");
        referencedEl.setAttribute("xmi:id", generateUniqueId());
        referencedEl.setAttribute("name", variableName);
        referencedEl.setAttribute("dataType", businessObjectTypeId);
        referencedEl.setAttribute("className", "com.company.model." + className);
        leftOperand.appendChild(referencedEl);

        return leftOperand;
    }

    private static Element createRightOperandExpression(Document doc, String variableName, PumlField field,
                                                        PumlDiagram businessObjectData, String parentClassName,
                                                        String businessObjectTypeId) {
        String fieldName = field.name().trim();
        String inputVarName = variableName + "Input";
        String fieldType = field.type();
        boolean isClassRef = isClassReference(fieldType, businessObjectData);
        boolean isComposition = isClassRef;

        Element rightOperand = doc.createElement("rightOperand");
        rightOperand.setAttribute("xmi:type", "expression:Expression");
        rightOperand.setAttribute("xmi:id", generateUniqueId());
        rightOperand.setAttribute("name", inputVarName + "." + fieldName);
        rightOperand.setAttribute("interpreter", "GROOVY");
        rightOperand.setAttribute("type", "TYPE_READ_ONLY_SCRIPT");

        if (isClassRef) {
            String content = buildCompositionScript(inputVarName, fieldName, fieldType, variableName, businessObjectData);
            rightOperand.setAttribute("content", content);
            rightOperand.setAttribute("returnType", "com.company.model." + fieldType);
        } else {
            String content = buildPrimitiveRightOperandContent(inputVarName, fieldName, fieldType, businessObjectData);
            rightOperand.setAttribute("content", content);
            String returnType = mapPumlTypeToOperationReturnType(fieldType, businessObjectData);
            if (returnType != null) {
                rightOperand.setAttribute("returnType", returnType);
            }
        }

        Element referencedInputEl = doc.createElement("referencedElements");
        referencedInputEl.setAttribute("xmi:type", "process:ContractInput");
        referencedInputEl.setAttribute("xmi:id", generateUniqueId());
        referencedInputEl.setAttribute("name", inputVarName);
        referencedInputEl.setAttribute("type", "COMPLEX");
        referencedInputEl.setAttribute("createMode", "false");
        rightOperand.appendChild(referencedInputEl);

        if (isClassRef) {
            appendBusinessObjectReference(rightOperand, doc, variableName, parentClassName, businessObjectTypeId);
            appendBusinessObjectReference(rightOperand, doc, variableName, parentClassName, businessObjectTypeId);
        }

        return rightOperand;
    }

    private static Element createOperatorElement(Document doc, PumlField field, PumlDiagram businessObjectData) {
        String fieldType = field.type();
        boolean isClassRef = isClassReference(fieldType, businessObjectData);
        String inputType = mapPumlTypeToOperatorInputType(fieldType, businessObjectData, isClassRef);

        Element operatorEl = doc.createElement("operator");
        operatorEl.setAttribute("xmi:type", "expression:Operator");
        operatorEl.setAttribute("xmi:id", generateUniqueId());
        operatorEl.setAttribute("type", "JAVA_METHOD");
        operatorEl.setAttribute("expression", "set" + capitalize(field.name().trim()));

        Element inputTypesEl = doc.createElement("inputTypes");
        inputTypesEl.setTextContent(inputType);
        operatorEl.appendChild(inputTypesEl);

        return operatorEl;
    }

    private static Element createContractInputElement(Document doc, String name) {
        Element inputEl = doc.createElement("inputs");
        inputEl.setAttribute("xmi:type", "process:ContractInput");
        inputEl.setAttribute("xmi:id", generateUniqueId());
        inputEl.setAttribute("name", name);
        inputEl.setAttribute("createMode", "false");
        return inputEl;
    }

    private static Element getOrCreateContractElement(Document doc, Element taskEl) {
        Element contractEl = null;
        NodeList children = taskEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if ("contract".equals(child.getNodeName())) {
                contractEl = child;
                break;
            }
        }

        if (contractEl == null) {
            contractEl = doc.createElement("contract");
            contractEl.setAttribute("xmi:type", "process:Contract");
            contractEl.setAttribute("xmi:id", generateUniqueId());
            taskEl.appendChild(contractEl);
        } else if (!contractEl.hasAttribute("xmi:type")) {
            contractEl.setAttribute("xmi:type", "process:Contract");
        }

        return contractEl;
    }

    private static void clearElementChildren(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }

    private static void removeChildElementsByName(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if (name.equals(child.getNodeName())) {
                parent.removeChild(child);
            }
        }
    }

    private static Element findDirectChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static Map<String, String> loadFormIndex(String indexFilePath) {
        Map<String, String> formIdToUuid = new HashMap<>();
        if (indexFilePath == null || indexFilePath.trim().isEmpty()) {
            return formIdToUuid;
        }

        Path indexPath = Paths.get(indexFilePath);
        if (!Files.exists(indexPath)) {
            System.out.println("Warning: Bonita forms index file not found: " + indexFilePath);
            return formIdToUuid;
        }

        try {
            JsonNode root = mapper.readTree(indexPath.toFile());
            if (root != null && root.isObject()) {
                root.fieldNames().forEachRemaining(uuid -> {
                    JsonNode formIdNode = root.get(uuid);
                    if (formIdNode != null && formIdNode.isTextual()) {
                        formIdToUuid.put(formIdNode.asText(), uuid);
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Bonita forms index file: " + indexFilePath, e);
        }

        return formIdToUuid;
    }

    private static String extractFormPathFromRef(String formRef) {
        if (formRef == null || !formRef.startsWith("${file:") || !formRef.endsWith("}")) {
            return null;
        }
        return formRef.substring(7, formRef.length() - 1);
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx == -1) {
            return fileName;
        }
        return fileName.substring(0, idx);
    }

    private static String toFormId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "form";
        }

        String normalized = value.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return "form";
        }

        String[] parts = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (i == 0) {
                result.append(part.substring(0, 1).toLowerCase());
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            } else {
                result.append(part.substring(0, 1).toUpperCase());
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }

        return result.length() == 0 ? "form" : result.toString();
    }

    public static void updateSendTasksInDocument(Document procDocument, BpmnDefinitions bpmnDefinitions) {
        if (procDocument == null) {
            return;
        }

        NodeList elements = procDocument.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!"process:SendTask".equals(element.getAttribute("xmi:type"))) {
                continue;
            }

            // Temporary fallback:
            // Bonita send tasks are converted to a generic abstract activity representation.
            // TODO: Replace this with proper SendTask-to-Bonita mapping implementation.
            element.setAttribute("xmi:type", "process:Activity");
            element.removeAttribute("overrideActorsOfTheLane");
            element.removeAttribute("priority");
        }
    }

    private static class DataObjectInfo {
        final String className;
        final String dataType;

        private DataObjectInfo(String className, String dataType) {
            this.className = className;
            this.dataType = dataType;
        }
    }

    private static boolean isProcUserTaskType(String xmiType) {
        return "process:Task".equals(xmiType) ||
               "process:UserTask".equals(xmiType) ||
               "process:HumanTask".equals(xmiType);
    }

    private static boolean isClassReference(String type, PumlDiagram pumlDiagram) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        return pumlDiagram.classes() != null && pumlDiagram.classes().containsKey(type);
    }

    private static String mapPumlTypeToContractType(String pumlType, PumlDiagram pumlDiagram) {
        if (pumlType == null || pumlType.isEmpty()) {
            return "TEXT";
        }

        if (pumlDiagram.enums() != null && pumlDiagram.enums().containsKey(pumlType)) {
            return "TEXT";
        }

        return switch (pumlType.toLowerCase()) {
            case "string" -> "TEXT";
            case "boolean", "bool" -> "BOOLEAN";
            case "localdate" -> "LOCALDATE";
            case "localdatetime" -> "LOCALDATETIME";
            case "offsetdatetime" -> "OFFSETDATETIME";
            case "double", "float" -> "DECIMAL";
            case "integer", "int" -> "INTEGER";
            case "long", "text" -> "TEXT";
            default -> "TEXT";
        };
    }

    private static String mapPumlTypeToOperationReturnType(String pumlType, PumlDiagram pumlDiagram) {
        if (pumlType == null || pumlType.isEmpty()) {
            return null;
        }

        if (pumlDiagram.enums() != null && pumlDiagram.enums().containsKey(pumlType)) {
            return null;
        }

        return switch (pumlType.toLowerCase()) {
            case "boolean", "bool" -> "java.lang.Boolean";
            case "localdate" -> "java.time.LocalDate";
            case "localdatetime" -> "java.time.LocalDateTime";
            case "offsetdatetime" -> "java.time.OffsetDateTime";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            case "integer", "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            default -> null;
        };
    }

    private static String mapPumlTypeToOperatorInputType(String pumlType, PumlDiagram pumlDiagram,
                                                         boolean isClassRef) {
        if (isClassRef && pumlType != null && !pumlType.isEmpty()) {
            return "com.company.model." + pumlType;
        }
        if (pumlType == null || pumlType.isEmpty()) {
            return "java.lang.String";
        }
        if (pumlDiagram.enums() != null && pumlDiagram.enums().containsKey(pumlType)) {
            return "java.lang.String";
        }
        return switch (pumlType.toLowerCase()) {
            case "boolean", "bool" -> "java.lang.Boolean";
            case "localdate" -> "java.time.LocalDate";
            case "localdatetime" -> "java.time.LocalDateTime";
            case "offsetdatetime" -> "java.time.OffsetDateTime";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            case "integer", "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            default -> "java.lang.String";
        };
    }

    private static String buildPrimitiveRightOperandContent(String inputVarName, String fieldName,
                                                            String fieldType, PumlDiagram businessObjectData) {
        String safeAccess = inputVarName + "?." + fieldName;
        String directAccess = inputVarName + "." + fieldName;
        if (fieldType == null || fieldType.isEmpty()) {
            return safeAccess;
        }
        if (businessObjectData.enums() != null && businessObjectData.enums().containsKey(fieldType)) {
            return safeAccess;
        }
        return switch (fieldType.toLowerCase()) {
            case "long" -> safeAccess + "?.trim() ? " + directAccess + ".toLong() : null";
            case "float" -> safeAccess + "?.toFloat()";
            default -> safeAccess;
        };
    }

    private static String buildNestedFieldContent(String inputVarName, String parentFieldName, String nestedFieldName,
                                                  String nestedFieldType, PumlDiagram businessObjectData) {
        String safeAccess = inputVarName + "?." + parentFieldName + "?." + nestedFieldName;
        String directAccess = inputVarName + "." + parentFieldName + "." + nestedFieldName;
        if (nestedFieldType == null || nestedFieldType.isEmpty()) {
            return safeAccess;
        }
        if (businessObjectData.enums() != null && businessObjectData.enums().containsKey(nestedFieldType)) {
            return safeAccess;
        }
        return switch (nestedFieldType.toLowerCase()) {
            case "long" -> safeAccess + "?.trim() ? " + directAccess + ".toLong() : null";
            case "float" -> safeAccess + "?.toFloat()";
            default -> safeAccess;
        };
    }

    private static String buildCompositionScript(String inputVarName, String fieldName, String fieldType,
                                                 String parentVarName, PumlDiagram businessObjectData) {
        String varName = toLowerCamel(fieldType) + "Var";

        StringBuilder script = new StringBuilder();
        script.append("if (!").append(inputVarName).append("?.").append(fieldName).append(") {\n");
        script.append("\treturn null\n");
        script.append("}\n");
        script.append("def ").append(varName).append(" = ").append(parentVarName).append(".")
                .append(fieldName).append(" ?: new com.company.model.").append(fieldType).append("()\n");

        PumlClass pumlClass = businessObjectData.classes() != null
                ? businessObjectData.classes().get(fieldType)
                : null;
        if (pumlClass != null && pumlClass.fields() != null) {
            for (PumlField nestedField : pumlClass.fields()) {
                if (nestedField.name() == null || nestedField.name().trim().isEmpty()) {
                    continue;
                }
                String nestedFieldName = nestedField.name().trim();
                String nestedFieldType = nestedField.type();
                String valueExpr;
                if (isClassReference(nestedFieldType, businessObjectData)) {
                    valueExpr = inputVarName + "?." + fieldName + "?." + nestedFieldName;
                } else {
                    valueExpr = buildNestedFieldContent(inputVarName, fieldName, nestedFieldName,
                            nestedFieldType, businessObjectData);
                }
                script.append(varName).append(".").append(nestedFieldName).append(" = ")
                        .append(valueExpr).append("\n");
            }
        }

        script.append("return ").append(varName);
        return script.toString();
    }

    private static String normalizeDmnResultVariable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(2, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("~") && trimmed.endsWith("~") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.contains("~")) {
            Matcher matcher = Pattern.compile("~([^~]+)~").matcher(trimmed);
            if (matcher.find()) {
                trimmed = matcher.group(1).trim();
            }
        }
        return trimmed;
    }

    private static String resolveDmnFileKey(String dmnRef, Map<String, String> processedDmnFiles) {
        if (dmnRef == null || dmnRef.trim().isEmpty()) {
            return null;
        }
        String ref = dmnRef.trim();
        if (ref.startsWith("${file:") && ref.endsWith("}")) {
            ref = ref.substring(7, ref.length() - 1);
        }
        int lastSlash = Math.max(ref.lastIndexOf('/'), ref.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
        if (processedDmnFiles.containsKey(fileName)) {
            return fileName;
        }
        if (!fileName.toLowerCase().endsWith(".dmn")) {
            String withExt = fileName + ".dmn";
            if (processedDmnFiles.containsKey(withExt)) {
                return withExt;
            }
        }
        for (String key : processedDmnFiles.keySet()) {
            if (key.equalsIgnoreCase(fileName) || key.equalsIgnoreCase(fileName + ".dmn")) {
                return key;
            }
        }
        return null;
    }

    private static DmnDecisionTable parseDmnDecisionTable(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlContent)));
            Element root = doc.getDocumentElement();
            if (root == null) {
                return null;
            }

            String dmnNamespace = root.getNamespaceURI();
            NodeList decisionTables = dmnNamespace != null && !dmnNamespace.trim().isEmpty()
                    ? root.getElementsByTagNameNS(dmnNamespace, "decisionTable")
                    : root.getElementsByTagName("decisionTable");
            if (decisionTables.getLength() == 0) {
                return null;
            }

            Element decisionTableEl = (Element) decisionTables.item(0);
            DmnDecisionTable table = new DmnDecisionTable();

            NodeList tableChildren = decisionTableEl.getChildNodes();
            for (int i = 0; i < tableChildren.getLength(); i++) {
                if (tableChildren.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                Element child = (Element) tableChildren.item(i);
                String localName = child.getLocalName();
                if ("input".equals(localName)) {
                    DmnInput input = new DmnInput();
                    Element inputExpressionEl = firstChildByLocalName(child, "inputExpression");
                    input.expression = readDmnText(inputExpressionEl);
                    input.typeRef = inputExpressionEl != null ? inputExpressionEl.getAttribute("typeRef") : null;
                    table.inputs.add(input);
                } else if ("output".equals(localName)) {
                    DmnOutput output = new DmnOutput();
                    output.name = child.getAttribute("name");
                    output.typeRef = child.getAttribute("typeRef");
                    if (table.output == null) {
                        table.output = output;
                    }
                } else if ("rule".equals(localName)) {
                    DmnRule rule = new DmnRule();
                    NodeList ruleChildren = child.getChildNodes();
                    for (int j = 0; j < ruleChildren.getLength(); j++) {
                        if (ruleChildren.item(j).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element ruleChild = (Element) ruleChildren.item(j);
                        String ruleLocal = ruleChild.getLocalName();
                        if ("inputEntry".equals(ruleLocal)) {
                            rule.inputEntries.add(readDmnText(ruleChild));
                        } else if ("outputEntry".equals(ruleLocal)) {
                            rule.outputEntry = readDmnText(ruleChild);
                        }
                    }
                    table.rules.add(rule);
                }
            }

            return table;
        } catch (Exception e) {
            return null;
        }
    }

    private static Element firstChildByLocalName(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if (localName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    private static String readDmnText(Element parent) {
        if (parent == null) {
            return "";
        }
        Element textEl = firstChildByLocalName(parent, "text");
        if (textEl != null) {
            String text = textEl.getTextContent();
            return text != null ? text.trim() : "";
        }
        String text = parent.getTextContent();
        return text != null ? text.trim() : "";
    }

    private static String buildGroovyScript(DmnDecisionTable table, String outputType) {
        StringBuilder script = new StringBuilder();
        script.append("/*\n");
        script.append(" * Bonita Groovy script\n");
        Set<String> rootVars = new HashSet<>();
        for (DmnInput input : table.inputs) {
            String root = extractRootVariableFromExpression(input.expression);
            if (root != null && !root.isEmpty()) {
                rootVars.add(root);
            }
        }
        if (!rootVars.isEmpty()) {
            script.append(" * Expected available variable");
            if (rootVars.size() > 1) {
                script.append("s");
            }
            script.append(": ").append(String.join(", ", rootVars)).append("\n");
        }
        for (DmnInput input : table.inputs) {
            if (input.expression == null || input.expression.trim().isEmpty()) {
                continue;
            }
            String typeLabel = mapDmnTypeRefToGroovyType(input.typeRef);
            script.append(" * - ").append(input.expression.trim());
            if (typeLabel != null) {
                script.append(" (").append(typeLabel).append(")");
            }
            script.append("\n");
        }
        Set<String> outputValues = new HashSet<>();
        for (DmnRule rule : table.rules) {
            if (rule.outputEntry != null && !rule.outputEntry.trim().isEmpty()) {
                outputValues.add(rule.outputEntry.trim());
            }
        }
        if (!outputValues.isEmpty()) {
            script.append(" * Returns: ").append(String.join(" | ", outputValues)).append("\n");
        }
        script.append(" */\n\n");

        List<InputVar> inputVars = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (DmnInput input : table.inputs) {
            InputVar inputVar = new InputVar();
            inputVar.expression = input.expression != null ? input.expression.trim() : "";
            inputVar.typeRef = input.typeRef;
            inputVar.varName = deriveVariableName(inputVar.expression, usedNames);
            inputVars.add(inputVar);

            if (inputVar.expression.isEmpty()) {
                continue;
            }
            String groovyType = mapDmnTypeRefToGroovyType(inputVar.typeRef);
            String safeExpr = toSafeNavigation(inputVar.expression);
            if (groovyType == null) {
                script.append("def ").append(inputVar.varName).append(" = ").append(safeExpr).append("\n");
            } else {
                script.append(groovyType).append(" ").append(inputVar.varName)
                        .append(" = (").append(safeExpr).append(" as ").append(groovyType).append(")\n");
            }
        }

        script.append("\n");

        for (DmnRule rule : table.rules) {
            List<String> conditions = new ArrayList<>();
            for (int i = 0; i < inputVars.size(); i++) {
                String entry = i < rule.inputEntries.size() ? rule.inputEntries.get(i) : "";
                String condition = buildRuleCondition(inputVars.get(i), entry);
                if (!"true".equals(condition)) {
                    conditions.add(condition);
                }
            }
            String condition = conditions.isEmpty() ? "true" : String.join(" && ", conditions);
            String output = rule.outputEntry != null && !rule.outputEntry.trim().isEmpty()
                    ? rule.outputEntry.trim()
                    : "null";
            script.append("if (").append(condition).append(") {\n");
            script.append("    return ").append(output).append("\n");
            script.append("}\n\n");
        }

        script.append("return null\n");
        return script.toString();
    }

    private static String deriveVariableName(String expression, Set<String> usedNames) {
        String base = "value";
        if (expression != null && !expression.trim().isEmpty()) {
            String expr = expression.trim();
            int lastDot = expr.lastIndexOf('.');
            base = lastDot >= 0 ? expr.substring(lastDot + 1) : expr;
            base = base.replaceAll("[^A-Za-z0-9_]", "_");
            if (base.isEmpty()) {
                base = "value";
            }
        }
        String candidate = base;
        int counter = 1;
        while (usedNames.contains(candidate)) {
            candidate = base + counter;
            counter++;
        }
        usedNames.add(candidate);
        return candidate;
    }

    private static String toSafeNavigation(String expression) {
        if (expression == null) {
            return "";
        }
        String trimmed = expression.trim();
        if (!trimmed.contains(".")) {
            return trimmed;
        }
        return trimmed.replace(".", "?.");
    }

    private static String buildRuleCondition(InputVar inputVar, String rawTest) {
        if (rawTest == null) {
            return "true";
        }
        String test = rawTest.trim();
        if (test.isEmpty() || "-".equals(test)) {
            return "true";
        }

        String varName = inputVar.varName;
        if (test.startsWith("not(") && test.endsWith(")")) {
            String inner = test.substring(4, test.length() - 1).trim();
            String list = buildListLiteral(inner, inputVar.typeRef);
            return "!(" + list + ".contains(" + varName + "))";
        }

        if (test.contains(",") && !containsOperator(test)) {
            String list = buildListLiteral(test, inputVar.typeRef);
            return list + ".contains(" + varName + ")";
        }

        if (test.startsWith("=")) {
            return varName + " == " + test.substring(1).trim();
        }

        if (test.startsWith(">") || test.startsWith("<") || test.startsWith("!=")) {
            return varName + " " + test;
        }

        if ("true".equalsIgnoreCase(test) || "false".equalsIgnoreCase(test)) {
            return varName + " == " + test.toLowerCase();
        }

        String rangeCondition = buildRangeCondition(varName, test);
        if (rangeCondition != null) {
            return rangeCondition;
        }

        return varName + " == " + normalizeLiteral(test, inputVar.typeRef);
    }

    private static boolean containsOperator(String text) {
        return text.contains(">") || text.contains("<") || text.contains("=") || text.contains("!=");
    }

    private static String buildRangeCondition(String varName, String test) {
        Pattern rangePattern = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*\\.\\.\\s*(-?\\d+(?:\\.\\d+)?)$");
        Matcher matcher = rangePattern.matcher(test);
        if (!matcher.matches()) {
            return null;
        }
        String start = matcher.group(1);
        String end = matcher.group(2);
        return "(" + varName + " >= " + start + " && " + varName + " <= " + end + ")";
    }

    private static String buildListLiteral(String valueList, String typeRef) {
        List<String> values = splitByComma(valueList);
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(normalizeLiteral(trimmed, typeRef));
        }
        return "[" + String.join(", ", normalized) + "]";
    }

    private static List<String> splitByComma(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) {
            return parts;
        }
        String[] raw = text.split(",");
        for (String part : raw) {
            parts.add(part);
        }
        return parts;
    }

    private static String normalizeLiteral(String value, String typeRef) {
        if (value == null) {
            return "null";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "null";
        }
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            return trimmed;
        }
        String type = typeRef != null ? typeRef.trim().toLowerCase() : "";
        if ("string".equals(type)) {
            return "\"" + trimmed + "\"";
        }
        return trimmed;
    }

    private static String extractRootVariableFromExpression(String expression) {
        if (expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int dot = trimmed.indexOf('.');
        return dot > 0 ? trimmed.substring(0, dot) : trimmed;
    }

    private static String extractOutputField(String outputVar) {
        if (outputVar == null) {
            return null;
        }
        String trimmed = outputVar.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int dot = trimmed.lastIndexOf('.');
        return dot > 0 ? trimmed.substring(dot + 1) : trimmed;
    }

    private static String extractFirstRootVariable(List<DmnInput> inputs) {
        for (DmnInput input : inputs) {
            String root = extractRootVariableFromExpression(input.expression);
            if (root != null && !root.isEmpty()) {
                return root;
            }
        }
        return null;
    }

    private static String mapDmnTypeRefToGroovyType(String typeRef) {
        if (typeRef == null || typeRef.trim().isEmpty()) {
            return null;
        }
        String normalized = typeRef.trim().toLowerCase();
        switch (normalized) {
            case "string":
                return "String";
            case "integer":
                return "Integer";
            case "long":
                return "Long";
            case "boolean":
                return "Boolean";
            case "double":
            case "number":
                return "Double";
            default:
                return null;
        }
    }

    private static String mapDmnTypeRefToJava(String typeRef) {
        if (typeRef == null || typeRef.trim().isEmpty()) {
            return "java.lang.String";
        }
        String normalized = typeRef.trim().toLowerCase();
        switch (normalized) {
            case "string":
                return "java.lang.String";
            case "integer":
                return "java.lang.Integer";
            case "long":
                return "java.lang.Long";
            case "boolean":
                return "java.lang.Boolean";
            case "double":
            case "number":
                return "java.lang.Double";
            default:
                return "java.lang.String";
        }
    }

    private static Element createLeftOperandForBusinessObject(Document doc,
                                                              String variableName,
                                                              DataObjectInfo dataInfo,
                                                              String businessObjectTypeId) {
        Element leftOperand = doc.createElement("leftOperand");
        leftOperand.setAttribute("xmi:type", "expression:Expression");
        leftOperand.setAttribute("xmi:id", generateUniqueId());
        leftOperand.setAttribute("name", variableName);
        leftOperand.setAttribute("content", variableName);
        leftOperand.setAttribute("type", "TYPE_VARIABLE");
        if (dataInfo != null && dataInfo.className != null) {
            leftOperand.setAttribute("returnType", dataInfo.className);
        }

        if (dataInfo != null) {
            Element referencedEl = doc.createElement("referencedElements");
            referencedEl.setAttribute("xmi:type", "process:BusinessObjectData");
            referencedEl.setAttribute("xmi:id", generateUniqueId());
            referencedEl.setAttribute("name", variableName);
            if (dataInfo.dataType != null && !dataInfo.dataType.isEmpty()) {
                referencedEl.setAttribute("dataType", dataInfo.dataType);
            } else if (businessObjectTypeId != null && !businessObjectTypeId.isEmpty()) {
                referencedEl.setAttribute("dataType", businessObjectTypeId);
            }
            if (dataInfo.className != null && !dataInfo.className.isEmpty()) {
                referencedEl.setAttribute("className", dataInfo.className);
            }
            leftOperand.appendChild(referencedEl);
        }

        return leftOperand;
    }

    private static Element createGroovyScriptOperand(Document doc,
                                                     String script,
                                                     String returnType,
                                                     String rootVariable,
                                                     DataObjectInfo dataInfo) {
        Element rightOperand = doc.createElement("rightOperand");
        rightOperand.setAttribute("xmi:type", "expression:Expression");
        rightOperand.setAttribute("xmi:id", generateUniqueId());
        rightOperand.setAttribute("name", "newScript()");
        rightOperand.setAttribute("content", script);
        rightOperand.setAttribute("interpreter", "GROOVY");
        rightOperand.setAttribute("type", "TYPE_READ_ONLY_SCRIPT");
        if (returnType != null && !returnType.isEmpty()) {
            rightOperand.setAttribute("returnType", returnType);
        }

        if (dataInfo != null) {
            Element referencedEl = doc.createElement("referencedElements");
            referencedEl.setAttribute("xmi:type", "process:BusinessObjectData");
            referencedEl.setAttribute("xmi:id", generateUniqueId());
            referencedEl.setAttribute("name", rootVariable);
            if (dataInfo.dataType != null && !dataInfo.dataType.isEmpty()) {
                referencedEl.setAttribute("dataType", dataInfo.dataType);
            }
            if (dataInfo.className != null && !dataInfo.className.isEmpty()) {
                referencedEl.setAttribute("className", dataInfo.className);
            }
            rightOperand.appendChild(referencedEl);
        }

        return rightOperand;
    }

    private static Element createOperatorForField(Document doc, String fieldName, String inputType) {
        Element operatorEl = doc.createElement("operator");
        operatorEl.setAttribute("xmi:type", "expression:Operator");
        operatorEl.setAttribute("xmi:id", generateUniqueId());
        operatorEl.setAttribute("type", "JAVA_METHOD");
        operatorEl.setAttribute("expression", "set" + capitalize(fieldName));

        Element inputTypesEl = doc.createElement("inputTypes");
        inputTypesEl.setTextContent(inputType != null ? inputType : "java.lang.String");
        operatorEl.appendChild(inputTypesEl);

        return operatorEl;
    }

    private static class DmnDecisionTable {
        private final List<DmnInput> inputs = new ArrayList<>();
        private final List<DmnRule> rules = new ArrayList<>();
        private DmnOutput output;
    }

    private static class DmnInput {
        private String expression;
        private String typeRef;
    }

    private static class DmnOutput {
        private String name;
        private String typeRef;
    }

    private static class DmnRule {
        private final List<String> inputEntries = new ArrayList<>();
        private String outputEntry;
    }

    private static class InputVar {
        private String expression;
        private String typeRef;
        private String varName;
    }

    private static String toLowerCamel(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase() + value.substring(1);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static String findBusinessObjectTypeId(Document doc) {
        NodeList dataTypes = doc.getElementsByTagName("datatypes");
        for (int i = 0; i < dataTypes.getLength(); i++) {
            Element dataTypeEl = (Element) dataTypes.item(i);
            if ("process:BusinessObjectType".equals(dataTypeEl.getAttribute("xmi:type"))) {
                String id = dataTypeEl.getAttribute("xmi:id");
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            }
        }
        return null;
    }

    private static Element findOperationsInsertionAnchor(Element taskEl) {
        String[] orderedAnchors = {
                "loopCondition",
                "loopMaximum",
                "cardinalityExpression",
                "iteratorExpression",
                "completionCondition",
                "BoundaryIntermediateEvents",
                "formMapping",
                "contract",
                "expectedDuration"
        };

        NodeList children = taskEl.getChildNodes();
        for (String anchorName : orderedAnchors) {
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                Element child = (Element) children.item(i);
                if (anchorName.equals(child.getNodeName())) {
                    return child;
                }
            }
        }
        return null;
    }

    private static Element findConnectorInsertionAnchor(Element taskEl) {
        String[] orderedAnchors = {
                "loopCondition",
                "loopMaximum",
                "cardinalityExpression",
                "iteratorExpression",
                "completionCondition",
                "BoundaryIntermediateEvents",
                "formMapping",
                "contract",
                "expectedDuration"
        };

        NodeList children = taskEl.getChildNodes();
        for (String anchorName : orderedAnchors) {
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                Element child = (Element) children.item(i);
                if (anchorName.equals(child.getNodeName())) {
                    return child;
                }
            }
        }
        return null;
    }

    private static Element createConnectorParameter(Document doc, String key, Element expressionEl) {
        Element paramEl = doc.createElement("parameters");
        paramEl.setAttribute("xmi:type", "connectorconfiguration:ConnectorParameter");
        paramEl.setAttribute("xmi:id", generateUniqueId());
        paramEl.setAttribute("key", key);
        paramEl.appendChild(expressionEl);
        return paramEl;
    }

    private static Element createSimpleExpression(Document doc, String value) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:Expression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        expressionEl.setAttribute("name", value);
        expressionEl.setAttribute("content", value);
        expressionEl.setAttribute("returnTypeFixed", "true");
        return expressionEl;
    }

    private static Element createPatternExpression(Document doc, String value,
                                                   Map<String, DataObjectInfo> dataObjectsByName) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:Expression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        expressionEl.setAttribute("name", "<pattern-expression>");
        expressionEl.setAttribute("content", value);
        expressionEl.setAttribute("type", "TYPE_PATTERN");
        expressionEl.setAttribute("returnTypeFixed", "true");

        appendPatternReferencedElements(doc, expressionEl, value, dataObjectsByName);
        return expressionEl;
    }

    private static void appendDefaultRestConnectorParameters(Document doc, Element configurationEl) {
        Set<String> existingKeys = new HashSet<>();
        NodeList parameters = configurationEl.getElementsByTagName("parameters");
        for (int i = 0; i < parameters.getLength(); i++) {
            Element parameter = (Element) parameters.item(i);
            if ("connectorconfiguration:ConnectorParameter".equals(parameter.getAttribute("xmi:type"))) {
                String key = parameter.getAttribute("key");
                if (key != null && !key.isEmpty()) {
                    existingKeys.add(key);
                }
            }
        }

        addDefaultParameter(doc, configurationEl, existingKeys, "urlCookies", createTableExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "urlHeaders", createTableExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "add_bonita_context_headers",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "bonita_activity_instance_id_header",
                createSimpleExpression(doc, "X-Bonita-Activity-Instance-Id"));
        addDefaultParameter(doc, configurationEl, existingKeys, "bonita_process_instance_id_header",
                createSimpleExpression(doc, "X-Bonita-Process-Instance-Id"));
        addDefaultParameter(doc, configurationEl, existingKeys, "bonita_root_process_instance_id_header",
                createSimpleExpression(doc, "X-Bonita-Root-Process-Instance-Id"));
        addDefaultParameter(doc, configurationEl, existingKeys, "bonita_process_definition_id_header",
                createSimpleExpression(doc, "X-Bonita-Process-Definition-Id"));
        addDefaultParameter(doc, configurationEl, existingKeys, "bonita_task_assignee_id_header",
                createSimpleExpression(doc, "X-Bonita-Task-Assignee-Id"));
        addDefaultParameter(doc, configurationEl, existingKeys, "do_not_follow_redirect",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "ignore_body",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "fail_on_http_4xx",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "fail_on_http_5xx",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "failure_exception_codes", createTableExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "retry_on_http_5xx",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "retry_additional_codes", createTableExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "max_body_content_printed",
                createIntegerExpression(doc, 1000));
        addDefaultParameter(doc, configurationEl, existingKeys, "sensitive_headers_printed",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "TLS",
                createBooleanExpression(doc, true));
        addDefaultParameter(doc, configurationEl, existingKeys, "hostname_verifier",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "trust_store_file",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "trust_store_password",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "key_store_file",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "key_store_password",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_type",
                createSimpleExpression(doc, "NONE"));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_username",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_password",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_host",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_port",
                createIntegerExpression(doc, null));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_realm",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "auth_preemptive",
                createBooleanExpression(doc, true));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_token_endpoint",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_client_id",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_client_secret",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_scope",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_token",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_code",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_code_verifier",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "oauth2_redirect_uri",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "proxy_protocol",
                createSimpleExpression(doc, "HTTP"));
        addDefaultParameter(doc, configurationEl, existingKeys, "proxy_host",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "proxy_port",
                createIntegerExpression(doc, null));
        addDefaultParameter(doc, configurationEl, existingKeys, "proxy_username",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "proxy_password",
                createEmptyExpression(doc));
        addDefaultParameter(doc, configurationEl, existingKeys, "automatic_proxy_resolution",
                createBooleanExpression(doc, false));
        addDefaultParameter(doc, configurationEl, existingKeys, "socket_timeout_ms",
                createIntegerExpression(doc, 60000));
        addDefaultParameter(doc, configurationEl, existingKeys, "connection_timeout_ms",
                createIntegerExpression(doc, 60000));
        addDefaultParameter(doc, configurationEl, existingKeys, "trust_strategy",
                createSimpleExpression(doc, "DEFAULT"));
    }

    private static void addDefaultParameter(Document doc, Element configurationEl, Set<String> existingKeys,
                                            String key, Element expressionEl) {
        if (existingKeys.contains(key)) {
            return;
        }
        configurationEl.appendChild(createConnectorParameter(doc, key, expressionEl));
        existingKeys.add(key);
    }

    private static Element createEmptyExpression(Document doc) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:Expression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        expressionEl.setAttribute("content", "");
        expressionEl.setAttribute("returnTypeFixed", "true");
        return expressionEl;
    }

    private static Element createBooleanExpression(Document doc, boolean value) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:Expression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        String text = Boolean.toString(value);
        expressionEl.setAttribute("name", text);
        expressionEl.setAttribute("content", text);
        expressionEl.setAttribute("returnType", "java.lang.Boolean");
        expressionEl.setAttribute("returnTypeFixed", "true");
        return expressionEl;
    }

    private static Element createIntegerExpression(Document doc, Integer value) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:Expression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        if (value != null) {
            String text = Integer.toString(value);
            expressionEl.setAttribute("name", text);
            expressionEl.setAttribute("content", text);
        } else {
            expressionEl.setAttribute("content", "");
        }
        expressionEl.setAttribute("returnType", "java.lang.Integer");
        expressionEl.setAttribute("returnTypeFixed", "true");
        return expressionEl;
    }

    private static Element createTableExpression(Document doc) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:TableExpression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        return expressionEl;
    }

    private static Element createListExpression(Document doc) {
        Element expressionEl = doc.createElement("expression");
        expressionEl.setAttribute("xmi:type", "expression:ListExpression");
        expressionEl.setAttribute("xmi:id", generateUniqueId());
        return expressionEl;
    }

    private static void appendPatternReferencedElements(Document doc, Element expressionEl, String value,
                                                        Map<String, DataObjectInfo> dataObjectsByName) {
        if (value == null || value.isEmpty() || dataObjectsByName == null || dataObjectsByName.isEmpty()) {
            return;
        }

        Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
        Matcher matcher = pattern.matcher(value);
        Set<String> seenExpressions = new HashSet<>();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            if (expression.isEmpty() || !seenExpressions.add(expression)) {
                continue;
            }

            String rootVariable = extractRootVariable(expression);
            if (rootVariable == null) {
                continue;
            }

            DataObjectInfo info = dataObjectsByName.get(rootVariable);
            if (info == null) {
                continue;
            }

            Element referencedExpression = doc.createElement("referencedElements");
            referencedExpression.setAttribute("xmi:type", "expression:Expression");
            referencedExpression.setAttribute("xmi:id", generateUniqueId());
            referencedExpression.setAttribute("name", expression);
            referencedExpression.setAttribute("content", expression);
            referencedExpression.setAttribute("interpreter", "GROOVY");
            referencedExpression.setAttribute("type", "TYPE_READ_ONLY_SCRIPT");

            Element referencedVariable = doc.createElement("referencedElements");
            referencedVariable.setAttribute("xmi:type", "expression:Expression");
            referencedVariable.setAttribute("xmi:id", generateUniqueId());
            referencedVariable.setAttribute("name", rootVariable);
            referencedVariable.setAttribute("content", rootVariable);
            referencedVariable.setAttribute("type", "TYPE_VARIABLE");
            referencedVariable.setAttribute("returnType", info.className);

            Element referencedBo = doc.createElement("referencedElements");
            referencedBo.setAttribute("xmi:type", "process:BusinessObjectData");
            referencedBo.setAttribute("xmi:id", generateUniqueId());
            referencedBo.setAttribute("name", rootVariable);
            referencedBo.setAttribute("dataType", info.dataType);
            referencedBo.setAttribute("className", info.className);

            referencedVariable.appendChild(referencedBo);
            referencedExpression.appendChild(referencedVariable);
            expressionEl.appendChild(referencedExpression);
        }
    }

    private static String extractRootVariable(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)").matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String resolveConnectorName(JsonNode restConfig, String taskName) {
        if (restConfig != null && restConfig.has("id") && restConfig.get("id").isTextual()) {
            String id = restConfig.get("id").asText();
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }
        return taskName != null ? taskName : "restConnector";
    }

    private static String resolveRestConnectorDefinitionId(String method) {
        String normalized = method == null ? "POST" : method.trim().toUpperCase();
        return switch (normalized) {
            case "GET" -> "rest-get";
            case "PUT" -> "rest-put";
            case "PATCH" -> "rest-patch";
            case "DELETE" -> "rest-delete";
            case "HEAD" -> "rest-head";
            case "OPTIONS" -> "rest-options";
            default -> "rest-post";
        };
    }

    private static String extractHeaderValue(JsonNode headersNode, String headerName) {
        if (headersNode == null || !headersNode.isObject() || headerName == null) {
            return null;
        }
        String target = headerName.trim();
        if (target.isEmpty()) {
            return null;
        }

        for (java.util.Iterator<String> it = headersNode.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (field == null) {
                continue;
            }
            if (field.equalsIgnoreCase(target)) {
                JsonNode valueNode = headersNode.get(field);
                if (valueNode != null && valueNode.isTextual()) {
                    return valueNode.asText();
                }
            }
        }
        return null;
    }

    private static String extractCharset(String contentType) {
        if (contentType == null) {
            return null;
        }
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase().startsWith("charset=")) {
                return trimmed.substring("charset=".length()).trim();
            }
        }
        return null;
    }

    private static String stripCharset(String contentType) {
        if (contentType == null) {
            return null;
        }
        int idx = contentType.indexOf(';');
        if (idx == -1) {
            return contentType.trim();
        }
        return contentType.substring(0, idx).trim();
    }

    private static String serializeBody(JsonNode bodyNode) {
        if (bodyNode == null || bodyNode.isNull()) {
            return null;
        }
        if (bodyNode.isTextual()) {
            return bodyNode.asText();
        }
        try {
            return mapper.writeValueAsString(bodyNode);
        } catch (Exception e) {
            return bodyNode.toString();
        }
    }

    private static String resolveRestFileNameForTask(Node task, Map<String, String> processedRestFiles) {
        if (task == null || processedRestFiles == null || processedRestFiles.isEmpty()) {
            return null;
        }

        if (task.restCallRef != null && !task.restCallRef.trim().isEmpty()) {
            String fileName = extractFileNameFromRef(task.restCallRef);
            if (processedRestFiles.containsKey(fileName)) {
                return fileName;
            }
        }

        if (task.name == null || task.name.trim().isEmpty()) {
            return null;
        }

        String normalizedTask = normalizeRestKey(task.name);
        String candidate = normalizedTask + "_rest.json";
        if (processedRestFiles.containsKey(candidate)) {
            return candidate;
        }
        candidate = normalizedTask + ".json";
        if (processedRestFiles.containsKey(candidate)) {
            return candidate;
        }

        for (String key : processedRestFiles.keySet()) {
            String normalizedKey = normalizeRestKey(stripExtension(key));
            if (normalizedTask.equals(normalizedKey) || (normalizedTask + "_rest").equals(normalizedKey)) {
                return key;
            }
        }

        return null;
    }

    private static String resolveEmailFileNameForTask(Node task, Map<String, String> processedEmailFiles) {
        if (task == null || processedEmailFiles == null || processedEmailFiles.isEmpty()) {
            return null;
        }

        if (task.emailJsonRef != null && !task.emailJsonRef.trim().isEmpty()) {
            String fileName = extractFileNameFromRef(task.emailJsonRef);
            if (processedEmailFiles.containsKey(fileName)) {
                return fileName;
            }
        }

        if (task.name == null || task.name.trim().isEmpty()) {
            return null;
        }

        String normalizedTask = normalizeRestKey(task.name);
        String candidate = normalizedTask + "_email.json";
        if (processedEmailFiles.containsKey(candidate)) {
            return candidate;
        }
        candidate = normalizedTask + ".json";
        if (processedEmailFiles.containsKey(candidate)) {
            return candidate;
        }

        for (String key : processedEmailFiles.keySet()) {
            String normalizedKey = normalizeRestKey(stripExtension(key));
            if (normalizedTask.equals(normalizedKey) || (normalizedTask + "_email").equals(normalizedKey)) {
                return key;
            }
        }

        return null;
    }

    private static String resolveEmailConnectorName(JsonNode emailConfig, String taskName) {
        if (emailConfig != null && emailConfig.has("id") && emailConfig.get("id").isTextual()) {
            String id = emailConfig.get("id").asText();
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }
        return taskName != null ? taskName : "emailConnector";
    }

    private static String normalizeEmailExpressions(String value) {
        if (value == null || !value.contains("${~")) {
            return value;
        }
        Matcher matcher = EMAIL_EXPRESSION_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement = "${" + expression + "}";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String readOptionalText(JsonNode node, String field) {
        if (node == null || field == null || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Map<String, String> readEmailTemplateParameters(JsonNode bodyNode) {
        Map<String, String> params = new HashMap<>();
        if (bodyNode == null || !bodyNode.has("parameters")) {
            return params;
        }
        JsonNode paramsNode = bodyNode.get("parameters");
        if (paramsNode == null || !paramsNode.isObject()) {
            return params;
        }
        paramsNode.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()) {
                params.put(entry.getKey(), normalizeEmailExpressions(value.asText()));
            }
        });
        return params;
    }

    private static String resolveTemplatePathFromRef(String templateRef) {
        if (templateRef == null || templateRef.trim().isEmpty()) {
            return null;
        }

        String path = templateRef.trim();
        if (path.startsWith("${file:") && path.endsWith("}")) {
            path = path.substring(7, path.length() - 1);
        }

        Path directPath = Paths.get(path);
        if (Files.exists(directPath)) {
            return directPath.toString();
        }

        Path resourcePath = Paths.get("src/main/resources", path);
        if (Files.exists(resourcePath)) {
            return resourcePath.toString();
        }

        String fileName = extractFileNameFromRef(templateRef);
        if (fileName != null && !fileName.trim().isEmpty()) {
            Path rootCandidate = Paths.get("src/main/resources", fileName);
            if (Files.exists(rootCandidate)) {
                return rootCandidate.toString();
            }
            Path modelsEmailCandidate = Paths.get("src/main/resources/models/email", fileName);
            if (Files.exists(modelsEmailCandidate)) {
                return modelsEmailCandidate.toString();
            }
        }

        return null;
    }

    private static String readTemplateContent(String templatePath) {
        if (templatePath == null || templatePath.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(templatePath));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Warning: Failed to read email template '" + templatePath + "': " + e.getMessage());
            return null;
        }
    }

    private static String applyTemplateParameters(String templateContent, Map<String, String> templateParams) {
        if (templateContent == null) {
            return null;
        }
        String result = templateContent;
        if (templateParams != null && !templateParams.isEmpty()) {
            for (Map.Entry<String, String> entry : templateParams.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isEmpty()) {
                    continue;
                }
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("${" + key + "}", value);
            }
        }
        return result;
    }

    private static Element createEmailValueExpression(Document doc, String value,
                                                      Map<String, DataObjectInfo> dataObjectsByName) {
        if (value == null || value.trim().isEmpty()) {
            return createEmptyExpression(doc);
        }
        String normalized = normalizeEmailExpressions(value);
        if (normalized.contains("${")) {
            return createPatternExpression(doc, normalized, dataObjectsByName);
        }
        return createSimpleExpression(doc, normalized);
    }

    private static Element createEmailMessageExpression(Document doc, String message,
                                                        Map<String, DataObjectInfo> dataObjectsByName) {
        if (message == null || message.trim().isEmpty()) {
            return createEmptyExpression(doc);
        }
        String normalized = normalizeEmailExpressions(message);
        return createPatternExpression(doc, normalized, dataObjectsByName);
    }

    private static boolean isHtmlMessage(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        return trimmed.contains("<") && trimmed.contains(">");
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeRestKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized;
    }

    private static String extractFileNameFromRef(String fileRef) {
        if (fileRef == null || !fileRef.startsWith("${file:") || !fileRef.endsWith("}")) {
            return fileRef;
        }
        String path = fileRef.substring(7, fileRef.length() - 1);
        int lastSlashIndex = path.lastIndexOf('/');
        return lastSlashIndex >= 0 ? path.substring(lastSlashIndex + 1) : path;
    }

    private static void appendBusinessObjectReference(Element parent, Document doc, String variableName,
                                                      String className, String businessObjectTypeId) {
        Element referencedBoEl = doc.createElement("referencedElements");
        referencedBoEl.setAttribute("xmi:type", "process:BusinessObjectData");
        referencedBoEl.setAttribute("xmi:id", generateUniqueId());
        referencedBoEl.setAttribute("name", variableName);
        referencedBoEl.setAttribute("dataType", businessObjectTypeId);
        referencedBoEl.setAttribute("className", "com.company.model." + className);
        parent.appendChild(referencedBoEl);
    }

    /**
     * Converts ISO8601 duration to milliseconds.
     * Supports durations like PT5M, P1D, PT1H30M, etc.
     *
     * @param iso8601Duration the ISO8601 duration string
     * @return milliseconds as long
     */
    private static long convertISO8601DurationToMillis(String iso8601Duration) {
        try {
            Duration duration = Duration.parse(iso8601Duration);
            return duration.toMillis();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert ISO8601 duration to milliseconds: " + iso8601Duration, e);
        }
    }

    /**
     * Converts milliseconds to human-readable time format (HH:MM:SS).
     *
     * @param milliseconds the time in milliseconds
     * @return formatted time string
     */
    private static String convertMillisToReadableTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    public static void writeProcDocument(Document doc, String outputFilePath) {
        try {
            if (doc != null && doc.getDocumentElement() != null) {
                doc.getDocumentElement().setAttribute(
                        "xmlns:connectorconfiguration",
                        "http://www.bonitasoft.org/model/connector/configuration");
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter stringWriter = new StringWriter();
            DOMSource source = new DOMSource(doc);
            StreamResult stringResult = new StreamResult(stringWriter);
            transformer.transform(source, stringResult);

            String xmlContent = stringWriter.toString();
            
            // Remove standalone attribute from XML declaration
            xmlContent = xmlContent.replaceAll(" standalone=\"yes\"", "");
            xmlContent = xmlContent.replaceAll(" standalone=\"no\"", "");
            
            // Remove extra blank lines (consecutive newlines)
            xmlContent = xmlContent.replaceAll("(\r?\n)\\s*\r?\n", "$1");

            Files.write(Paths.get(outputFilePath), xmlContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write Proc document: " + outputFilePath, e);
        }
    }

    /**
     * Sets the initiator attribute for the actor whose lane contains a start event.
     * Finds the lane containing a start event, gets its actor attribute, and sets initiator="true" on that actor.
     *
     * @param doc the proc Document
     */
    public static void setInitiatorForProcess(Document doc) {
        if (doc == null) {
            return;
        }

        // Find all lane elements
        NodeList allElements = doc.getElementsByTagName("elements");
        
        String initiatorActorId = null;
        
        // First pass: Find the lane that contains a start event
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String xmiType = element.getAttribute("xmi:type");
            
            if ("process:Lane".equals(xmiType)) {
                // Check if this lane contains a start event
                if (laneContainsStartEvent(element)) {
                    // Get the actor attribute of this lane
                    initiatorActorId = element.getAttribute("actor");
                    if (initiatorActorId != null && !initiatorActorId.isEmpty()) {
                        break; // Found the initiator, stop searching
                    }
                }
            }
        }
        
        if (initiatorActorId == null) {
            return; // No start event found in any lane
        }
        
        // Second pass: Find the actor element with the matching ID and set initiator="true"
        NodeList actorElements = doc.getElementsByTagName("actors");
        for (int i = 0; i < actorElements.getLength(); i++) {
            Element element = (Element) actorElements.item(i);
            String xmiType = element.getAttribute("xmi:type");
            
            if ("process:Actor".equals(xmiType)) {
                String actorId = element.getAttribute("xmi:id");
                if (initiatorActorId.equals(actorId)) {
                    element.setAttribute("initiator", "true");
                    return; // Done, stop searching
                }
            }
        }
    }

    /**
     * Checks if a lane element contains a start event.
     * Looks through direct child elements to find any event with "Start" in their xmi:type.
     *
     * @param laneElement the lane Element
     * @return true if the lane contains a start event, false otherwise
     */
    private static boolean laneContainsStartEvent(Element laneElement) {
        NodeList children = laneElement.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            
            Element child = (Element) children.item(i);
            String nodeName = child.getNodeName();
            
            // Only check direct child elements named "elements"
            if ("elements".equals(nodeName)) {
                String xmiType = child.getAttribute("xmi:type");
                
                if (xmiType != null && xmiType.contains("Start")) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
