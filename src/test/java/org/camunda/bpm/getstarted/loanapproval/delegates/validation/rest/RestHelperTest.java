package org.camunda.bpm.getstarted.loanapproval.delegates.validation.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestHelperTest {
    @Test
    void shouldValidateWhenValid() {
        String path = "models/rest/_valid.json";
        assertDoesNotThrow(() -> RestHelper.validate(path));
    }

    @Test
    void shouldThrowWhenInvalid() {
        String path = "models/rest/_invalid.json";
        assertThrows(Exception.class, () -> RestHelper.validate(path));
    }
}