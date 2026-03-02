package generator.businessObjectState;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class StatesHelper {
    public static BusinessDataStates loadDataFromFile(String filePath) throws IOException {
        if (filePath == null) {return null;}

        ObjectMapper mapper = new ObjectMapper();

        return mapper.readValue(new File(filePath), BusinessDataStates.class);
    }

    public static void validateStatesFile(String businessObjectStatePath) {
        if (businessObjectStatePath == null) {
            System.out.println("Warning: No business object states file provided, skipping validation.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode statesNode = mapper.readTree(new File(businessObjectStatePath));

            String schemaPath = "src/main/resources/misc/states_validation_schema.json";
            JsonNode schemaNode = mapper.readTree(new File(schemaPath));

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = factory.getSchema(schemaNode);

            Set<ValidationMessage> validationMessages = schema.validate(statesNode);
            if (!validationMessages.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("States file '").append(businessObjectStatePath)
                        .append("' is not valid against schema '")
                        .append(schemaPath).append("':\n");
                for (ValidationMessage message : validationMessages) {
                    sb.append(" - ").append(message.getMessage());
                    if (message.getPath() != null && !message.getPath().isEmpty()) {
                        sb.append(" (path: ").append(message.getPath()).append(")");
                    }
                    sb.append("\n");
                }
                throw new IllegalStateException(sb.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate states file '" + businessObjectStatePath + "'", e);
        }
    }
}
