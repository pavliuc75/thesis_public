package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailHelperTest {
    @Test
    void shouldValidateWhenAllArgsValid() {
        String ftl = "models/email/_valid.ftl";
        String json = "models/email/_valid.json";
        assertDoesNotThrow(() -> EmailHelper.validate(ftl, json));
    }

    @Test
    void shouldThrowWhenFtlInvalid() {
        String ftl = "models/email/_invalid.ftl";
        String json = "models/email/_valid.json";
        assertThrows(Exception.class, () -> EmailHelper.validate(ftl, json));
    }

    @Test
    void shouldThrowWhenJsonInvalid() {
        String ftl = "models/email/_valid.ftl";
        String json = "models/email/_invalid.json";
        assertThrows(Exception.class, () -> EmailHelper.validate(ftl, json));
    }

    @Test
    void shouldThrowWhenBothInvalid() {
        String ftl = "models/email/_invalid.ftl";
        String json = "models/email/_invalid.json";
        assertThrows(Exception.class, () -> EmailHelper.validate(ftl, json));
    }
}