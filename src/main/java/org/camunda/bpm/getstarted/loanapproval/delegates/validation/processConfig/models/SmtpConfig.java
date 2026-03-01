package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SmtpConfig {
    public String host;
    public int port;
    public String username;
    public String password;
}
