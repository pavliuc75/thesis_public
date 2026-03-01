package org.camunda.bpm.getstarted.loanapproval.delegates;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("blancDelegate")
public class BlancDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        System.out.println("Service task name: " + execution.getCurrentActivityName());
    }
}
