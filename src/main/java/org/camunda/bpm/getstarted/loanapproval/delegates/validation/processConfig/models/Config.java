package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    /**
     * Lane configurations.
     * Example: [{"name": "Employee", "assignee": "...", ...}, ...]
     */
    public List<Lane> lanes;
    
    /**
     * Task configurations.
     * Example: [{"name": "Request Absence", "formRef": "...", ...}, ...]
     */
    public List<Node> tasks;
    
    /**
     * Event configurations.
     * Example: [{"name": "employee_needs_time_off", ...}, ...]
     */
    public List<Node> events;
}
