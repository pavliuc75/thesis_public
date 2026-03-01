package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Lane {
    public String name;
    public String assignee;
    public String resolvedAssignee;
    
    @JsonProperty("vendor_specific_attributes")
    public Map<String, String> vendorSpecificAttributes;
}
