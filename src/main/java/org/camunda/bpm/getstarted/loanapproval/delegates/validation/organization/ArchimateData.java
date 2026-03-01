package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

import lombok.Data;

import java.util.List;

@Data
public class ArchimateData {
    private List<Role> archimateRoles;
}
