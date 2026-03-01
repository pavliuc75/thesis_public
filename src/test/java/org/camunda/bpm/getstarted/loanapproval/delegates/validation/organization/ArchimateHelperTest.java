package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchimateHelperTest {

    @Test
    void shouldValidateWhenValid() {
        String path = "models/organization/_valid.archimate";
        assertDoesNotThrow(() -> ArchimateHelper.validate(path));
    }

    @Test
    void shouldThrowWhenInvalid() {
        String path = "models/organization/_invalid.archimate";
        assertThrows(Exception.class, () -> ArchimateHelper.validate(path));
    }
}