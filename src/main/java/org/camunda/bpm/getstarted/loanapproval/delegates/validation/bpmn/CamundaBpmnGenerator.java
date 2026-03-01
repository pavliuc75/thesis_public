package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.Lane;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
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
import java.util.Map;

public class CamundaBpmnGenerator {
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    /**
     * Copies a BPMN file to an output directory.
     *
     * @param sourceBpmnFilePath the source BPMN file path
     * @param outputDirPath      the output directory path
     * @return the path to the copied BPMN file
     * @throws RuntimeException if file operations fail
     */
    public static String copyBpmnFileToOutput(String sourceBpmnFilePath, String outputDirPath) {
        try {
            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            // Get source file name and create output path
            Path sourcePath = Paths.get(sourceBpmnFilePath);
            String fileName = sourcePath.getFileName().toString();
            Path outputPath = outputDir.resolve(fileName);

            // Copy source file to output directory
            Files.copy(sourcePath, outputPath, StandardCopyOption.REPLACE_EXISTING);

            return outputPath.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy BPMN file: " + sourceBpmnFilePath, e);
        }
    }

    /**
     * Parses a BPMN XML file into a Document object.
     *
     * @param bpmnFilePath the path to the BPMN file
     * @return the parsed Document
     * @throws RuntimeException if parsing fails
     */
    public static Document parseBpmnDocument(String bpmnFilePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new File(bpmnFilePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BPMN document: " + bpmnFilePath, e);
        }
    }

    /**
     * Updates lane elements in the BPMN document with vendor attributes from
     * BpmnDefinitions.
     */
    public static void updateLaneAttributesInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // Get all lane elements in the document
        NodeList laneNodes = doc.getElementsByTagNameNS(BPMN_NS, "lane");

        for (int i = 0; i < laneNodes.getLength(); i++) {
            Element laneEl = (Element) laneNodes.item(i);
            String laneId = laneEl.getAttribute("id");

            // Find matching lane in BpmnDefinitions
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.laneSet() == null) {
                    continue;
                }

