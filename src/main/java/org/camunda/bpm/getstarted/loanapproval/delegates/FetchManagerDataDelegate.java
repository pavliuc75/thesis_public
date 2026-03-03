package org.camunda.bpm.getstarted.loanapproval.delegates;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("fetchManagerDataDelegate")
public class FetchManagerDataDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        delegateExecution.setVariable("mgr1_id", "67");
        delegateExecution.setVariable("mgr1_fullName", "Alice Manager");
        delegateExecution.setVariable("mgr1_email", "voleakcook@gmail.com");
    }
}
