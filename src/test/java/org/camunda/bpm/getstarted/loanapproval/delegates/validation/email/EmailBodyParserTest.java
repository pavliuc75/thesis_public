package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailBodyParserTest {

    @Test
    void shouldRenderBody() {
        String body = EmailBodyParser.renderBody(
                "models/email/remind_line_manager_email.ftl",
                "models/email/remind_line_manager_email.json"
        );

        System.out.println(body);
    }
}