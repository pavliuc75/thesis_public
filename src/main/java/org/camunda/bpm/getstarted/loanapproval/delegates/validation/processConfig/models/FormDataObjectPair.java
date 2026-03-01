package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

/**
 * Represents a pair of a form reference and its associated data object reference.
 * Used to track which forms produce which data objects in user tasks.
 */
public record FormDataObjectPair(
        String formRef,           // e.g., "${file:forms/request_absence_form.json}"
        String dataObjectRefId    // e.g., "DataObjectReference_0zlxotf"
) {}

