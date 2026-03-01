package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Process {
    public String id;
    public String name;
    public Config config;
}
