package org.camunda.bpm.getstarted.loanapproval.delegates.validation.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.util.JuelToGroovyParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for Bonita-specific REST transformations.
 */
public class BonitaRestHelper {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern PRE_RUNTIME_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");
    private static final Pattern FULL_EXPRESSION_PATTERN = Pattern.compile("^\\$\\{~([^}]+)~\\}$");

    /**
     * Resolves REST action expressions for Bonita.
     * Converts ${~expression~} to Groovy-compatible expressions.
     *
     * @param processedRestFiles map of file names to processed JSON content strings
     * @return updated map with Bonita-compatible expressions
     */
    public static Map<String, String> resolveRestActionExpressions(Map<String, String> processedRestFiles) {
        Map<String, String> resolvedRestFiles = new HashMap<>();
        if (processedRestFiles == null || processedRestFiles.isEmpty()) {
            return resolvedRestFiles;
        }

        for (Map.Entry<String, String> entry : processedRestFiles.entrySet()) {
            String fileName = entry.getKey();
            String jsonContent = entry.getValue();
            if (jsonContent == null) {
                continue;
            }

            try {
                JsonNode rootNode = mapper.readTree(jsonContent);
                JsonNode processedNode = processJsonNode(rootNode);
                String processedJsonString = mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(processedNode);
                resolvedRestFiles.put(fileName, processedJsonString);
            } catch (IOException e) {
                System.err.println("Error processing REST JSON content for '" + fileName + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        return resolvedRestFiles;
    }

    private static JsonNode processJsonNode(JsonNode node) {
        if (node == null) {
            return node;
        }

        if (node.isTextual()) {
            String text = node.asText();
            String processed = resolveGroovyExpression(text);
            return new TextNode(processed);
        }

        if (node.isObject()) {
            ObjectNode objectNode = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                JsonNode processedValue = processObjectEntry(entry.getKey(), entry.getValue());
                objectNode.set(entry.getKey(), processedValue);
            });
            return objectNode;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = mapper.createArrayNode();
            node.elements().forEachRemaining(element -> {
                JsonNode processedElement = processJsonNode(element);
                arrayNode.add(processedElement);
            });
            return arrayNode;
        }

        return node;
    }

    private static JsonNode processObjectEntry(String fieldName, JsonNode value) {
        if ("body".equals(fieldName) && value != null && value.isTextual()) {
            JsonNode parsedBody = parseJsonBody(value.asText());
            if (parsedBody != null) {
                return processJsonNode(parsedBody);
            }
        }

        return processJsonNode(value);
    }

    private static JsonNode parseJsonBody(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }

        try {
            return mapper.readTree(trimmed);
        } catch (IOException e) {
            return null;
        }
    }

    private static String resolveGroovyExpression(String text) {
        if (text == null || !text.contains("${~")) {
            return text;
        }

        Matcher fullMatcher = FULL_EXPRESSION_PATTERN.matcher(text);
        if (fullMatcher.matches()) {
            String innerExpression = fullMatcher.group(1);
            return JuelToGroovyParser.parse("${" + innerExpression + "}");
        }

        Matcher matcher = PRE_RUNTIME_EXPRESSION_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement = "${" + expression + "}";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
