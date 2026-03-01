package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import org.junit.jupiter.api.Test;

class EmailBodyParserTest {

    @Test
    void shouldRenderBody() {
        String body = EmailBodyParser.renderBody(
                "models/email/kickoff_email.ftl",
                "models/email/kickoff_email.json"
        );

        System.out.println(body);
    }
}