package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Root configuration file structure.
 * Contains global variables and process definitions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigFile {
    /**
     * Global variables that can be referenced anywhere in other models
     * and will be resolved before runtime.
     * Example: [{"name": "notificationServiceBaseUrl", "value": "https://..."}, ...]
     */
    public List<Variable> globalVariables;
    
    /**
     * Process definitions.
     * Example: [{"id": "absenceRequest", "name": "absenceRequest", "config": {...}}, ...]
     */
    public List<Process> processes;
    public SmtpConfig smtpConfig;
}
