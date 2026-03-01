package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for Camunda-specific BPMN transformations.
 */
public class CamundaBpmnHelper {

    /**
     * Resolves sequence flow expressions for Camunda.
     * Converts dot notation to underscore notation in sequence flow expressions.
     * Transforms ~variable.field~ to variable_field (removes tildes and converts dots).
     * Sets the resolvedExpression field (copies expression if no changes needed).
     * 
     * This is Camunda-specific because Camunda uses JUEL which doesn't support dots in variable names.
     *
     * @param bpmnDefinitions the BPMN definitions
     * @return updated BPMN definitions with resolvedExpression field set
     */
    public static BpmnDefinitions resolveSequenceFlowExpressions(BpmnDefinitions bpmnDefinitions) {
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

        return new BpmnDefinitions(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Resolves businessRuleTask attributes for Camunda.
     * - dmnRef: keep only the file name without extension
     * - dmnResultVariable: remove ${~...~} wrappers and replace dots with underscores
     *
     * @param bpmnDefinitions the BPMN definitions
     * @return updated BPMN definitions with resolved businessRuleTask attributes
     */
    public static BpmnDefinitions resolveBusinessRuleTasks(BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

        List<ProcessDef> updatedProcesses = new ArrayList<>();

        for (ProcessDef process : bpmnDefinitions.processes()) {
            if (process.nodesById() == null || process.nodesById().isEmpty()) {
                updatedProcesses.add(process);
                continue;
            }

            Map<String, FlowNode> updatedNodes = new HashMap<>(process.nodesById());

            for (Map.Entry<String, FlowNode> entry : process.nodesById().entrySet()) {
                FlowNode node = entry.getValue();
                if (!"businessRuleTask".equals(node.type())) {
                    continue;
                }

                String resolvedDmnRef = normalizeDmnRef(node.dmnRef());
                String resolvedDmnResultVariable = normalizeDmnResultVariable(node.dmnResultVariable());

                FlowNode updatedNode = node.toBuilder()
                        .dmnRef(resolvedDmnRef)
                        .dmnResultVariable(resolvedDmnResultVariable)
                        .build();
                updatedNodes.put(entry.getKey(), updatedNode);
            }

            ProcessDef updatedProcess = new ProcessDef(
                    process.id(),
                    process.name(),
                    process.isExecutable(),
                    process.laneSet(),
                    updatedNodes,
                    process.dataObjectsById(),
                    process.flowsById()
            );

            updatedProcesses.add(updatedProcess);
        }

        return new BpmnDefinitions(
                bpmnDefinitions.id(),
                bpmnDefinitions.targetNamespace(),
                bpmnDefinitions.collaboration(),
                updatedProcesses
        );
    }

    /**
     * Converts dot notation to underscore notation in an expression.
     * ~variable.field~ → variable_field (removes tildes, converts dots to underscores)
     *
     * @param text    the text containing expressions
     * @param pattern the pattern for matching ~content~
     * @return the text with dots converted to underscores and tildes removed
     */
    private static String convertDotsToUnderscoresInExpression(String text, Pattern pattern) {
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

    private static String normalizeDmnRef(String dmnRef) {
        if (dmnRef == null) {
            return null;
        }

        String trimmed = dmnRef.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String path = trimmed;
        if (path.startsWith("${file:") && path.endsWith("}")) {
            path = path.substring(7, path.length() - 1);
        }

        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private static String normalizeDmnResultVariable(String dmnResultVariable) {
        if (dmnResultVariable == null) {
            return null;
        }

        String trimmed = dmnResultVariable.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String content = trimmed;
        if (content.startsWith("${") && content.endsWith("}")) {
            content = content.substring(2, content.length() - 1).trim();
        }

        if (content.startsWith("~") && content.endsWith("~") && content.length() > 1) {
            content = content.substring(1, content.length() - 1);
        } else if (content.contains("~")) {
            Matcher matcher = Pattern.compile("~([^~]+)~").matcher(content);
            if (matcher.find()) {
                content = matcher.group(1);
            }
        }

        return content.replace('.', '_');
    }
}
