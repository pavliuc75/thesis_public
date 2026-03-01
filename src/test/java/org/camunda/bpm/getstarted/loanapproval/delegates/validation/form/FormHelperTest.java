package org.camunda.bpm.getstarted.loanapproval.delegates.validation.form;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FormHelperTest {

    @Test
    void shouldGenerateFormFromJsonSchema() {
        // Given
        String schemaPath = "models/form/approve_absence_form.json";
        String variableName = "req1";

        // When
        String formHtml = FormHelper.generateCamundaFormFromSchema(schemaPath, variableName);

        // Then
        assertNotNull(formHtml);
        assertFalse(formHtml.isEmpty());
        
        // Check form structure
        assertTrue(formHtml.contains("<form role=\"form\""), "Should contain form tag");
        assertTrue(formHtml.contains("</form>"), "Should contain closing form tag");
        
        // Check that fields have variable name prefix
        assertTrue(formHtml.contains("req1_requestId"),
            "Should prefix field with variable name: req1_requestId");
        assertTrue(formHtml.contains("req1_employeeId"),
            "Should prefix field with variable name: req1_employeeId");
        assertTrue(formHtml.contains("req1_absenceType"),
            "Should prefix field with variable name: req1_absenceType");
        
        // Check that required fields are marked
        assertTrue(formHtml.contains("required"), "Should mark required fields");
        
        // Check that nested object is flattened with variable prefix
        // (clarificationMessage.message -> req1_clarificationMessage_message)
        assertTrue(formHtml.contains("req1_clarificationMessage_message"),
            "Should flatten nested object with variable prefix: req1_clarificationMessage_message");
        assertTrue(formHtml.contains("req1_clarificationMessage_timestamp"),
            "Should flatten nested object with variable prefix: req1_clarificationMessage_timestamp");
        
        // Check that enum field generates select dropdown
        assertTrue(formHtml.contains("<select"), "Should generate select for enum field");
        assertTrue(formHtml.contains("req1_status"), "Should prefix enum field with variable name");
        assertTrue(formHtml.contains("PENDING"), "Should include enum values");
        assertTrue(formHtml.contains("APPROVED"), "Should include enum values");
        assertTrue(formHtml.contains("REJECTED"), "Should include enum values");
        
        // Check Camunda-specific attributes with prefixed names
        assertTrue(formHtml.contains("cam-variable-name=\"req1_requestId\""),
            "Should use prefixed name in cam-variable-name");
        assertTrue(formHtml.contains("cam-variable-type"), "Should include Camunda variable type attribute");
        
        // Check type mapping (integer -> Long)
        assertTrue(formHtml.contains("cam-variable-type=\"Long\""), "Should map integer to Long");
        
        System.out.println("Generated form:");
        System.out.println(formHtml);
    }

    @Test
    void shouldSaveGeneratedFormToFile() throws Exception {
        // Given
        String schemaPath = "models/form/approve_absence_form.json";
        String variableName = "req1";
        String outputDir = "src/test/resources/out";
        String outputFileName = "test_approve_absence_form.html";

        // When
        String outputPath = FormHelper.generateAndSaveForm(schemaPath, variableName, outputDir, outputFileName);

        // Then
        assertNotNull(outputPath);
        
        File outputFile = new File(outputPath);
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.isFile(), "Output should be a file");
        
        // Read and verify content
        String content = Files.readString(Paths.get(outputPath));
        assertFalse(content.isEmpty(), "File content should not be empty");
        assertTrue(content.contains("req1_requestId"), "Content should have prefixed field names");
        assertTrue(content.contains("<form role=\"form\""), "Content should contain form tag");
        
        System.out.println("Form saved to: " + outputPath);
        System.out.println("File size: " + outputFile.length() + " bytes");
        
        // Cleanup (optional - comment out if you want to inspect the file)
        outputFile.delete();
    }
}

