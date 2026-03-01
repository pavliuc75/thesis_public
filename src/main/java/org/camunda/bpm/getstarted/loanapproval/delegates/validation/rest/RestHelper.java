package org.camunda.bpm.getstarted.loanapproval.delegates.validation.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Variable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestHelper {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final Pattern PRE_RUNTIME_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");

    public static Set<ValidationMessage> validate(String jsonResourcePath) throws Exception {
        String schemaResourcePath = "models/rest/rest_validation_schema.json";
        ClassLoader cl = RestHelper.class.getClassLoader();

        try (InputStream schemaStream = cl.getResourceAsStream(schemaResourcePath);
             InputStream jsonStream = cl.getResourceAsStream(jsonResourcePath)) {

            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema resource not found: " + schemaResourcePath);
            }
            if (jsonStream == null) {
                throw new IllegalArgumentException("JSON resource not found: " + jsonResourcePath);
            }

            JsonNode schemaNode = mapper.readTree(schemaStream);
            JsonSchema schema = factory.getSchema(schemaNode);

            JsonNode jsonNode = mapper.readTree(jsonStream);

            Set<ValidationMessage> result = schema.validate(jsonNode);
            if (result.isEmpty()) {
                System.out.println("✅ REST config JSON is valid.");
            } else {
                System.out.println("❌ REST config JSON is INVALID:");
                result.forEach(e -> System.out.println(" - " + e.getMessage()));
                throw new IllegalArgumentException("REST config JSON is invalid.");
            }

            return schema.validate(jsonNode);
        }
    }

    /**
     * Resolves global variables in REST JSON files.
     * Replaces ${~globalVariables.xxx~} with the actual value from config.
     * Leaves other ${~...~} expressions unchanged.
     * Returns a map of file paths to processed JSON content (as strings).
     *
     * @param restActionPaths list of JSON file paths
     * @param configFileData the config file containing globalVariables
     * @return map of original JSON file names to processed JSON content strings
     */
    public static Map<String, String> resolveGlobalVariablesInRestActions(List<String> restActionPaths, ConfigFile configFileData) {
        Map<String, String> processedRestFiles = new HashMap<>();
        
        if (restActionPaths == null || restActionPaths.isEmpty()) {
            return processedRestFiles;
        }

        // Build map of global variables for quick lookup
        Map<String, String> globalVarsMap = new HashMap<>();
        if (configFileData != null && configFileData.globalVariables != null) {
            for (Variable var : configFileData.globalVariables) {
                if (var.name != null && var.value != null) {
                    globalVarsMap.put(var.name, var.value);
                }
            }
        }

        for (String jsonPath : restActionPaths) {
            if (jsonPath == null || jsonPath.trim().isEmpty()) {
                continue;
            }

            try {
                // Read the JSON file
                String resourcePath = jsonPath.startsWith("src/main/resources/") 
                    ? jsonPath 
                    : "src/main/resources/" + jsonPath;
                
                File jsonFile = new File(resourcePath);
                if (!jsonFile.exists()) {
                    System.err.println("Warning: REST JSON file not found: " + resourcePath);
                    continue;
                }

                JsonNode rootNode = mapper.readTree(jsonFile);

                // Process the JSON recursively
                JsonNode processedNode = processJsonNode(rootNode, globalVarsMap);

                // Convert processed JSON to string
                String processedJsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(processedNode);
                
                // Store with original file name as key
                processedRestFiles.put(jsonFile.getName(), processedJsonString);

            } catch (IOException e) {
                System.err.println("Error processing REST JSON file '" + jsonPath + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return processedRestFiles;
    }

    /**
     * Saves processed REST JSON files to the output directory.
     * 
     * @param processedRestFiles map of file names to processed JSON content strings
     * @param outputDir the output directory path (e.g., "src/main/resources/out")
     */
    public static void saveProcessedRestFiles(Map<String, String> processedRestFiles, String outputDir) {
        if (processedRestFiles == null || processedRestFiles.isEmpty()) {
            return;
        }

        try {
            Path outputDirPath = Paths.get(outputDir);
            Files.createDirectories(outputDirPath);

            for (Map.Entry<String, String> entry : processedRestFiles.entrySet()) {
                String fileName = entry.getKey();
                String jsonContent = entry.getValue();
                
                Path outputPath = outputDirPath.resolve(fileName);
                Files.write(outputPath, jsonContent.getBytes(StandardCharsets.UTF_8));
                
                System.out.println("Processed REST JSON: " + outputPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save processed REST files: " + e.getMessage(), e);
        }
    }

    /**
     * Recursively processes a JSON node, resolving global variables in string values.
     */
    private static JsonNode processJsonNode(JsonNode node, Map<String, String> globalVarsMap) {
        if (node == null) {
            return node;
        }

        if (node.isTextual()) {
            String text = node.asText();
            String processed = resolveGlobalVariablesInExpression(text, globalVarsMap);
            return new TextNode(processed);
        }

        if (node.isObject()) {
            ObjectNode objectNode = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                JsonNode processedValue = processJsonNode(entry.getValue(), globalVarsMap);
                objectNode.set(entry.getKey(), processedValue);
            });
            return objectNode;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = mapper.createArrayNode();
            node.elements().forEachRemaining(element -> {
                JsonNode processedElement = processJsonNode(element, globalVarsMap);
                arrayNode.add(processedElement);
            });
            return arrayNode;
        }

        // For other types (number, boolean, null), return as-is
        return node;
    }

    /**
     * Resolves global variables in a string.
     * ${~globalVariables.xxx~} → resolves from config (plain value, no ${})
     * Other ${~...~} expressions remain unchanged.
     */
    private static String resolveGlobalVariablesInExpression(String text, Map<String, String> globalVarsMap) {
        if (text == null || !text.contains("${~")) {
            return text;
        }

        Matcher matcher = PRE_RUNTIME_EXPRESSION_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1); // Content inside ${~...~}
            if (expression.startsWith("globalVariables.")) {
                String varName = expression.substring("globalVariables.".length());
                String varValue = globalVarsMap.get(varName);
                if (varValue != null) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(varValue));
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                }
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
