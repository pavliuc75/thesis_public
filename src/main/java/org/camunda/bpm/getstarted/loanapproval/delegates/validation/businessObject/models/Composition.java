package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models;

public record Composition(String whole, String part, String wholeMultiplicity, String partMultiplicity) {}
// whole *-- part
// multiplicities are "1" or "many"
