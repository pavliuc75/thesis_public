package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a global variable that can be referenced anywhere in other models
 * and will be resolved before runtime.
 * 
 * Example from config.json:
 * {
 *   "name": "notificationServiceBaseUrl",
 *   "value": "https://api.example.com"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Variable {
    /**
     * The name of the variable.
     * Example: "notificationServiceBaseUrl", "appBaseUrl"
     */
    public String name;
    
    /**
     * The value of the variable.
     * Example: "https://api.example.com", "https://app.example.com"
     */
    public String value;
}

