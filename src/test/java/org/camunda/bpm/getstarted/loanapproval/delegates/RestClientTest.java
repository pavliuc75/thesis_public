package org.camunda.bpm.getstarted.loanapproval.delegates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Bare minimum test to load a REST call configuration file and execute the REST call.
 */
public class RestClientTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testExecuteRestCall() {
        JsonNode restConfig = loadRestConfigFromFile("notify_employee_rest.json");
        if (restConfig == null) {
            return;
        }

        JsonNode requestNode = restConfig.get("request");
        if (requestNode == null) {
            return;
        }

        String method = requestNode.has("method") ? requestNode.get("method").asText() : "POST";
        String urlString = requestNode.has("url") ? requestNode.get("url").asText() : null;
        JsonNode bodyNode = requestNode.get("body");

        if (urlString == null || urlString.isEmpty() || urlString.contains("....")) {
            return;
        }

        // Random map of variables
        Map<String, String> variables = new HashMap<>();
        variables.put("req1_employeeEmail", "test@example.com");
        variables.put("req1_requestId", "12345");
        variables.put("req1_status", "APPROVED");
        variables.put("req1_employeeName", "John Doe");

        try {
            executeRestCall(method, urlString, bodyNode, variables);
            System.out.println("REST call executed successfully");
        } catch (Exception e) {
            System.out.println("Failed to execute REST call: " + e.getMessage());
        }
    }

    private JsonNode loadRestConfigFromFile(String fileName) {
        try {
            InputStream configStream = getClass().getClassLoader()
                    .getResourceAsStream(fileName);
            if (configStream == null) {
                configStream = getClass().getClassLoader()
                        .getResourceAsStream("models/rest/" + fileName);
            }
            if (configStream == null) {
                return null;
            }
            return mapper.readTree(configStream);
        } catch (Exception e) {
            return null;
        }
    }

    private void executeRestCall(String method, String urlString, JsonNode bodyNode, 
                                 Map<String, String> variables) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            
            if (bodyNode != null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                String bodyString = bodyNode.toString();
                // Simple variable replacement
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    bodyString = bodyString.replace("${" + entry.getKey() + "}", entry.getValue());
                }
                
                byte[] bodyBytes = bodyString.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bodyBytes);
                }
            }
            
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            
            InputStream inputStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            
            if (inputStream != null) {
                try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                    if (scanner.hasNext()) {
                        System.out.println("Response: " + scanner.next());
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
