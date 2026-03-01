package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

import lombok.Data;

import java.util.List;

@Data
public class OrganizationData {
    private List<Role> archimateRoles;
}
