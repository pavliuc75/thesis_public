package org.camunda.bpm.getstarted.loanapproval.delegates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.el.ExpressionManager;
import org.camunda.bpm.engine.impl.el.JuelExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("restCallDelegate")
public class RestCallDelegate implements JavaDelegate {
    private static final Pattern UEL_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProcessEngine processEngine;

    @Autowired
    public RestCallDelegate(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        //TODO TEST !!!!!!!

        // Get REST config filename from Camunda input parameter
        String restCallConfigFileName = (String) delegateExecution.getVariable("restCallConfig");

        if (restCallConfigFileName == null || restCallConfigFileName.isEmpty()) {
            System.err.println("Input parameter 'restCallConfig' is required but not provided");
            return;
        }

        // Load the REST config JSON file from resources
        String jsonContent = loadJsonFileFromResources(restCallConfigFileName);

        // Resolve all UEL expressions in the JSON string
        String resolvedJson = resolveUelExpressions(jsonContent, delegateExecution);

        // Parse the resolved JSON
        JsonNode restConfig = OBJECT_MAPPER.readTree(resolvedJson);
        JsonNode requestNode = restConfig.get("request");

        if (requestNode == null) {
            System.err.println("REST config does not contain 'request' object");
            return;
        }

        // Extract request details
        String method = requestNode.has("method") ? requestNode.get("method").asText() : "POST";
        String urlString = requestNode.has("url") ? requestNode.get("url").asText() : null;
        JsonNode headersNode = requestNode.get("headers");
        JsonNode bodyNode = requestNode.get("body");

        if (urlString == null || urlString.isEmpty()) {
            System.err.println("REST config does not contain a valid URL");
            return;
        }

        // Execute the REST call (don't throw on failure, just log)
        executeRestCall(method, urlString, headersNode, bodyNode);
    }

    /**
     * Loads a JSON file from the resources folder and returns its content as a string.
     */
    private String loadJsonFileFromResources(String fileName) throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new IllegalStateException("File not found in resources: " + fileName);
        }

        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * Resolves all UEL expressions (${...}) in a string using Camunda's ExpressionManager.
     * This supports full UEL expressions, not just variable lookups.
     *
     * @param text  the text containing UEL expressions
     * @param scope the variable scope (DelegateExecution implements VariableScope)
     * @return the text with all UEL expressions resolved to their values
     */
    private String resolveUelExpressions(String text, VariableScope scope) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl config = (org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl) processEngine
                .getProcessEngineConfiguration();
        ExpressionManager expressionManager = config.getExpressionManager();

        Matcher matcher = UEL_EXPRESSION_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expressionText = matcher.group(0); // Full ${...} expression

            try {
                // Create and evaluate the expression using ExpressionManager
                JuelExpression expression = (JuelExpression) expressionManager.createExpression(expressionText);
                Object value = expression.getValue(scope);

                String replacement;
                if (value != null) {
                    replacement = String.valueOf(value);
                } else {
                    // Expression evaluated to null, keep the expression as-is
                    replacement = expressionText;
                }

                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } catch (Exception e) {
                // If expression evaluation fails, keep the original expression
                System.err.println("Warning: Failed to evaluate expression '" + expressionText + "': " + e.getMessage());
                matcher.appendReplacement(result, Matcher.quoteReplacement(expressionText));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Executes a REST call using the provided configuration.
     * Does not throw exceptions on failure, just logs errors.
     */
    private void executeRestCall(String method, String urlString, JsonNode headersNode, JsonNode bodyNode) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            try {
                connection.setRequestMethod(method);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                // Set default Content-Type if not provided
                boolean hasContentType = false;
                if (headersNode != null && headersNode.isObject()) {
                    headersNode.fields().forEachRemaining(entry -> {
                        connection.setRequestProperty(entry.getKey(), entry.getValue().asText());
                        if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                            // Content-Type is set
                        }
                    });
                    if (headersNode.has("Content-Type")) {
                        hasContentType = true;
                    }
                }

                if (!hasContentType && bodyNode != null) {
                    connection.setRequestProperty("Content-Type", "application/json");
                }

                // Write request body if present
                if (bodyNode != null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                    String bodyString;
                    if (bodyNode.isTextual()) {
                        // Body is already a JSON string
                        bodyString = bodyNode.asText();
                    } else {
                        // Body is a JSON object, convert to string
                        bodyString = OBJECT_MAPPER.writeValueAsString(bodyNode);
                    }

                    byte[] bodyBytes = bodyString.getBytes(StandardCharsets.UTF_8);
                    connection.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(bodyBytes);
                        os.flush();
                    }
                }

                // Get response
                int responseCode = connection.getResponseCode();
                System.out.println("REST call executed - Response Code: " + responseCode);

                // Read response (but don't store it as process variable)
                InputStream inputStream = responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                if (inputStream != null) {
                    try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                        if (scanner.hasNext()) {
                            String response = scanner.next();
                            System.out.println("REST call response: " + response);
                        }
                    }
                }

            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            // Don't throw, just log the error
            System.err.println("Failed to execute REST call: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
