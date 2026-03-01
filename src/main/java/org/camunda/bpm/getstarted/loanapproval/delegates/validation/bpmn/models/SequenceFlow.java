package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

/**
 * Represents a BPMN SequenceFlow connecting nodes in a process.
 * 
 * @param id         the unique identifier of the sequence flow
 * @param name       the name/label of the sequence flow
 * @param expression the conditional expression for the flow (if any)
 * @param resolvedExpression
 */
public record SequenceFlow(
        String id,
        String name,
        String expression,
        String resolvedExpression
) {
    // Constructor without expression (for unconditional flows)
    public SequenceFlow(String id, String name) {
        this(id, name, null, null);
    }
}
