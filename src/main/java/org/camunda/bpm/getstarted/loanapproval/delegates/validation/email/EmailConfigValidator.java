package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.Set;

public class EmailConfigValidator {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public static void validateConfig(String schemaResourcePath, String jsonResourcePath) throws Exception {

        ClassLoader cl = EmailConfigValidator.class.getClassLoader();

        try (InputStream schemaStream = cl.getResourceAsStream(schemaResourcePath); InputStream jsonStream = cl.getResourceAsStream(jsonResourcePath)) {

            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema not found: " + schemaResourcePath);
            }
            if (jsonStream == null) {
                throw new IllegalArgumentException("JSON config not found: " + jsonResourcePath);
            }

            JsonNode schemaNode = mapper.readTree(schemaStream);
            JsonSchema schema = factory.getSchema(schemaNode);

            JsonNode jsonNode = mapper.readTree(jsonStream);

            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) throw new IllegalArgumentException("Email config JSON is INVALID: " + errors);
        }
    }
}
