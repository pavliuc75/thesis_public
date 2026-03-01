package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState.models.ClassType;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessDataStates {
    public List<ClassType> classes;
}
