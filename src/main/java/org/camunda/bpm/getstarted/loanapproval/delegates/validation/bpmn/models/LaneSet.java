package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models;

import java.util.List;

public record LaneSet(String id, List<Lane> lanes) {
}
