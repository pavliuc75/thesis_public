package org.camunda.bpm.getstarted.loanapproval.delegates;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class LogAbsenceInSystemService implements JavaDelegate {
    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        String rec1_recordId = "1";
        String rec1_absenceRequestId = "1";
        String rec1_loggedDate = "2026-01-08";

        delegateExecution.setVariable("rec1_recordId", rec1_recordId);
        delegateExecution.setVariable("rec1_absenceRequestId", rec1_absenceRequestId);
        delegateExecution.setVariable("rec1_loggedDate", rec1_loggedDate);
    }
}
