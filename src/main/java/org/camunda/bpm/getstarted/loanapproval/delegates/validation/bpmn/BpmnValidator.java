package org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.File;
import java.io.InputStream;

public class BpmnValidator {

    /**
     * Validates a BPMN file from disk.
     * Throws an exception if invalid.
     */
    public static void validate(File bpmnFile) {
        BpmnModelInstance modelInstance = Bpmn.readModelFromFile(bpmnFile);
        Bpmn.validateModel(modelInstance);  // throws exception if invalid

        //todo iso time validation
    }

    /**
     * Validates a BPMN file from the classpath.
     * Throws an exception if invalid.
     */
    public static void validateFromClasspath(String classpathResource) {
        InputStream is = BpmnValidator.class.getClassLoader()
                .getResourceAsStream(classpathResource);

        if (is == null) {
            throw new IllegalArgumentException(
                    "BPMN resource not found on classpath: " + classpathResource
            );
        }

        BpmnModelInstance modelInstance = Bpmn.readModelFromStream(is);
        Bpmn.validateModel(modelInstance);  // throws exception if invalid
    }

    /**
     * Boolean-style validation.
     */
    public static boolean isValid(File bpmnFile) {
        try {
            validate(bpmnFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
