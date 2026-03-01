package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnDefinitions;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.util.JuelToGroovyParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for Bonita-specific BPMN transformations.
 */
public class BonitaBpmnHelper {

    /**
     * Resolves sequence flow expressions for Bonita.
     * Converts JUEL expressions to Groovy format.
     * Removes tildes and converts ${...} wrapper to plain Groovy expression.
     * Sets the resolvedExpression field.
     * 
     * This is Bonita-specific because Bonita uses Groovy which supports dots in variable names
     * and doesn't use the ${} wrapper.
     *
     * @param bpmnDefinitions the BPMN definitions
     * @return updated BPMN definitions with resolvedExpression field set for Bonita
     */
    public static BpmnDefinitions resolveSequenceFlowExpressions(BpmnDefinitions bpmnDefinitions) {
        if (bpmnDefinitions == null || bpmnDefinitions.processes() == null) {
            return bpmnDefinitions;
        }

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
                
                // Work with the expression field (which has global variables already resolved)
                if (flow.expression() == null || flow.expression().trim().isEmpty()) {
                    resolvedExpression = flow.expression();
                } else {
                    String expression = flow.expression();
                    
                    // Remove tildes if present (convert ~variable.field~ to variable.field)
                    // Bonita supports dots, so we keep them
                    expression = expression.replaceAll("~([^~]+)~", "$1");
                    
                    // Convert JUEL to Groovy (removes ${} wrapper)
                    resolvedExpression = JuelToGroovyParser.parse(expression);
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
}
