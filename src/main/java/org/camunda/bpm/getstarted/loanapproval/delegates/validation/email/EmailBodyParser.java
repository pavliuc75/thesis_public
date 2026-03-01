package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EmailBodyParser {

    private static final Configuration FREEMARKER_CONFIG;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        FREEMARKER_CONFIG = new Configuration(new Version("2.3.32"));
        FREEMARKER_CONFIG.setDefaultEncoding(StandardCharsets.UTF_8.name());
        FREEMARKER_CONFIG.setClassLoaderForTemplateLoading(
                EmailBodyParser.class.getClassLoader(),
                "/"
        );

        // fail fast when variables are missing, etc.
        FREEMARKER_CONFIG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        FREEMARKER_CONFIG.setLogTemplateExceptions(false);
        FREEMARKER_CONFIG.setFallbackOnNullLoopVariable(false);
    }

    public static String renderBody(String ftlPath, String jsonPath) {
        Map<String, Object> variables = extractParametersFromJson(jsonPath);

        try {
            Template template = FREEMARKER_CONFIG.getTemplate(ftlPath);
            StringWriter writer = new StringWriter();
            template.process(variables, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FTL template: " + ftlPath, e);
        } catch (TemplateException e) {
            // If a variable is missing in JSON but used in FTL, you'll end up here
            throw new RuntimeException("Error rendering template (possible missing variable): " + ftlPath, e);
        }
    }


    private static Map<String, Object> extractParametersFromJson(String jsonPath) {
        try (InputStream is = EmailBodyParser.class.getClassLoader().getResourceAsStream(jsonPath)) {
            if (is == null) {
                throw new IllegalArgumentException("JSON config not found on classpath: " + jsonPath);
            }

            JsonNode root = OBJECT_MAPPER.readTree(is);
            JsonNode paramsNode = root.path("body").path("parameters");

            if (!paramsNode.isObject()) {
                throw new IllegalArgumentException("JSON does not contain 'body.parameters' object: " + jsonPath);
            }

            Map<String, Object> variables = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String paramName = entry.getKey();

                // We just need *some* value so FreeMarker can render.
                // Using the param name itself is enough for validation.
                variables.put(paramName, paramName);
            }

            return variables;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read or parse JSON config: " + jsonPath, e);
        }
    }
}
