package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassType {
    public String name;
    public List<State> states;
}
