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
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

@Component("sendEmailDelegate")
public class SendEmailDelegate implements JavaDelegate {
    private static final Pattern UEL_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Configuration FREEMARKER_CONFIG;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        FREEMARKER_CONFIG = new Configuration(new Version("2.3.32"));
        FREEMARKER_CONFIG.setDefaultEncoding(StandardCharsets.UTF_8.name());
        FREEMARKER_CONFIG.setClassLoaderForTemplateLoading(
                SendEmailDelegate.class.getClassLoader(),
                "/");

        // fail fast when variables are missing, etc.
        FREEMARKER_CONFIG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        FREEMARKER_CONFIG.setLogTemplateExceptions(false);
        FREEMARKER_CONFIG.setFallbackOnNullLoopVariable(false);
    }

    private final ProcessEngine processEngine;

    @Autowired
    public SendEmailDelegate(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        // Get filenames from Camunda input parameters
        String configJsonFileName = (String) delegateExecution.getVariable("configJson");
        String templateFtlFileName = (String) delegateExecution.getVariable("templateFtl");
        String configFileName = "config.json";

        if (configJsonFileName == null || configJsonFileName.isEmpty()) {
            throw new IllegalStateException("Input parameter 'configJson' is required but not provided");
        }
        if (templateFtlFileName == null || templateFtlFileName.isEmpty()) {
            throw new IllegalStateException("Input parameter 'templateFtl' is required but not provided");
        }

        // Load the JSON file from resources
        String jsonContent = loadJsonFileFromResources(configJsonFileName);

        // Resolve all UEL expressions in the JSON string
        String resolvedJson = resolveUelExpressions(jsonContent, delegateExecution);

        // Extract parameters and render email body
        Map<String, Object> parameters = extractParametersFromJsonString(resolvedJson);
        String to = getTo(resolvedJson);
        String subject = getSubject(resolvedJson);
        String emailBody = renderBody(templateFtlFileName, parameters);

        // Load SMTP config from config.json
        Map<String, Object> smtpConfig = loadSmtpConfigFromFile(configFileName);

        try {
            sendEmail(smtpConfig, to, subject, emailBody);
            System.out.println("Email sent successfully to: " + to);
        } catch (MessagingException e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Resolved JSON content:");
        System.out.println(resolvedJson);
        System.out.println("Rendered email body:");
        System.out.println(emailBody);
    }

    private String getTo(String emailJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(emailJson);
            JsonNode toNode = root.path("headers").path("to");
            return toNode.asText();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse email JSON to extract to", e);
        }
    }

    private String getSubject(String emailJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(emailJson);
            JsonNode subjectNode = root.path("headers").path("subject");
            return subjectNode.asText();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse email JSON to extract subject", e);
        }
    }

    /**
     * Loads a JSON file from the resources folder and returns its content as a
     * string.
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
     * Loads SMTP configuration from the config.json file.
     *
     * @param configFileName the name of the config file (e.g., "config.json")
     * @return a Map containing SMTP configuration (host, port, username, password)
     * @throws RuntimeException if the config file cannot be loaded or parsed
     */
    private Map<String, Object> loadSmtpConfigFromFile(String configFileName) {
        try {
            // Load the config file from resources
            String configContent = loadJsonFileFromResources(configFileName);
            
            // Parse the JSON
            JsonNode root = OBJECT_MAPPER.readTree(configContent);
            JsonNode smtpConfigNode = root.path("smtpConfig");
            
            if (smtpConfigNode.isMissingNode() || !smtpConfigNode.isObject()) {
                throw new IllegalStateException("smtpConfig section not found in config file: " + configFileName);
            }
            
            // Extract SMTP config values
            Map<String, Object> smtpConfig = new HashMap<>();
            JsonNode hostNode = smtpConfigNode.path("host");
            JsonNode portNode = smtpConfigNode.path("port");
            JsonNode usernameNode = smtpConfigNode.path("username");
            JsonNode passwordNode = smtpConfigNode.path("password");
            
            if (!hostNode.isMissingNode()) {
                smtpConfig.put("host", hostNode.asText());
            }
            if (!portNode.isMissingNode()) {
                smtpConfig.put("port", portNode.asInt());
            }
            if (!usernameNode.isMissingNode()) {
                smtpConfig.put("username", usernameNode.asText());
            }
            if (!passwordNode.isMissingNode()) {
                smtpConfig.put("password", passwordNode.asText());
            }
            
            // Validate required fields
            if (!smtpConfig.containsKey("host") || !smtpConfig.containsKey("port") ||
                !smtpConfig.containsKey("username") || !smtpConfig.containsKey("password")) {
                throw new IllegalStateException("smtpConfig in " + configFileName + 
                    " is missing required fields (host, port, username, password)");
            }
            
            return smtpConfig;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SMTP config from " + configFileName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolves all UEL expressions (${...}) in a string using Camunda's
     * ExpressionManager.
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
                System.err
                        .println("Warning: Failed to evaluate expression '" + expressionText + "': " + e.getMessage());
                matcher.appendReplacement(result, Matcher.quoteReplacement(expressionText));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static Map<String, Object> extractParametersFromJsonString(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string must not be null or empty");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode paramsNode = root.path("body").path("parameters");

            if (!paramsNode.isObject()) {
                throw new IllegalArgumentException(
                        "JSON does not contain 'body.parameters' object");
            }

            Map<String, Object> variables = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String paramName = entry.getKey();
                JsonNode paramValue = entry.getValue();

                // Extract actual value from JSON node
                Object value;
                if (paramValue.isTextual()) {
                    value = paramValue.asText();
                } else if (paramValue.isNumber()) {
                    value = paramValue.asDouble();
                } else if (paramValue.isBoolean()) {
                    value = paramValue.asBoolean();
                } else {
                    value = paramValue.toString();
                }

                variables.put(paramName, value);
            }

            return variables;

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON string", e);
        }
    }

    /**
     * Renders the email body using FreeMarker template.
     *
     * @param ftlPath    the path to the FreeMarker template
     * @param parameters the parameters map to use for rendering
     * @return the rendered email body string
     */
    private String renderBody(String ftlPath, Map<String, Object> parameters) {
        try {
            Template template = FREEMARKER_CONFIG.getTemplate(ftlPath);
            StringWriter writer = new StringWriter();
            template.process(parameters, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FTL template: " + ftlPath, e);
        } catch (TemplateException e) {
            // If a variable is missing in parameters but used in FTL, you'll end up here
            throw new RuntimeException("Error rendering template (possible missing variable): " + ftlPath, e);
        }
    }

    private void sendEmail(Map<String, Object> smtpConfig, String to, String subject, String body) throws MessagingException {
        // Set up mail properties
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpConfig.get("host"));
        props.put("mail.smtp.port", String.valueOf(smtpConfig.get("port")));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        // Create session with authenticator
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        (String) smtpConfig.get("username"),
                        (String) smtpConfig.get("password"));
            }
        });

        // Create message
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress((String) smtpConfig.get("username")));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);

        // Send message
        Transport.send(message);
    }
}
