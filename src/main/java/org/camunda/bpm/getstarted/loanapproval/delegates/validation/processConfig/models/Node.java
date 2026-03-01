package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Configuration for a flow node (task or event) in the process.
 * Represents the configuration block for individual tasks/events from config.json.
 * <p>
 * Example from config.json:
 * {
 * "name": "Request Absence",
 * "formRef": "${file:forms/request-absence-v1.json}",
 * "vendor_specific_attributes": {}
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Node {

    /**
     * The name of the flow node (task or event).
     * This should match the name in the BPMN diagram.
     * Example: "Request Absence", "employee_needs_time_off"
     */
    public String name;

    /**
     * Reference to a form definition file.
     * Example: "${file:forms/request-absence-v1.json}"
     */
    public String formRef;

    /**
     * Reference to an email config file.
     * Example: "${file:emails/remind-line-manager-email.yml}"
     */
    public String emailJsonRef;

    /**
     * Reference to an email template file.
     * Example: "${file:emails/remind-line-manager-email.ftl}"
     */

    public String emailFtlRef;
    /**
     * Reference to a REST call definition file.
     * Example: "${file:rests/notify-employee.json}"
     */
    public String restCallRef;

    /**
     * Reference to a DMN decision table file for business rule tasks.
     * Example: "${file:dmn/loan_approval_dmn.dmn}"
     */
    public String dmnRef;

    /**
     * Result variable to store the DMN decision output.
     * Example: "${~loan.approvalState~}"
     */
    public String dmnResultVariable;

    /**
     * Vendor-specific attributes for this flow node.
     * Example: {"camunda:class": "org.example.MyDelegate"}
     */
    @JsonProperty("vendor_specific_attributes")
    public Map<String, Object> vendorSpecificAttributes;
}
