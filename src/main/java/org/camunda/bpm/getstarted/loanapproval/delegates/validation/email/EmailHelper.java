package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Variable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailHelper {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern PRE_RUNTIME_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");

    public static void validate(String ftlPath,
                         String jsonPath) throws Exception {
        String configPath = "models/email/email_validation_schema.json";

        EmailConfigValidator.validateConfig(configPath, jsonPath);
        EmailTemplateRuntimeChecker.validate(ftlPath, jsonPath);
    }

    /**
     * Resolves global variables in email JSON files.
     * Replaces ${~globalVariables.xxx~} with the actual value from config.
     * Leaves other ${~...~} expressions unchanged.
     * Returns a map of file paths to processed JSON content (as strings).
     *
     * @param emailConfigsPaths list of [ftlPath, jsonPath] pairs
     * @param configFileData the config file containing globalVariables
     * @return map of original JSON file paths to processed JSON content strings
     */
    public static Map<String, String> resolveGlobalVariablesInEmailConfigs(List<String[]> emailConfigsPaths, ConfigFile configFileData) {
        Map<String, String> processedEmails = new HashMap<>();
        
        if (emailConfigsPaths == null || emailConfigsPaths.isEmpty()) {
            return processedEmails;
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

        for (String[] config : emailConfigsPaths) {
            if (config == null || config.length < 2) {
                continue;
            }

            String jsonPath = config[1]; // jsonPath is at index 1

            try {
                // Read the JSON file
                String resourcePath = jsonPath.startsWith("src/main/resources/") 
                    ? jsonPath 
                    : "src/main/resources/" + jsonPath;
                
                File jsonFile = new File(resourcePath);
                if (!jsonFile.exists()) {
                    System.err.println("Warning: Email JSON file not found: " + resourcePath);
                    continue;
                }

                JsonNode rootNode = mapper.readTree(jsonFile);

                // Process the JSON recursively
                JsonNode processedNode = processJsonNode(rootNode, globalVarsMap);

                // Convert processed JSON to string
                String processedJsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(processedNode);
                
                // Store with original file name as key
                processedEmails.put(jsonFile.getName(), processedJsonString);

            } catch (IOException e) {
                System.err.println("Error processing email JSON file '" + jsonPath + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return processedEmails;
    }

    /**
     * Saves processed email JSON files and copies corresponding FTL template files to the output directory.
     * 
     * @param processedEmails map of file names to processed JSON content strings
     * @param emailConfigsPaths list of [ftlPath, jsonPath] pairs to find corresponding FTL files
     * @param outputDir the output directory path (e.g., "src/main/resources/out")
     */
    public static void saveProcessedEmailFiles(Map<String, String> processedEmails, List<String[]> emailConfigsPaths, String outputDir) {
        if (processedEmails == null || processedEmails.isEmpty()) {
            return;
        }

        try {
            Path outputDirPath = Paths.get(outputDir);
            Files.createDirectories(outputDirPath);

            // Build a map from JSON file name to FTL path for quick lookup
            Map<String, String> jsonToFtlMap = new HashMap<>();
            if (emailConfigsPaths != null) {
                for (String[] config : emailConfigsPaths) {
                    if (config != null && config.length >= 2) {
                        String ftlPath = config[0];
                        String jsonPath = config[1];
                        // Extract just the file name from the JSON path
                        String jsonFileName = jsonPath.substring(jsonPath.lastIndexOf('/') + 1);
                        jsonToFtlMap.put(jsonFileName, ftlPath);
                    }
                }
            }

            for (Map.Entry<String, String> entry : processedEmails.entrySet()) {
                String jsonFileName = entry.getKey();
                String jsonContent = entry.getValue();
                
                // Write processed JSON file
                Path jsonOutputPath = outputDirPath.resolve(jsonFileName);
                Files.write(jsonOutputPath, jsonContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Processed email JSON: " + jsonOutputPath);

                // Copy corresponding FTL file if it exists
                String ftlPath = jsonToFtlMap.get(jsonFileName);
                if (ftlPath != null) {
                    String resourcePath = ftlPath.startsWith("src/main/resources/") 
                        ? ftlPath 
                        : "src/main/resources/" + ftlPath;
                    
                    File ftlFile = new File(resourcePath);
                    if (ftlFile.exists()) {
                        String ftlFileName = ftlPath.substring(ftlPath.lastIndexOf('/') + 1);
                        Path ftlOutputPath = outputDirPath.resolve(ftlFileName);
                        Files.copy(ftlFile.toPath(), ftlOutputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Copied email FTL: " + ftlOutputPath);
                    } else {
                        System.err.println("Warning: FTL file not found: " + resourcePath);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save processed email files: " + e.getMessage(), e);
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
                // Resolve global variable
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
