package org.camunda.bpm.getstarted.loanapproval.delegates;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("throwDelegate")
public class ThrowDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        System.out.println("Service task name: " + execution.getCurrentActivityName());
        throw new BpmnError("ERR");
    }
}
