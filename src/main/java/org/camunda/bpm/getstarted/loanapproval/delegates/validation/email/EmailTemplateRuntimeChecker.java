package org.camunda.bpm.getstarted.loanapproval.delegates.validation.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class EmailTemplateRuntimeChecker {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void validate(String bodyPath, String jsonPath) throws Exception {
        ClassLoader cl = EmailTemplateRuntimeChecker.class.getClassLoader();

        // Load JSON config
        JsonNode config;
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(cl.getResourceAsStream(jsonPath)), StandardCharsets.UTF_8)) {
            config = mapper.readTree(reader);
        }

        JsonNode paramsNode = config.path("body").path("parameters");
        Map<String, Object> model = new HashMap<>();
        Iterator<String> fieldNames = paramsNode.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            // dummy value – you could also store the expression string
            model.put(key, "DUMMY");
        }

        // Freemarker config
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);

        // Load template from classpath
        Template template;
        try (Reader templateReader = new InputStreamReader(
                Objects.requireNonNull(cl.getResourceAsStream(bodyPath)), StandardCharsets.UTF_8)) {
            template = new Template("remind-line-manager.ftl", templateReader, cfg);
        }

        // Try to process template
        try (StringWriter out = new StringWriter()) {
            template.process(model, out);
            System.out.println("✅ FTL template renders successfully with given parameters.");
            // System.out.println("Rendered body:\n" + out);
        } catch (TemplateException e) {
            System.out.println("❌ Template error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