                for (Lane lane : process.laneSet().lanes()) {
                    if (lane.id().equals(laneId)) {
                        // Update lane element with vendor attributes
                        for (Map.Entry<String, String> attr : lane.vendorAttributes().entrySet()) {
                            String attrName = attr.getKey();
                            String attrValue = attr.getValue();

                            // Handle namespace attributes (e.g., "camunda:assignee")
                            if (attrName.contains(":")) {
                                String prefix = attrName.substring(0, attrName.indexOf(":"));

                                // Set namespace-aware attribute
                                String namespaceUri = doc.lookupNamespaceURI(prefix);
                                if (namespaceUri != null) {
                                    laneEl.setAttributeNS(namespaceUri, attrName, attrValue);
                                } else {
                                    // If namespace not found, set as regular attribute
                                    laneEl.setAttribute(attrName, attrValue);
                                }
                            } else {
                                // Regular attribute
                                laneEl.setAttribute(attrName, attrValue);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Updates lane elements in the BPMN document with camunda:assignee attribute
     * from the resolvedActor field in BpmnDefinitions.
     */
    public static void updateLanesWithResolvedActorInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // Get all lane elements in the document
        NodeList laneNodes = doc.getElementsByTagNameNS(BPMN_NS, "lane");

        for (int i = 0; i < laneNodes.getLength(); i++) {
            Element laneEl = (Element) laneNodes.item(i);
            String laneId = laneEl.getAttribute("id");

            // Find matching lane in BpmnDefinitions
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.laneSet() == null) {
                    continue;
                }

                for (Lane lane : process.laneSet().lanes()) {
                    if (lane.id().equals(laneId)) {
                        // Update lane element with camunda:assignee from resolvedActor
                        String resolvedActor = lane.resolvedActor();
                        if (resolvedActor != null && !resolvedActor.isEmpty()) {
                            String camundaNamespaceUri = doc.lookupNamespaceURI("camunda");
                            if (camundaNamespaceUri != null) {
                                laneEl.setAttributeNS(camundaNamespaceUri, "camunda:assignee", resolvedActor);
                            } else {
                                // If namespace not found, set as regular attribute
                                laneEl.setAttribute("camunda:assignee", resolvedActor);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Updates userTask elements in the BPMN document with camunda:assignee attribute
     * from the resolvedActor field in BpmnDefinitions.
     */
    public static void updateUserTasksWithResolvedActorInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // Get all userTask elements in the document
        NodeList userTaskNodes = doc.getElementsByTagNameNS(BPMN_NS, "userTask");

        for (int i = 0; i < userTaskNodes.getLength(); i++) {
            Element userTaskEl = (Element) userTaskNodes.item(i);
            String taskId = userTaskEl.getAttribute("id");

            // Find matching FlowNode in BpmnDefinitions
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.nodesById() == null) {
                    continue;
                }

                FlowNode flowNode = process.nodesById().get(taskId);
                if (flowNode != null && "userTask".equals(flowNode.type())) {
                    // Update userTask element with camunda:assignee from resolvedActor
                    String resolvedActor = flowNode.resolvedActor();
                    if (resolvedActor != null && !resolvedActor.isEmpty()) {
                        String camundaNamespaceUri = doc.lookupNamespaceURI("camunda");
                        if (camundaNamespaceUri != null) {
                            userTaskEl.setAttributeNS(camundaNamespaceUri, "camunda:assignee", resolvedActor);
                        } else {
                            // If namespace not found, set as regular attribute
                            userTaskEl.setAttribute("camunda:assignee", resolvedActor);
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Updates userTask elements in the BPMN document with camunda:formKey attribute
     * from resolvedFormRef.
     */
    public static void updateUserTasksFormKeyInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // Get all userTask elements in the document
        NodeList userTaskNodes = doc.getElementsByTagNameNS(BPMN_NS, "userTask");

        for (int i = 0; i < userTaskNodes.getLength(); i++) {
            Element userTaskEl = (Element) userTaskNodes.item(i);
            String taskId = userTaskEl.getAttribute("id");

            // Find matching FlowNode in BpmnDefinitions
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.nodesById() == null) {
                    continue;
                }

                FlowNode flowNode = process.nodesById().get(taskId);
                if (flowNode != null && "userTask".equals(flowNode.type())) {
                    // Update userTask element with formKey from resolvedFormRef
                    String formKey = flowNode.resolvedFormRef();
                    if (formKey != null && !formKey.isEmpty()) {
                        // Set camunda:formKey attribute
                        String prefix = "camunda";
                        String namespaceUri = doc.lookupNamespaceURI(prefix);
                        if (namespaceUri != null) {
                            userTaskEl.setAttributeNS(namespaceUri, "camunda:formKey", formKey);
                        } else {
                            // If namespace not found, set as regular attribute
                            userTaskEl.setAttribute("camunda:formKey", formKey);
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Updates all flow node elements in the BPMN document with vendor attributes
     * (like camunda:assignee, camunda:class) from vendorAttributes map.
     */
    public static void updateFlowNodesVendorAttributesInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // List of BPMN flow node types to update
        String[] flowNodeTypes = {
                "userTask", "serviceTask", "scriptTask", "businessRuleTask", "sendTask", "receiveTask", "manualTask",
                "startEvent", "endEvent", "intermediateThrowEvent", "intermediateCatchEvent", "boundaryEvent",
                "exclusiveGateway", "inclusiveGateway", "parallelGateway", "eventBasedGateway", "complexGateway"
        };

        // Process each flow node type
        for (String nodeType : flowNodeTypes) {
            NodeList flowNodeElements = doc.getElementsByTagNameNS(BPMN_NS, nodeType);

            for (int i = 0; i < flowNodeElements.getLength(); i++) {
                Element flowNodeEl = (Element) flowNodeElements.item(i);
                String nodeId = flowNodeEl.getAttribute("id");

                // Find matching FlowNode in BpmnDefinitions
                for (ProcessDef process : bpmnDefinitions.processes()) {
                    if (process.nodesById() == null) {
                        continue;
                    }

                    FlowNode flowNode = process.nodesById().get(nodeId);
                    if (flowNode != null) {
                        // Update flow node element with vendor attributes
                        if (flowNode.vendorAttributes() == null || flowNode.vendorAttributes().isEmpty()) {
                            break;
                        }
                        for (Map.Entry<String, String> attr : flowNode.vendorAttributes().entrySet()) {
                            String attrName = attr.getKey();
                            String attrValue = attr.getValue();

                            if (attrName == null || !attrName.contains("camunda")) {
                                continue;
                            }

                            // Skip formKey as it's handled separately by updateUserTasksFormKeyInDocument
                            if ("camunda:formKey".equals(attrName)) {
                                continue;
                            }

                            // Handle namespace attributes (e.g., "camunda:assignee", "camunda:class")
                            if (attrName.contains(":")) {
                                String attrPrefix = attrName.substring(0, attrName.indexOf(":"));

                                // Set namespace-aware attribute
                                String attrNamespaceUri = doc.lookupNamespaceURI(attrPrefix);
                                if (attrNamespaceUri != null) {
                                    flowNodeEl.setAttributeNS(attrNamespaceUri, attrName, attrValue);
                                } else {
                                    // If namespace not found, set as regular attribute
                                    flowNodeEl.setAttribute(attrName, attrValue);
                                }
                            } else {
                                // Regular attribute
                                flowNodeEl.setAttribute(attrName, attrValue);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Updates task elements in the BPMN document with camunda:delegateExpression attribute
     * for tasks that have resolvedEmailConfigFileName set.
     * The delegateExpression will be in the format: "${beanName}"
     */
    public static void updateTasksWithEmailDelegateExpressionInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        NodeList serviceTaskNodes = doc.getElementsByTagNameNS(BPMN_NS, "serviceTask");

        // Process serviceTasks
        for (int i = 0; i < serviceTaskNodes.getLength(); i++) {
            Element taskEl = (Element) serviceTaskNodes.item(i);
            String taskId = taskEl.getAttribute("id");
            updateTaskWithEmailDelegateExpression(taskEl, taskId, doc, bpmnDefinitions);
        }
    }

    /**
     * Helper method to update a single task element with delegateExpression and input parameters
     * if it has resolvedEmailConfigFileName or resolvedEmailTemplateFileName.
     */
    private static void updateTaskWithEmailDelegateExpression(Element taskEl, String taskId, Document doc, BpmnDefinitions bpmnDefinitions) {
        // Find matching FlowNode in BpmnDefinitions
        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                continue;
            }

            FlowNode flowNode = process.nodesById().get(taskId);
            if (flowNode != null) {
                String configFileName = flowNode.resolvedEmailConfigFileName();
                String templateFileName = flowNode.resolvedEmailTemplateFileName();

                // Only proceed if at least one email file is configured
                if ((configFileName != null && !configFileName.isEmpty()) ||
                        (templateFileName != null && !templateFileName.isEmpty())) {

                    // Set camunda:delegateExpression
                    String camundaPrefix = "camunda";
                    String camundaNamespaceUri = doc.lookupNamespaceURI(camundaPrefix);
                    if (camundaNamespaceUri == null) {
                        camundaNamespaceUri = "http://camunda.org/schema/1.0/bpmn";
                    }

                    taskEl.setAttributeNS(camundaNamespaceUri, "camunda:delegateExpression", "${sendEmailDelegate}");

                    // Get or create extensionElements
                    Element extensionElements = getOrCreateExtensionElements(taskEl, doc);

                    // Create camunda:inputOutput element
                    Element inputOutput = doc.createElementNS(camundaNamespaceUri, "camunda:inputOutput");
                    extensionElements.appendChild(inputOutput);

                    // Add input parameters
                    if (configFileName != null && !configFileName.isEmpty()) {
                        Element configParam = doc.createElementNS(camundaNamespaceUri, "camunda:inputParameter");
                        configParam.setAttribute("name", "configJson");
                        configParam.setTextContent(configFileName);
                        inputOutput.appendChild(configParam);
                    }

                    if (templateFileName != null && !templateFileName.isEmpty()) {
                        Element templateParam = doc.createElementNS(camundaNamespaceUri, "camunda:inputParameter");
                        templateParam.setAttribute("name", "templateFtl");
                        templateParam.setTextContent(templateFileName);
                        inputOutput.appendChild(templateParam);
                    }
                }
                break;
            }
        }
    }

    /**
     * Gets or creates the extensionElements element for a given task element.
     * If created, it will be inserted as the first child of the task element.
     */
    private static Element getOrCreateExtensionElements(Element taskEl, Document doc) {
        // Check if extensionElements already exists
        NodeList extensionElementsList = taskEl.getElementsByTagNameNS(BPMN_NS, "extensionElements");
        if (extensionElementsList.getLength() > 0) {
            return (Element) extensionElementsList.item(0);
        }

        // Create new extensionElements and insert as first child
        Element extensionElements = doc.createElementNS(BPMN_NS, "bpmn:extensionElements");
        
        // Insert as first child instead of appending at the end
        if (taskEl.hasChildNodes()) {
            taskEl.insertBefore(extensionElements, taskEl.getFirstChild());
        } else {
            taskEl.appendChild(extensionElements);
        }
        
        return extensionElements;
    }

    /**
     * Updates task elements in the BPMN document with camunda:delegateExpression attribute
     * for tasks that have resolvedRestCallFileName set.
     * The delegateExpression will be in the format: "${restCallDelegate}"
     */
    public static void updateTasksWithRestCallDelegateExpressionInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        NodeList serviceTaskNodes = doc.getElementsByTagNameNS(BPMN_NS, "serviceTask");

        // Process serviceTasks
        for (int i = 0; i < serviceTaskNodes.getLength(); i++) {
            Element taskEl = (Element) serviceTaskNodes.item(i);
            String taskId = taskEl.getAttribute("id");
            updateTaskWithRestCallDelegateExpression(taskEl, taskId, doc, bpmnDefinitions);
        }
    }

    /**
     * Helper method to update a single task element with delegateExpression and input parameters
     * if it has resolvedRestCallFileName.
     */
    private static void updateTaskWithRestCallDelegateExpression(Element taskEl, String taskId, Document doc, BpmnDefinitions bpmnDefinitions) {
        // Find matching FlowNode in BpmnDefinitions
        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                continue;
            }

            FlowNode flowNode = process.nodesById().get(taskId);
            if (flowNode != null) {
                String restCallFileName = flowNode.resolvedRestCallFileName();

                // Only proceed if REST call file is configured
                if (restCallFileName != null && !restCallFileName.isEmpty()) {

                    // Set camunda:delegateExpression
                    String camundaPrefix = "camunda";
                    String camundaNamespaceUri = doc.lookupNamespaceURI(camundaPrefix);
                    if (camundaNamespaceUri == null) {
                        camundaNamespaceUri = "http://camunda.org/schema/1.0/bpmn";
                    }

                    taskEl.setAttributeNS(camundaNamespaceUri, "camunda:delegateExpression", "${restCallDelegate}");

                    // Get or create extensionElements
                    Element extensionElements = getOrCreateExtensionElements(taskEl, doc);

                    // Create camunda:inputOutput element
                    Element inputOutput = doc.createElementNS(camundaNamespaceUri, "camunda:inputOutput");
                    extensionElements.appendChild(inputOutput);

                    // Add input parameter for REST call config file
                    Element restCallParam = doc.createElementNS(camundaNamespaceUri, "camunda:inputParameter");
                    restCallParam.setAttribute("name", "restCallConfig");
                    restCallParam.setTextContent(restCallFileName);
                    inputOutput.appendChild(restCallParam);
                }
                break;
            }
        }
    }

    /**
     * Updates businessRuleTask elements in the BPMN document with DMN attributes.
     * Sets camunda:decisionRef, camunda:resultVariable, and camunda:mapDecisionResult="singleEntry".
     */
    public static void updateBusinessRuleTaskTasksWithDmnAttributesInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        NodeList businessRuleTaskNodes = doc.getElementsByTagNameNS(BPMN_NS, "businessRuleTask");

        for (int i = 0; i < businessRuleTaskNodes.getLength(); i++) {
            Element taskEl = (Element) businessRuleTaskNodes.item(i);
            String taskId = taskEl.getAttribute("id");

            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.nodesById() == null) {
                    continue;
                }

                FlowNode flowNode = process.nodesById().get(taskId);
                if (flowNode == null) {
                    continue;
                }

                String dmnRef = flowNode.dmnRef();
                String dmnResultVariable = flowNode.dmnResultVariable();
                boolean hasDmnRef = dmnRef != null && !dmnRef.isEmpty();
                boolean hasResultVariable = dmnResultVariable != null && !dmnResultVariable.isEmpty();

                if (!hasDmnRef && !hasResultVariable) {
                    break;
                }

                String camundaPrefix = "camunda";
                String camundaNamespaceUri = doc.lookupNamespaceURI(camundaPrefix);
                if (camundaNamespaceUri == null) {
                    camundaNamespaceUri = "http://camunda.org/schema/1.0/bpmn";
                }

                if (hasDmnRef) {
                    taskEl.setAttributeNS(camundaNamespaceUri, "camunda:decisionRef", dmnRef);
                }
                if (hasResultVariable) {
                    taskEl.setAttributeNS(camundaNamespaceUri, "camunda:resultVariable", dmnResultVariable);
                }

                taskEl.setAttributeNS(camundaNamespaceUri, "camunda:mapDecisionResult", "singleEntry");
                break;
            }
        }
    }

    /**
     * Generates Camunda-specific attributes in the BPMN document root element.
     * Updates the definitions element with proper namespaces and Camunda modeler attributes.
     * Also sets default process attributes like isExecutable and historyTimeToLive.
     *
     * @param doc the BPMN Document to update
     */
    public static void generateCamundaSpecificAttributes(Document doc) {
        Element definitionsEl = doc.getDocumentElement();
        
        // Set all required namespace declarations
        definitionsEl.setAttribute("xmlns:bpmn", "http://www.omg.org/spec/BPMN/20100524/MODEL");
        definitionsEl.setAttribute("xmlns:bpmndi", "http://www.omg.org/spec/BPMN/20100524/DI");
        definitionsEl.setAttribute("xmlns:camunda", "http://camunda.org/schema/1.0/bpmn");
        definitionsEl.setAttribute("xmlns:dc", "http://www.omg.org/spec/DD/20100524/DC");
        definitionsEl.setAttribute("xmlns:di", "http://www.omg.org/spec/DD/20100524/DI");
        definitionsEl.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        definitionsEl.setAttribute("xmlns:modeler", "http://camunda.org/schema/modeler/1.0");
        
        // Set Camunda modeler attributes
        definitionsEl.setAttribute("exporter", "Camunda Modeler");
        definitionsEl.setAttribute("exporterVersion", "5.27.0");
        definitionsEl.setAttributeNS("http://camunda.org/schema/modeler/1.0", "modeler:executionPlatform", "Camunda Platform");
        definitionsEl.setAttributeNS("http://camunda.org/schema/modeler/1.0", "modeler:executionPlatformVersion", "7.24.0");
        
        // Set targetNamespace if not already set
        if (!definitionsEl.hasAttribute("targetNamespace")) {
            definitionsEl.setAttribute("targetNamespace", "http://bpmn.io/schema/bpmn");
        }
        
        // Update all process elements with Camunda-specific attributes
        NodeList processNodes = doc.getElementsByTagNameNS(BPMN_NS, "process");
        String camundaNamespaceUri = "http://camunda.org/schema/1.0/bpmn";
        
        for (int i = 0; i < processNodes.getLength(); i++) {
            Element processEl = (Element) processNodes.item(i);
            
            // Set isExecutable="true" if not already set
            if (!processEl.hasAttribute("isExecutable")) {
                processEl.setAttribute("isExecutable", "true");
            }
            
            // Set camunda:historyTimeToLive="P180D" if not already set
            if (!processEl.hasAttributeNS(camundaNamespaceUri, "historyTimeToLive")) {
                processEl.setAttributeNS(camundaNamespaceUri, "camunda:historyTimeToLive", "P180D");
            }
        }
    }

    /**
     * Writes a BPMN Document to a file.
     *
     * @param doc            the Document to write
     * @param outputFilePath the output file path
     * @throws RuntimeException if writing fails
     */
    public static void writeBpmnDocument(Document doc, String outputFilePath) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            // Transform to StringWriter first to process the output
            StringWriter stringWriter = new StringWriter();
            DOMSource source = new DOMSource(doc);
            StreamResult stringResult = new StreamResult(stringWriter);
            transformer.transform(source, stringResult);

            // Remove extra blank lines (consecutive newlines)
            String xmlContent = stringWriter.toString();
            xmlContent = xmlContent.replaceAll("(\r?\n)\\s*\r?\n", "$1");

            // Replace the XML declaration and opening definitions tag with exact Camunda format
            // Find the end of the opening <bpmn:definitions...> tag or <definitions...> tag
            int definitionsEndIndex = xmlContent.indexOf(">", xmlContent.indexOf("<bpmn:definitions"));
            if (definitionsEndIndex == -1) {
                definitionsEndIndex = xmlContent.indexOf(">", xmlContent.indexOf("<definitions"));
            }
            
            if (definitionsEndIndex != -1) {
                // Replace everything from start to the end of the opening definitions tag
                String replacementHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
                    "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" " +
                    "xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\" " +
                    "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" " +
                    "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" " +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                    "xmlns:modeler=\"http://camunda.org/schema/modeler/1.0\" " +
                    "id=\"Definitions_18xz33h\" " +
                    "targetNamespace=\"http://bpmn.io/schema/bpmn\" " +
                    "exporter=\"Camunda Modeler\" " +
                    "exporterVersion=\"5.27.0\" " +
                    "modeler:executionPlatform=\"Camunda Platform\" " +
                    "modeler:executionPlatformVersion=\"7.24.0\">\n" +
                    "  <bpmn:collaboration id=\"Collaboration_1ca5n1y\">";
                
                // Find where collaboration tag starts in the original content
                int collaborationStartIndex = xmlContent.indexOf("<bpmn:collaboration");
                if (collaborationStartIndex == -1) {
                    collaborationStartIndex = xmlContent.indexOf("<collaboration");
                }
                
                if (collaborationStartIndex != -1) {
                    // Find the end of the collaboration opening tag
                    int collaborationEndIndex = xmlContent.indexOf(">", collaborationStartIndex);
                    if (collaborationEndIndex != -1) {
                        // Replace from start to end of collaboration opening tag
                        xmlContent = replacementHeader + xmlContent.substring(collaborationEndIndex + 1);
                    }
                } else {
                    // No collaboration tag found, just replace up to definitions tag
                    xmlContent = replacementHeader.substring(0, replacementHeader.lastIndexOf("<bpmn:collaboration")) + 
                                xmlContent.substring(definitionsEndIndex + 1);
                }
            }

            // Write processed content to file
            Files.write(Paths.get(outputFilePath), xmlContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write BPMN document: " + outputFilePath, e);
        }
    }

    /**
     * Updates sequence flow expressions in the BPMN document with resolvedExpression values.
     * Replaces the content of conditionExpression elements with the resolved expression.
     *
     * @param doc             the BPMN Document
     * @param bpmnDefinitions the BPMN definitions containing sequence flows with resolved expressions
     */
    public static void updateSequenceFlowsExpressionsInDocument(Document doc, BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return;
        }

        // Get all sequenceFlow elements in the document
        NodeList sequenceFlowNodes = doc.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");

        for (int i = 0; i < sequenceFlowNodes.getLength(); i++) {
            Element sequenceFlowEl = (Element) sequenceFlowNodes.item(i);
            String flowId = sequenceFlowEl.getAttribute("id");

            // Find matching SequenceFlow in BpmnDefinitions
            for (ProcessDef process : bpmnDefinitions.processes()) {
                if (process.flowsById() == null) {
                    continue;
                }

                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow sequenceFlow = 
                    process.flowsById().get(flowId);
                
                if (sequenceFlow != null && sequenceFlow.resolvedExpression() != null && 
                    !sequenceFlow.resolvedExpression().trim().isEmpty()) {
                    
                    // Find or create conditionExpression element
                    NodeList conditionNodes = sequenceFlowEl.getElementsByTagNameNS(BPMN_NS, "conditionExpression");
                    Element conditionEl;
                    
                    if (conditionNodes.getLength() > 0) {
                        // Update existing conditionExpression
                        conditionEl = (Element) conditionNodes.item(0);
                        conditionEl.setTextContent(sequenceFlow.resolvedExpression());
                    } else {
                        // Create new conditionExpression element if it doesn't exist
                        conditionEl = doc.createElementNS(BPMN_NS, "bpmn:conditionExpression");
                        conditionEl.setAttribute("xsi:type", "bpmn:tFormalExpression");
                        conditionEl.setTextContent(sequenceFlow.resolvedExpression());
                        sequenceFlowEl.appendChild(conditionEl);
                    }
                    
                    break;
                }
            }
        }
    }

    public static void updateSendTasksInDocument(Document bpmnDocument, BpmnDefinitions bpmnDefinitions) {
        // TODO
    }
}
