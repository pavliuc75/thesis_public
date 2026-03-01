package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.Collaboration;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.Lane;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.LaneSet;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.Actor;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.OrganizationData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.Role;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.ProcessConfigHelper;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Process;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Variable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BpmnHelper {
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    /**
     * Gets all data inputs for a specific flow node.
     * Returns the actual DataObjectReference objects, not just the associations.
     *
     * @param flowNode the flow node to get inputs for
     * @param process  the process containing the data object references
     * @return list of data object references that are inputs to this node
     */
    public static List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> getFlowNodeInputs(
            FlowNode flowNode,
            ProcessDef process) {

        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> inputs = new ArrayList<>();

        for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataInputAssociation inputAssoc : flowNode
                .dataInputAssociations()) {
            if (inputAssoc.sourceRef() != null) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObj = process
                        .dataObjectsById().get(inputAssoc.sourceRef());
                if (dataObj != null) {
                    inputs.add(dataObj);
                }
            }
        }

        return inputs;
    }

    /**
     * Gets all data outputs for a specific flow node.
     * Returns the actual DataObjectReference objects, not just the associations.
     *
     * @param flowNode the flow node to get outputs for
     * @param process  the process containing the data object references
     * @return list of data object references that are outputs from this node
     */
    public static List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> getFlowNodeOutputs(
            FlowNode flowNode,
            ProcessDef process) {

        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> outputs = new ArrayList<>();

        for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation outputAssoc : flowNode
                .dataOutputAssociations()) {
            if (outputAssoc.targetRef() != null) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObj = process
                        .dataObjectsById().get(outputAssoc.targetRef());
                if (dataObj != null) {
                    outputs.add(dataObj);
                }
            }
        }

        return outputs;
    }

    /**
     * Parses a BPMN file and returns a BpmnDefinitions object instance.
     *
     * @param bpmnFilePath the path to the BPMN file
     * @return BpmnDefinitions object containing the parsed BPMN structure
     * @throws RuntimeException if parsing fails
     */
    public static BpmnData parseBpmnFile(String bpmnFilePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(bpmnFilePath));

            // Get root definitions element
            Element definitionsEl = doc.getDocumentElement();
            if (!definitionsEl.getLocalName().equals("definitions")) {
                throw new RuntimeException("Root element is not 'definitions'");
            }

            String id = definitionsEl.getAttribute("id");
            String targetNamespace = definitionsEl.getAttribute("targetNamespace");

            // Parse collaboration (if present)
            Collaboration collaboration = null;
            NodeList collaborationNodes = doc.getElementsByTagNameNS(BPMN_NS, "collaboration");
            if (collaborationNodes.getLength() > 0) {
                collaboration = new Collaboration();
            }

            // Parse all processes
            List<ProcessDef> processes = new ArrayList<>();
            NodeList processNodes = doc.getElementsByTagNameNS(BPMN_NS, "process");
            for (int i = 0; i < processNodes.getLength(); i++) {
                Element processEl = (Element) processNodes.item(i);
                ProcessDef process = parseProcess(processEl);
                processes.add(process);
            }

            return new BpmnData(id, targetNamespace, collaboration, processes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BPMN file: " + bpmnFilePath, e);
        }
    }

    /**
     * Parses a process element into a ProcessDef object.
     */
    private static ProcessDef parseProcess(Element processEl) {
        String id = processEl.getAttribute("id");
        String name = processEl.getAttribute("name");
        String isExecutableStr = processEl.getAttribute("isExecutable");
        boolean isExecutable = "true".equalsIgnoreCase(isExecutableStr);

        // Parse laneSet (if present)
        LaneSet laneSet = null;
        NodeList laneSetNodes = processEl.getElementsByTagNameNS(BPMN_NS, "laneSet");
        if (laneSetNodes.getLength() > 0) {
            Element laneSetEl = (Element) laneSetNodes.item(0);
            laneSet = parseLaneSet(laneSetEl);
        }

        // Parse data object references
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjectsById = parseDataObjectReferences(
                processEl);

        // Parse all flow nodes (tasks, events, gateways)
        Map<String, FlowNode> nodesById = parseFlowNodes(processEl);

        // Parse all sequence flows
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> flowsById = parseSequenceFlows(processEl);

        return new ProcessDef(id, name, isExecutable, laneSet, nodesById, dataObjectsById, flowsById);
    }

    /**
     * Parses a laneSet element into a LaneSet object.
     */
    private static LaneSet parseLaneSet(Element laneSetEl) {
        String id = laneSetEl.getAttribute("id");

        // Parse all lanes
        List<Lane> lanes = new ArrayList<>();
        NodeList laneNodes = laneSetEl.getElementsByTagNameNS(BPMN_NS, "lane");
        for (int i = 0; i < laneNodes.getLength(); i++) {
            Element laneEl = (Element) laneNodes.item(i);
            Lane lane = parseLane(laneEl);
            lanes.add(lane);
        }

        return new LaneSet(id, lanes);
    }

    /**
     * Parses a lane element into a Lane object.
     */
    private static Lane parseLane(Element laneEl) {
        String id = laneEl.getAttribute("id");
        String name = laneEl.getAttribute("name");

        // Parse all flowNodeRef elements
        List<String> flowNodeRefs = new ArrayList<>();
        NodeList flowNodeRefNodes = laneEl.getElementsByTagNameNS(BPMN_NS, "flowNodeRef");
        for (int i = 0; i < flowNodeRefNodes.getLength(); i++) {
            Element flowNodeRefEl = (Element) flowNodeRefNodes.item(i);
            String refText = flowNodeRefEl.getTextContent();
            if (refText != null && !refText.trim().isEmpty()) {
                flowNodeRefs.add(refText.trim());
            }
        }

        return new Lane(id, name, flowNodeRefs);
    }

    /**
     * Parses data object references from a process element.
     */
    private static Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> parseDataObjectReferences(
            Element processEl) {
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference> dataObjectsById = new HashMap<>();

        NodeList dataObjectRefNodes = processEl.getElementsByTagNameNS(BPMN_NS, "dataObjectReference");
        for (int i = 0; i < dataObjectRefNodes.getLength(); i++) {
            Element dataObjRefEl = (Element) dataObjectRefNodes.item(i);
            String id = dataObjRefEl.getAttribute("id");
            String name = dataObjRefEl.getAttribute("name");
            String dataObjectRef = dataObjRefEl.getAttribute("dataObjectRef");

            if (id != null && !id.isEmpty()) {
                // Use factory method to parse the name string into components
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef = org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference
                        .parse(id, name, dataObjectRef);
                dataObjectsById.put(id, dataObjRef);
            }
        }

        return dataObjectsById;
    }

    /**
     * Parses all flow nodes (tasks, events, gateways) from a process element.
     */
    private static Map<String, FlowNode> parseFlowNodes(Element processEl) {
        Map<String, FlowNode> nodesById = new HashMap<>();

        // List of BPMN flow node types to parse
        String[] flowNodeTypes = {
                "userTask", "serviceTask", "scriptTask", "businessRuleTask", "sendTask", "receiveTask", "manualTask",
                "startEvent", "endEvent", "intermediateThrowEvent", "intermediateCatchEvent", "boundaryEvent",
                "exclusiveGateway", "inclusiveGateway", "parallelGateway", "eventBasedGateway", "complexGateway"
        };

        for (String nodeType : flowNodeTypes) {
            NodeList nodes = processEl.getElementsByTagNameNS(BPMN_NS, nodeType);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element nodeEl = (Element) nodes.item(i);
                String nodeId = nodeEl.getAttribute("id");
                String nodeName = nodeEl.getAttribute("name");

                if (nodeId != null && !nodeId.isEmpty()) {
                    // Parse data input associations
                    List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataInputAssociation> dataInputs = parseDataInputAssociations(
                            nodeEl);

                    // Parse data output associations
                    List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation> dataOutputs = parseDataOutputAssociations(
                            nodeEl);

                    // Parse ISO8601 time for timer events
                    String iso8601time = null;
                    if (nodeType.contains("Event")) {
                        iso8601time = extractTimerEventDuration(nodeEl);
                    }

                    FlowNode flowNode = FlowNode.builder()
                            .id(nodeId)
                            .type(nodeType)
                            .name(nodeName)
                            .vendorAttributes(new HashMap<>())
                            .dataInputAssociations(dataInputs)
                            .dataOutputAssociations(dataOutputs)
                            .iso8601time(iso8601time)
                            .build();
                    nodesById.put(nodeId, flowNode);
                }
            }
        }

        return nodesById;
    }

    /**
     * Parses all sequence flows from a process element.
     *
     * @param processEl the process element
     * @return a map of sequence flow ID to SequenceFlow object
     */
    private static Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> parseSequenceFlows(Element processEl) {
        Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> flowsById = new HashMap<>();

        NodeList sequenceFlowNodes = processEl.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < sequenceFlowNodes.getLength(); i++) {
            Element flowEl = (Element) sequenceFlowNodes.item(i);
            String flowId = flowEl.getAttribute("id");
            String flowName = flowEl.getAttribute("name");

            // Extract condition expression if present
            String expression = null;
            NodeList conditionNodes = flowEl.getElementsByTagNameNS(BPMN_NS, "conditionExpression");
            if (conditionNodes.getLength() > 0) {
                Element conditionEl = (Element) conditionNodes.item(0);
                expression = conditionEl.getTextContent();
            }

            if (flowId != null && !flowId.isEmpty()) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow sequenceFlow =
                        new org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow(
                                flowId,
                                flowName,
                                expression,
                                null
                        );
                flowsById.put(flowId, sequenceFlow);
            }
        }

        return flowsById;
    }

    /**
     * Extracts ISO8601 duration from a timer event element.
     * Only supports timeDuration (not timeDate or timeCycle).
     * Validates that the duration follows ISO8601 format.
     *
     * @param eventEl the event element
     * @return the ISO8601 duration string, or null if not found
     * @throws IllegalStateException if duration is invalid
     */
    private static String extractTimerEventDuration(Element eventEl) {
        // Look for timerEventDefinition
        NodeList timerEventDefs = eventEl.getElementsByTagNameNS(BPMN_NS, "timerEventDefinition");
        if (timerEventDefs.getLength() == 0) {
            return null;
        }

        Element timerEventDef = (Element) timerEventDefs.item(0);

        // Only support timeDuration (ISO8601 duration like PT5M, P1D, PT1H30M)
        NodeList timeDurationNodes = timerEventDef.getElementsByTagNameNS(BPMN_NS, "timeDuration");
        if (timeDurationNodes.getLength() > 0) {
            Element timeDurationEl = (Element) timeDurationNodes.item(0);
            String duration = timeDurationEl.getTextContent();
            if (duration != null && !duration.trim().isEmpty()) {
                String trimmedDuration = duration.trim();

                // Validate ISO8601 duration format
                if (!isValidISO8601Duration(trimmedDuration)) {
                    throw new IllegalStateException(
                            String.format("Invalid ISO8601 duration format for timer event '%s': '%s'. " +
                                            "Expected format: P[n]Y[n]M[n]DT[n]H[n]M[n]S or P[n]W (e.g., PT5M, P1D, PT1H30M)",
                                    eventEl.getAttribute("id"), trimmedDuration));
                }

                return trimmedDuration;
            }
        }

        return null;
    }

    /**
     * Validates if a string is a valid ISO8601 duration format.
     * Valid formats: P[n]Y[n]M[n]DT[n]H[n]M[n]S or P[n]W
     * Examples: PT5M, P1D, PT1H30M, P1DT2H30M, P2W
     *
     * @param duration the duration string to validate
     * @return true if valid ISO8601 duration, false otherwise
     */
    private static boolean isValidISO8601Duration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return false;
        }

        // ISO8601 duration must start with 'P'
        if (!duration.startsWith("P")) {
            return false;
        }

        // Simple regex pattern for ISO8601 duration
        // P[n]Y[n]M[n]DT[n]H[n]M[n]S or P[n]W
        String iso8601Pattern = "^P(?:\\d+Y)?(?:\\d+M)?(?:\\d+D)?(?:T(?:\\d+H)?(?:\\d+M)?(?:\\d+(?:\\.\\d+)?S)?)?$|^P\\d+W$";

        boolean matches = duration.matches(iso8601Pattern);

        // Additional check: if there's a 'T', make sure there's at least one time component after it
        if (matches && duration.contains("T")) {
            String timePart = duration.substring(duration.indexOf("T") + 1);
            if (timePart.isEmpty()) {
                return false;
            }
        }

        // If there's a 'T', there should be something before it or it should be at least 'PT'
        if (matches && duration.contains("T") && duration.equals("PT")) {
            return false;
        }

        return matches;
    }

    /**
     * Parses data input associations from a flow node element.
     */
    private static List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataInputAssociation> parseDataInputAssociations(
            Element nodeEl) {
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataInputAssociation> dataInputs = new ArrayList<>();

        NodeList dataInputNodes = nodeEl.getElementsByTagNameNS(BPMN_NS, "dataInputAssociation");
        for (int i = 0; i < dataInputNodes.getLength(); i++) {
            Element dataInputEl = (Element) dataInputNodes.item(i);
            String id = dataInputEl.getAttribute("id");

            // Get sourceRef
            String sourceRef = null;
            NodeList sourceRefNodes = dataInputEl.getElementsByTagNameNS(BPMN_NS, "sourceRef");
            if (sourceRefNodes.getLength() > 0) {
                Element sourceRefEl = (Element) sourceRefNodes.item(0);
                sourceRef = sourceRefEl.getTextContent();
            }

            // Get targetRef
            String targetRef = null;
            NodeList targetRefNodes = dataInputEl.getElementsByTagNameNS(BPMN_NS, "targetRef");
            if (targetRefNodes.getLength() > 0) {
                Element targetRefEl = (Element) targetRefNodes.item(0);
                targetRef = targetRefEl.getTextContent();
            }

            if (id != null && !id.isEmpty()) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataInputAssociation dataInput = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataInputAssociation(
                        id, sourceRef, targetRef);
                dataInputs.add(dataInput);
            }
        }

        return dataInputs;
    }

    /**
     * Parses data output associations from a flow node element.
     */
    private static List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation> parseDataOutputAssociations(
            Element nodeEl) {
        List<org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation> dataOutputs = new ArrayList<>();

        NodeList dataOutputNodes = nodeEl.getElementsByTagNameNS(BPMN_NS, "dataOutputAssociation");
        for (int i = 0; i < dataOutputNodes.getLength(); i++) {
            Element dataOutputEl = (Element) dataOutputNodes.item(i);
            String id = dataOutputEl.getAttribute("id");

            // Get targetRef
            String targetRef = null;
            NodeList targetRefNodes = dataOutputEl.getElementsByTagNameNS(BPMN_NS, "targetRef");
            if (targetRefNodes.getLength() > 0) {
                Element targetRefEl = (Element) targetRefNodes.item(0);
                targetRef = targetRefEl.getTextContent();
            }

            if (id != null && !id.isEmpty()) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation dataOutput = new org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation(
                        id, targetRef);
                dataOutputs.add(dataOutput);
            }
        }

        return dataOutputs;
    }

    /**
     * Resolves an assignee reference (e.g., "${ref:organization.alice}") to the
     * actual actor value.
     *
     * @param assigneeRef    the assignee reference string
     * @param archimateRoles the list of roles from archimate data
     * @return the resolved actor name or id, or null if not found
     */
    public static String resolveAssignee(String assigneeRef, List<Role> archimateRoles) {
        if (assigneeRef == null || assigneeRef.trim().isEmpty()) {
            return null;
        }

        // Pattern to match references like "${ref:organization.alice}" and extract
        // "alice"
        Pattern refPattern = Pattern.compile("\\$\\{ref:organization\\.([^}]+)\\}");
        Matcher matcher = refPattern.matcher(assigneeRef);

        if (matcher.matches()) {
            String entityName = matcher.group(1); // Extract "alice" from "${ref:organization.alice}"

            // Find the actor in archimate roles
            for (Role role : archimateRoles) {
                for (Actor actor : role.getActors()) {
                    if (entityName.equals(actor.getName()) || entityName.equals(actor.getId())) {
                        // Return the actor's name (preferred) or id as fallback
                        return actor.getName() != null && !actor.getName().isEmpty()
                                ? actor.getName()
                                : actor.getId();
                    }
                }
            }
        }

        // If it doesn't match the pattern, return as-is (might be a literal value)
        return assigneeRef;
    }

    /**
     * Updates BpmnDefinitions with resolved Camunda assignees from the config file.
     * Matches lanes by name between config and BPMN processes, resolves assignee
     * references,
     * and updates the corresponding BPMN lanes.
     *
     * @param bpmnDefinitions the BPMN definitions to update
     * @param configFile        the config file containing lane assignees
     * @param organizationData  the organization data (archimate roles) for resolving
     *                          references
     * @return updated BpmnDefinitions with resolved assignees
     */
    public static BpmnData updateLanesWithAssignees(
            BpmnData bpmnDefinitions,
            ConfigFile configFile,
            OrganizationData organizationData) {

        List<Role> archimateRoles = organizationData != null ? organizationData.getArchimateRoles() : null;

        if (bpmnDefinitions == null || configFile == null || configFile.processes == null) {
            return bpmnDefinitions;
        }

        // Create updated processes list
        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef bpmnProcess : bpmnDefinitions.processes()) {
            // Find matching config process by ID
            Process configProcess = ProcessConfigHelper.findProcessById(configFile, bpmnProcess.id());

            if (configProcess == null || configProcess.config == null || configProcess.config.lanes == null) {
                // No config for this process, keep as-is
                updatedProcesses.add(bpmnProcess);
                continue;
            }

            // Update laneSet with resolved assignees
            LaneSet updatedLaneSet = null;
            if (bpmnProcess.laneSet() != null) {
                List<Lane> updatedLanes = new ArrayList<>();

                for (Lane bpmnLane : bpmnProcess.laneSet().lanes()) {
                    // Find matching config lane by name
                    org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Lane configLane = configProcess.config.lanes
                            .stream()
                            .filter(l -> bpmnLane.name() != null && bpmnLane.name().equals(l.name))
                            .findFirst()
                            .orElse(null);

                    // Start with existing vendor attributes or create new map
                    Map<String, String> vendorAttributes = new HashMap<>(bpmnLane.vendorAttributes());
                    String resolvedActor = null;

                    if (configLane != null) {
                        // Resolve assignee if present in config
                        if (configLane.assignee != null) {
                            resolvedActor = resolveAssignee(configLane.assignee, archimateRoles);
                        }

                        // Copy vendor-specific attributes directly from config to Lane (not to FlowNodes)
                        if (configLane.vendorSpecificAttributes != null) {
                            vendorAttributes.putAll(configLane.vendorSpecificAttributes);
                        }
                    }

                    // Create updated lane with resolved actor
                    Lane updatedLane = new Lane(
                            bpmnLane.id(),
                            bpmnLane.name(),
                            bpmnLane.flowNodeRefs(),
                            vendorAttributes,
                            resolvedActor);
                    updatedLanes.add(updatedLane);
                }

                updatedLaneSet = new LaneSet(bpmnProcess.laneSet().id(), updatedLanes);
            }

            // Create updated process with updated laneSet
            ProcessDef updatedProcess = new ProcessDef(
                    bpmnProcess.id(),
                    bpmnProcess.name(),
                    bpmnProcess.isExecutable(),
                    updatedLaneSet,
                    bpmnProcess.nodesById(),
                    bpmnProcess.dataObjectsById(),
                    bpmnProcess.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses);
    }

    /**
     * Updates userTask nodes with camunda:assignee from their containing lanes.
     * For each lane, finds all userTask nodes referenced in flowNodeRefs and copies
     * the lane's camunda:assignee to those task nodes.
     *
     * @param bpmnDefinitions the BPMN definitions (should already have lanes
     *                        updated with assignees)
     * @return updated BpmnDefinitions with userTask nodes having assignees from
     * their lanes
     */
    public static BpmnData updateUserTasksWithLaneAssignees(BpmnData bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.laneSet() == null || process.nodesById() == null) {
                // No lanes or nodes to process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Create updated nodes map
            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            // For each lane, propagate assignee to userTask nodes in that lane
            for (Lane lane : process.laneSet().lanes()) {
                String laneAssignee = lane.resolvedActor();

                if (laneAssignee == null || laneAssignee.isEmpty()) {
                    continue; // No assignee to propagate
                }

                // Find all nodes referenced in this lane
                for (String nodeId : lane.flowNodeRefs()) {
                    FlowNode node = updatedNodes.get(nodeId);

                    if (node != null && "userTask".equals(node.type())) {
                        // Create updated node with assignee from lane
                        FlowNode updatedNode = node.toBuilder()
                                .resolvedActor(laneAssignee)
                                .build();
                        updatedNodes.put(nodeId, updatedNode);
                    }
                }
            }

            // Create updated process with updated nodes
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses);
    }

    /**
     * Updates userTask nodes with formKey references from the config file and the
     * output variable name from the first data output association.
     * For each user task with a formRef in the config, finds the corresponding
     * FlowNode
     * in BPMN and sets the resolvedFormRef field with the converted formKey and the
     * resolvedFormOutputVariableName field with the output variable name from the
     * first data output association.
     *
     * @param bpmnDefinitions the BPMN definitions to update
     * @param configFile      the config file containing task formRef definitions
     * @return updated BpmnDefinitions with userTask nodes having resolvedFormRef
     * set
     */
    public static BpmnData updateUserTasksWithFormRefsAndResolvedFormOutputVariableName(
            BpmnData bpmnDefinitions,
            ConfigFile configFile) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        if (configFile == null || configFile.processes == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                // No nodes to process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Find matching config process by ID
            Process configProcess = ProcessConfigHelper.findProcessById(configFile, process.id());

            if (configProcess == null || configProcess.config == null || configProcess.config.tasks == null) {
                // No config for this process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Create updated nodes map
            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            // For each task with formRef in config, find and update the corresponding
            // FlowNode
            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node configTask : configProcess.config.tasks) {
                if (configTask.formRef == null || configTask.formRef.trim().isEmpty()) {
                    continue; // Skip tasks without form references
                }

                // Find matching FlowNode by name
                FlowNode matchingNode = process.nodesById().values().stream()
                        .filter(node -> "userTask".equals(node.type())
                                && configTask.name != null
                                && configTask.name.equals(node.name()))
                        .findFirst()
                        .orElse(null);

                if (matchingNode != null) {
                    // Extract form path and convert to HTML form key format
                    String formKey = convertFormRefToFormKey(configTask.formRef);

                    if (formKey != null) {
                        // Get the output variable name from the first data output association
                        String outputVariableName = null;
                        if (matchingNode.dataOutputAssociations() != null
                                && !matchingNode.dataOutputAssociations().isEmpty()) {
                            // Get the first data output association
                            org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataOutputAssociation firstOutput = matchingNode
                                    .dataOutputAssociations().get(0);
                            if (firstOutput.targetRef() != null) {
                                // Look up the data object reference by ID
                                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.DataObjectReference dataObjRef = process
                                        .dataObjectsById().get(firstOutput.targetRef());
                                if (dataObjRef != null) {
                                    outputVariableName = dataObjRef.variableName();
                                }
                            }
                        }

                        // Create updated node with formKey in resolvedFormRef and typeName in
                        // resolvedFormOutputVariableName
                        FlowNode updatedNode = matchingNode.toBuilder()
                                .resolvedFormRef(formKey)
                                .resolvedFormOutputVariableName(outputVariableName)
                                .build();
                        updatedNodes.put(matchingNode.id(), updatedNode);
                    }
                }
            }

            // Create updated process with updated nodes
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses);
    }

    /**
     * Converts a formRef from config (e.g.,
     * "${file:forms/request_absence_form.json}")
     * to a Camunda formKey format (e.g.,
     * "embedded:app:forms/request_absence_form.html").
     *
     * @param formRef the form reference from config
     * @return the formKey string for Camunda, or null if conversion fails
     */
    private static String convertFormRefToFormKey(String formRef) {
        if (formRef == null || !formRef.startsWith("${file:") || !formRef.endsWith("}")) {
            return null;
        }

        // Extract path: "${file:forms/request_absence_form.json}" ->
        // "forms/request_absence_form.json"
        String formPath = formRef.substring(7, formRef.length() - 1);

        // Extract filename and change extension from .json to .html
        String fileName = formPath.substring(formPath.lastIndexOf('/') + 1);
        String htmlFileName = fileName.replace(".json", ".html");

        // Build formKey: "embedded:app:forms/{filename}.html"
        return "embedded:app:forms/" + htmlFileName;
    }

    /**
     * Extracts the filename from a file reference string.
     * Expected format: "${file:emails/remind-line-manager-email.yml}" -> "remind-line-manager-email.yml"
     *
     * @param fileRef the file reference string
     * @return the extracted filename, or the original string if the format is invalid
     */
    private static String extractFileNameFromRef(String fileRef) {
        if (fileRef == null || !fileRef.startsWith("${file:") || !fileRef.endsWith("}")) {
            return fileRef; // Return original if format is invalid
        }
        // Extract path between "${file:" and "}"
        String path = fileRef.substring(7, fileRef.length() - 1);
        // Extract filename (part after last "/")
        int lastSlashIndex = path.lastIndexOf('/');
        return lastSlashIndex >= 0 ? path.substring(lastSlashIndex + 1) : path;
    }

    /**
     * Updates FlowNode objects with resolvedEmailConfigFileName for tasks that have emailJsonRef.
     * The bean name is generated from the task name in camelCase format.
     *
     * @param bpmnDefinitions the BPMN definitions to update
     * @param configFile      the config file containing task emailJsonRef definitions
     * @return updated BpmnDefinitions with FlowNode objects having resolvedEmailConfigFileName set
     */
    public static BpmnData updateTasksWithEmailDelegateBeanName(
            BpmnData bpmnDefinitions,
            ConfigFile configFile) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        if (configFile == null || configFile.processes == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                // No nodes to process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Find matching config process by ID
            Process configProcess = ProcessConfigHelper.findProcessById(configFile, process.id());

            if (configProcess == null || configProcess.config == null || configProcess.config.tasks == null) {
                // No config for this process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Create updated nodes map
            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            // For each task with emailJsonRef in config, find and update the corresponding FlowNode
            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node configTask : configProcess.config.tasks) {
                if (configTask.emailJsonRef == null || configTask.emailJsonRef.trim().isEmpty()) {
                    continue; // Skip tasks without email references
                }

                // Find matching FlowNode by name
                FlowNode matchingNode = process.nodesById().values().stream()
                        .filter(node -> configTask.name != null
                                && configTask.name.equals(node.name()))
                        .findFirst()
                        .orElse(null);

                if (matchingNode != null) {
                    // Create updated node with resolvedEmailConfigFileName
                    FlowNode updatedNode = matchingNode.toBuilder()
                            .resolvedEmailConfigFileName(extractFileNameFromRef(configTask.emailJsonRef))
                            .resolvedEmailTemplateFileName(extractFileNameFromRef(configTask.emailFtlRef))
                            .build();
                    updatedNodes.put(matchingNode.id(), updatedNode);
                }
            }

            // Create updated process with updated nodes
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Updates FlowNode objects with resolvedRestCallFileName for tasks that have restCallRef.
     * Similar to updateTasksWithEmailDelegateBeanName but for REST call references.
     *
     * @param bpmnDefinitions the BPMN definitions to update
     * @param configFile      the config file containing task restCallRef definitions
     * @return updated BpmnDefinitions with FlowNode objects having resolvedRestCallFileName set
     */
    public static BpmnData updateTasksWithRestCallDelegateBeanName(
            BpmnData bpmnDefinitions,
            ConfigFile configFile) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        if (configFile == null || configFile.processes == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                // No nodes to process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Find matching config process by ID
            Process configProcess = ProcessConfigHelper.findProcessById(configFile, process.id());

            if (configProcess == null || configProcess.config == null || configProcess.config.tasks == null) {
                // No config for this process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Create updated nodes map
            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            // For each task with restCallRef in config, find and update the corresponding FlowNode
            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node configTask : configProcess.config.tasks) {
                if (configTask.restCallRef == null || configTask.restCallRef.trim().isEmpty()) {
                    continue; // Skip tasks without REST call references
                }

                // Find matching FlowNode by name
                FlowNode matchingNode = process.nodesById().values().stream()
                        .filter(node -> configTask.name != null
                                && configTask.name.equals(node.name()))
                        .findFirst()
                        .orElse(null);

                if (matchingNode != null) {
                    // Create updated node with resolvedRestCallFileName
                    FlowNode updatedNode = matchingNode.toBuilder()
                            .resolvedRestCallFileName(extractFileNameFromRef(configTask.restCallRef))
                            .build();
                    updatedNodes.put(matchingNode.id(), updatedNode);
                }
            }

            // Create updated process with updated nodes
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Updates businessRuleTask flow nodes with DMN references and result variable names from config.
     * Only nodes of type "businessRuleTask" are considered.
     * The DMN reference and result variable are copied as-is from the config file.
     *
     * @param bpmnDefinitions the BPMN definitions to update
     * @param configFile      the config file containing task DMN references
     * @return updated BpmnDefinitions with FlowNode objects having dmnRef and dmnResultVariable set
     */
    public static BpmnData updateFlowNodesWithDmnRefAndDmnResultVariable(
            BpmnData bpmnDefinitions,
            ConfigFile configFile) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        if (configFile == null || configFile.processes == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                // No nodes to process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Find matching config process by ID
            Process configProcess = ProcessConfigHelper.findProcessById(configFile, process.id());

            if (configProcess == null || configProcess.config == null || configProcess.config.tasks == null) {
                // No config for this process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Create updated nodes map
            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            // For each task with dmnRef/dmnResultVariable in config, find and update the corresponding FlowNode
            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node configTask : configProcess.config.tasks) {
                boolean hasDmnRef = configTask.dmnRef != null && !configTask.dmnRef.trim().isEmpty();
                boolean hasDmnResultVar = configTask.dmnResultVariable != null
                        && !configTask.dmnResultVariable.trim().isEmpty();
                if (!hasDmnRef && !hasDmnResultVar) {
                    continue; // Skip tasks without DMN references
                }

                // Find matching FlowNode by name and type
                FlowNode matchingNode = process.nodesById().values().stream()
                        .filter(node -> "businessRuleTask".equals(node.type())
                                && configTask.name != null
                                && configTask.name.equals(node.name()))
                        .findFirst()
                        .orElse(null);

                if (matchingNode != null) {
                    // Create updated node with dmnRef and dmnResultVariable from config (as-is)
                    FlowNode updatedNode = matchingNode.toBuilder()
                            .dmnRef(configTask.dmnRef)
                            .dmnResultVariable(configTask.dmnResultVariable)
                            .build();
                    updatedNodes.put(matchingNode.id(), updatedNode);
                }
            }

            // Create updated process with updated nodes
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Updates FlowNode objects with vendor-specific attributes from configFileData.
     * Adds vendor_specific_attributes from:
     * - config.tasks[].vendor_specific_attributes → FlowNodes (matched by name)
     * - config.events[].vendor_specific_attributes → FlowNodes (matched by name)
     * - config.lanes[].vendor_specific_attributes → FlowNodes in those lanes (via flowNodeRefs)
     *
     * @param bpmnDefinitions the BPMN definitions to update
     * @param configFile      the config file containing vendor_specific_attributes
     * @return updated BpmnDefinitions with FlowNode objects having vendor attributes set
     */
    public static BpmnData updateFlowNodesWithVendorAttributes(
            BpmnData bpmnDefinitions,
            ConfigFile configFile) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        if (configFile == null || configFile.processes == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null) {
                // No nodes to process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Find matching config process by ID
            Process configProcess = ProcessConfigHelper.findProcessById(configFile, process.id());

            if (configProcess == null || configProcess.config == null) {
                // No config for this process, keep as-is
                updatedProcesses.add(process);
                continue;
            }

            // Create updated nodes map
            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            // Process tasks vendor_specific_attributes
            if (configProcess.config.tasks != null) {
                for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node configTask : configProcess.config.tasks) {
                    if (configTask.name == null || configTask.vendorSpecificAttributes == null || configTask.vendorSpecificAttributes.isEmpty()) {
                        continue;
                    }

                    // Find matching FlowNode by name
                    FlowNode matchingNode = process.nodesById().values().stream()
                            .filter(node -> configTask.name.equals(node.name()))
                            .findFirst()
                            .orElse(null);

                    if (matchingNode != null) {
                        // Merge vendor attributes (convert Object to String)
                        Map<String, String> updatedVendorAttributes = new HashMap<>(matchingNode.vendorAttributes());
                        for (Map.Entry<String, Object> entry : configTask.vendorSpecificAttributes.entrySet()) {
                            updatedVendorAttributes.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }

                        // Create updated node with vendor attributes
                        FlowNode updatedNode = matchingNode.toBuilder()
                                .vendorAttributes(updatedVendorAttributes)
                                .build();
                        updatedNodes.put(matchingNode.id(), updatedNode);
                    }
                }
            }

            // Process events vendor_specific_attributes
            if (configProcess.config.events != null) {
                for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node configEvent : configProcess.config.events) {
                    if (configEvent.name == null || configEvent.vendorSpecificAttributes == null || configEvent.vendorSpecificAttributes.isEmpty()) {
                        continue;
                    }

                    // Find matching FlowNode by name
                    FlowNode matchingNode = process.nodesById().values().stream()
                            .filter(node -> configEvent.name.equals(node.name()))
                            .findFirst()
                            .orElse(null);

                    if (matchingNode != null) {
                        // Merge vendor attributes (convert Object to String)
                        Map<String, String> updatedVendorAttributes = new HashMap<>(matchingNode.vendorAttributes());
                        for (Map.Entry<String, Object> entry : configEvent.vendorSpecificAttributes.entrySet()) {
                            updatedVendorAttributes.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }

                        // Create updated node with vendor attributes
                        FlowNode updatedNode = matchingNode.toBuilder()
                                .vendorAttributes(updatedVendorAttributes)
                                .build();
                        updatedNodes.put(matchingNode.id(), updatedNode);
                    }
                }
            }

            // Note: Lane vendor_specific_attributes are handled by updateLanesWithAssignees() and set on Lane elements only.
            // Only assignee is propagated from lanes to userTasks (via updateUserTasksWithLaneAssignees()).

            // Create updated process with updated nodes
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById());
            updatedProcesses.add(updatedProcess);
        }

        // Return updated BpmnDefinitions
        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Resolves global variables in sequence flow expressions.
     * Replaces ~globalVariables.xxx~ with the actual value from config.
     * Modifies the expression field directly.
     *
     * @param bpmnDefinitions the BPMN definitions
     * @param configFileData  the configuration file data containing global variables
     * @return updated BPMN definitions with resolved global variables in expression field
     */
    public static BpmnData resolveGlobalVariablesInSequenceFlows(
            BpmnData bpmnDefinitions,
            ConfigFile configFileData) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        // Build map of global variables for quick lookup
        Map<String, String> globalVarsMap = new HashMap<>();
        if (configFileData != null && configFileData.globalVariables != null) {
            for (Variable var : configFileData.globalVariables) {
                if (var.name != null && var.value != null) {
                    globalVarsMap.put(var.name, var.value);
                }
            }
        }

        // Create pattern for matching tildes around variables: ~variable~
        Pattern pattern = Pattern.compile("~([^~]+)~");

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.flowsById() == null || process.flowsById().isEmpty()) {
                updatedProcesses.add(process);
                continue;
            }

            Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> updatedFlows = new HashMap<>();

            for (Map.Entry<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> entry : process.flowsById().entrySet()) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow flow = entry.getValue();

                String updatedExpression;

                // Modify the expression field
                if (flow.expression() == null || flow.expression().trim().isEmpty()) {
                    updatedExpression = flow.expression();
                } else {
                    String expression = flow.expression();

                    // Resolve global variables if present
                    if (expression.contains("~")) {
                        updatedExpression = resolveGlobalVariablesInExpression(expression, globalVarsMap, pattern);
                    } else {
                        updatedExpression = expression;
                    }
                }

                // Create updated flow with modified expression field
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow updatedFlow =
                        new org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow(
                                flow.id(),
                                flow.name(),
                                updatedExpression,
                                flow.resolvedExpression()  // Keep existing resolvedExpression
                        );

                updatedFlows.put(entry.getKey(), updatedFlow);
            }

            // Create updated process with updated flows
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    process.nodesById(),
                    process.dataObjectsById(),
                    updatedFlows
            );

            updatedProcesses.add(updatedProcess);
        }

        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Converts dot notation to underscore notation in sequence flow expressions.
     * Transforms ~variable.field~ to variable_field (removes tildes and converts dots).
     * Sets the resolvedExpression field (copies expression if no changes needed).
     *
     * @param bpmnDefinitions the BPMN definitions
     * @return updated BPMN definitions with resolvedExpression field set
     */
    public static BpmnData convertDotsToUnderscoresInSequenceFlows(
            BpmnData bpmnDefinitions) {

        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        // Create pattern for matching tildes around variables: ~variable~
        Pattern pattern = Pattern.compile("~([^~]+)~");

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.flowsById() == null || process.flowsById().isEmpty()) {
                updatedProcesses.add(process);
                continue;
            }

            Map<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> updatedFlows = new HashMap<>();

            for (Map.Entry<String, org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow> entry : process.flowsById().entrySet()) {
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow flow = entry.getValue();

                String resolvedExpression;

                // Work with the expression field (which may have been modified by previous method)
                if (flow.expression() == null || flow.expression().trim().isEmpty()) {
                    resolvedExpression = flow.expression();
                } else {
                    String expression = flow.expression();

                    // Convert dots to underscores if tildes present
                    if (expression.contains("~")) {
                        resolvedExpression = convertDotsToUnderscoresInExpression(expression, pattern);
                    } else {
                        // No tildes, just copy the expression
                        resolvedExpression = expression;
                    }
                }

                // Create updated flow with resolvedExpression set
                org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow updatedFlow =
                        new org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.SequenceFlow(
                                flow.id(),
                                flow.name(),
                                flow.expression(),
                                resolvedExpression
                        );

                updatedFlows.put(entry.getKey(), updatedFlow);
            }

            // Create updated process with updated flows
            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    process.nodesById(),
                    process.dataObjectsById(),
                    updatedFlows
            );

            updatedProcesses.add(updatedProcess);
        }

        return new BpmnData(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Resolves global variables in an expression.
     * ~globalVariables.xxx~ → resolves from config to plain value
     * Other ~content~ remains unchanged (keeps tildes).
     *
     * @param text          the text containing expressions
     * @param globalVarsMap map of global variable names to values
     * @param pattern       the pattern for matching ~content~
     * @return the text with global variables resolved
     */
    private static String resolveGlobalVariablesInExpression(
            String text,
            Map<String, String> globalVarsMap,
            Pattern pattern) {

        if (text == null || !text.contains("~")) {
            return text;
        }

        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1); // Content inside ~...~
            String replacement;

            if (expression.startsWith("globalVariables.")) {
                // Resolve global variable
                String varName = expression.substring("globalVariables.".length());
                String varValue = globalVarsMap.get(varName);

                if (varValue != null) {
                    // Replace with plain value (no tildes)
                    replacement = varValue;
                } else {
                    // Variable not found, keep as-is with tildes
                    replacement = "~" + expression + "~";
                }
            } else {
                // Not a global variable, keep as-is with tildes
                replacement = "~" + expression + "~";
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Converts dot notation to underscore notation in an expression.
     * ~variable.field~ → variable_field (removes tildes, converts dots to underscores)
     *
     * @param text    the text containing expressions
     * @param pattern the pattern for matching ~content~
     * @return the text with dots converted to underscores and tildes removed
     */
    private static String convertDotsToUnderscoresInExpression(
            String text,
            Pattern pattern) {

        if (text == null || !text.contains("~")) {
            return text;
        }

        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1); // Content inside ~...~

            // Transform dot notation to underscore (e.g., req1.status → req1_status)
            // Remove tildes from the result
            String flattened = expression.replace('.', '_');

            matcher.appendReplacement(result, Matcher.quoteReplacement(flattened));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
