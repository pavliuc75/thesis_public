package org.camunda.bpm.getstarted.loanapproval.delegates.validation.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnData;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FormHelper {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void validate(String resourcePath) throws Exception {
        ClassLoader cl = FormHelper.class.getClassLoader();

        InputStream mySchemaStream = cl.getResourceAsStream(resourcePath);

        if (mySchemaStream == null) {
            throw new IllegalStateException("Schema file not found on classpath");
        }

        JsonNode mySchemaNode = mapper.readTree(mySchemaStream);

        // Load the official 2020-12 meta-schema
        URL metaUrl = new URL("https://json-schema.org/draft/2020-12/schema");
        JsonNode metaSchemaNode = mapper.readTree(metaUrl);

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema metaSchema = factory.getSchema(metaSchemaNode);

        Set<ValidationMessage> errors = metaSchema.validate(mySchemaNode);

        if (errors.isEmpty()) {
            System.out.println("✅ Your JSON Schema is valid according to draft 2020-12.");
        } else {
            System.out.println("❌ Your JSON Schema is NOT valid:");
            errors.forEach(e -> System.out.println(" - " + e.getMessage()));
        }
    }

    /**
     * Generates a Camunda embedded form (HTML) from a JSON Schema file.
     * Flattens nested objects using underscore notation with a variable name prefix.
     * For example, with variableName="absenceRequest" and nested field "clarificationMessage.message",
     * the generated field name will be "absenceRequest_clarificationMessage_message".
     *
     * @param jsonSchemaPath path to the JSON Schema file in resources
     * @param variableName the Camunda variable name to use as prefix for all fields
     * @return HTML string for the Camunda embedded form
     */
    public static String generateCamundaFormFromSchema(String jsonSchemaPath, String variableName) {
        try (InputStream schemaStream = FormHelper.class.getClassLoader().getResourceAsStream(jsonSchemaPath)) {
            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema file not found: " + jsonSchemaPath);
            }

            JsonNode schema = mapper.readTree(schemaStream);
            String title = schema.has("title") ? schema.get("title").asText() : "Form";

            StringBuilder html = new StringBuilder();
            html.append("<form role=\"form\" name=\"").append(escapeHtml(title.replaceAll("\\s+", ""))).append("\">\n");

            // Get required fields
            Set<String> requiredFields = new HashSet<>();
            if (schema.has("required") && schema.get("required").isArray()) {
                for (JsonNode req : schema.get("required")) {
                    requiredFields.add(req.asText());
                }
            }

            // Parse properties
            if (schema.has("properties")) {
                JsonNode properties = schema.get("properties");
                List<FormField> fields = extractFields(properties, "", requiredFields, schema);
                
                for (FormField field : fields) {
                    // Prepend variable name to field name
                    field.name = variableName + "_" + field.name;
                    html.append(generateFormField(field));
                }
            }

            html.append("</form>\n");

            return html.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Camunda form from schema: " + jsonSchemaPath, e);
        }
    }

    /**
     * Generates a Camunda embedded form from JSON Schema and saves it to the output directory.
     *
     * @param jsonSchemaPath path to the JSON Schema file in resources
     * @param variableName the Camunda variable name to use as prefix for all fields
     * @param outputDirPath the output directory path (e.g., "src/main/resources/out")
     * @param outputFileName the name for the output HTML file (e.g., "approve_absence_form.html")
     * @return the path to the generated HTML file
     */
    public static String generateAndSaveForm(String jsonSchemaPath, String variableName, 
                                            String outputDirPath, String outputFileName) {
        try {
            // Generate the form HTML
            String formHtml = generateCamundaFormFromSchema(jsonSchemaPath, variableName);

            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            // Create output file path
            Path outputPath = outputDir.resolve(outputFileName);

            // Write HTML to file
            Files.write(outputPath, formHtml.getBytes(StandardCharsets.UTF_8));

            return outputPath.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate and save form: " + jsonSchemaPath, e);
        }
    }

    /**
     * Recursively extracts fields from JSON schema, flattening nested objects.
     */
    private static List<FormField> extractFields(JsonNode properties, String prefix, Set<String> requiredFields, JsonNode rootSchema) {
        List<FormField> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();

        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String fieldName = entry.getKey();
            JsonNode fieldSchema = entry.getValue();

            String fullPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
            String flattenedName = fullPath.replace(".", "_");

            // Check if this is a reference to a definition
            if (fieldSchema.has("$ref")) {
                String ref = fieldSchema.get("$ref").asText();
                JsonNode referencedSchema = resolveReference(ref, rootSchema);
                if (referencedSchema != null && referencedSchema.has("properties")) {
                    // Recursively extract fields from referenced object
                    fields.addAll(extractFields(referencedSchema.get("properties"), fullPath, new HashSet<>(), rootSchema));
                    continue;
                }
            }

            String type = fieldSchema.has("type") ? fieldSchema.get("type").asText() : "string";

            // If it's an object type, recursively extract its properties
            if ("object".equals(type) && fieldSchema.has("properties")) {
                fields.addAll(extractFields(fieldSchema.get("properties"), fullPath, new HashSet<>(), rootSchema));
            } else {
                // Create a form field
                FormField field = new FormField();
                field.name = flattenedName;
                field.label = toLabel(fieldName);
                field.type = type;
                field.required = requiredFields.contains(fieldName);
                
                if (fieldSchema.has("description")) {
                    field.description = fieldSchema.get("description").asText();
                }
                
                if (fieldSchema.has("enum") && fieldSchema.get("enum").isArray()) {
                    field.enumValues = new ArrayList<>();
                    for (JsonNode enumValue : fieldSchema.get("enum")) {
                        field.enumValues.add(enumValue.asText());
                    }
                }
                
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * Resolves a $ref reference in the schema.
     */
    private static JsonNode resolveReference(String ref, JsonNode rootSchema) {
        if (!ref.startsWith("#/")) {
            return null;
        }
        
        String[] parts = ref.substring(2).split("/");
        JsonNode current = rootSchema;
        
        for (String part : parts) {
            if (current.has(part)) {
                current = current.get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }

    /**
     * Generates HTML for a single form field.
     */
    private static String generateFormField(FormField field) {
        StringBuilder html = new StringBuilder();
        String camType = mapTypeToCamunda(field.type);
        
        html.append("  <label for=\"").append(field.name).append("\">").append(escapeHtml(field.label));
        if (field.required) {
            html.append(" *");
        }
        html.append("</label>\n");
        
        if (field.enumValues != null && !field.enumValues.isEmpty()) {
            // Generate select dropdown for enum
            html.append("  <select id=\"").append(field.name).append("\" ");
            html.append("cam-variable-name=\"").append(field.name).append("\" ");
            html.append("cam-variable-type=\"String\"");
            if (field.required) {
                html.append(" required");
            }
            html.append(">\n");
            html.append("    <option value=\"\">-- Select --</option>\n");
            for (String enumValue : field.enumValues) {
                html.append("    <option value=\"").append(escapeHtml(enumValue)).append("\">");
                html.append(escapeHtml(enumValue)).append("</option>\n");
            }
            html.append("  </select>\n\n");
        } else if ("string".equals(field.type) && field.description != null && field.description.toLowerCase().contains("long")) {
            // Multi-line text area for long strings
            html.append("  <textarea id=\"").append(field.name).append("\" ");
            html.append("cam-variable-name=\"").append(field.name).append("\" ");
            html.append("cam-variable-type=\"").append(camType).append("\"");
            if (field.required) {
                html.append(" required");
            }
            html.append(" rows=\"4\"></textarea>\n\n");
        } else {
            // Regular input field
            String inputType = getHtmlInputType(field.type);
            html.append("  <input type=\"").append(inputType).append("\" ");
            html.append("id=\"").append(field.name).append("\" ");
            html.append("cam-variable-name=\"").append(field.name).append("\" ");
            html.append("cam-variable-type=\"").append(camType).append("\"");
            if (field.required) {
                html.append(" required");
            }
            html.append(">\n\n");
        }
        
        return html.toString();
    }

    /**
     * Maps JSON Schema type to Camunda variable type.
     */
    private static String mapTypeToCamunda(String jsonType) {
        return switch (jsonType) {
            case "integer" -> "Long";
            case "number" -> "Double";
            case "boolean" -> "Boolean";
            default -> "String";
        };
    }

    /**
     * Gets HTML input type based on JSON Schema type.
     */
    private static String getHtmlInputType(String jsonType) {
        return switch (jsonType) {
            case "integer", "number" -> "number";
            case "boolean" -> "checkbox";
            default -> "text";
        };
    }

    /**
     * Converts field name to a readable label.
     */
    private static String toLabel(String fieldName) {
        // Convert camelCase or snake_case to Title Case
        String result = fieldName.replaceAll("([A-Z])", " $1").replaceAll("_", " ");
        return result.substring(0, 1).toUpperCase() + result.substring(1).trim();
    }

    /**
     * Escapes HTML special characters.
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Internal class to represent a form field.
     */
    private static class FormField {
        String name;
        String label;
        String type;
        boolean required;
        String description;
        List<String> enumValues;
    }

    /**
     * Generates HTML forms for all user tasks that have a formRef in the configuration.
     * Iterates through all processes and their tasks, and for each task with a formRef,
     * generates an HTML form and saves it to the specified output directory.
     * Uses the resolvedFormOutputVariableName from BpmnDefinitions if available.
     *
     * @param configFileData the loaded configuration file containing process and task definitions
     * @param bpmnDefinitions the BPMN definitions containing flow nodes with resolvedFormOutputVariableName
     * @param outputDir the output directory path (e.g., "src/main/resources/out")
     * @return the number of forms generated
     */
    public static int generateFormsFromConfigAndBpmn(
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile configFileData,
            BpmnData bpmnDefinitions,
            String outputDir) {
        
        if (configFileData == null || configFileData.processes == null) {
            System.out.println("No configuration data available for form generation.");
            return 0;
        }

        int formsGenerated = 0;

        for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Process process : configFileData.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }

            // Find matching BPMN process by ID
            org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef bpmnProcess = null;
            if (bpmnDefinitions != null && bpmnDefinitions.processes() != null) {
                bpmnProcess = bpmnDefinitions.processes().stream()
                        .filter(p -> process.id != null && process.id.equals(p.id()))
                        .findFirst()
                        .orElse(null);
            }

            for (org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node task : process.config.tasks) {
                if (task.formRef == null || task.formRef.trim().isEmpty()) {
                    continue; // Skip tasks without form references
                }

                try {
                    // Extract form path from formRef (e.g., "${file:forms/request_absence_form.json}")
                    String formPath = extractFormPathFromRef(task.formRef);
                    if (formPath == null) {
                        System.out.println("Warning: Invalid formRef format for task '" + task.name + "': " + task.formRef);
                        continue;
                    }

                    // Normalize path to resource path
                    String resourcePath;
                    if (formPath.startsWith("forms/")) {
                        resourcePath = "models/form/" + formPath.substring(6);
                    } else {
                        resourcePath = formPath;
                    }

                    // Get variable name from resolvedFormOutputVariableName in BPMN, or fallback to generating from task name
                    String variableName = null;
                    if (bpmnProcess != null && bpmnProcess.nodesById() != null && task.name != null) {
                        org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.FlowNode flowNode = bpmnProcess.nodesById().values().stream()
                                .filter(node -> "userTask".equals(node.type())
                                        && task.name.equals(node.name()))
                                .findFirst()
                                .orElse(null);
                        if (flowNode != null) {
                            variableName = flowNode.resolvedFormOutputVariableName();
                        }
                    }
                    
                    // Fallback to generating variable name from task name if not found in BPMN
                    if (variableName == null || variableName.trim().isEmpty()) {
                        variableName = taskNameToVariableName(task.name);
                    }

                    // Generate output filename from form filename
                    String formFileName = formPath.substring(formPath.lastIndexOf('/') + 1);
                    String outputFileName = formFileName.replace(".json", ".html");

                    // Generate and save the HTML form
                    String generatedPath = generateAndSaveForm(
                            resourcePath,
                            variableName,
                            outputDir,
                            outputFileName
                    );

                    System.out.println("Generated HTML form: " + generatedPath + " (for task: " + task.name + ")");
                    formsGenerated++;

                } catch (Exception e) {
                    System.err.println("Error generating form for task '" + task.name + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        if (formsGenerated > 0) {
            System.out.println("Total HTML forms generated: " + formsGenerated);
        } else {
            System.out.println("No HTML forms generated.");
        }

        return formsGenerated;
    }

    /**
     * Extracts the file path from a formRef string like "${file:forms/request_absence_form.json}".
     */
    private static String extractFormPathFromRef(String formRef) {
        if (formRef == null || !formRef.startsWith("${file:") || !formRef.endsWith("}")) {
            return null;
        }
        return formRef.substring(7, formRef.length() - 1);
    }

    /**
     * Generates a camelCase variable name from a task name.
     * Example: "Request Absence" -> "requestAbsence"
     */
    private static String taskNameToVariableName(String taskName) {
        if (taskName == null || taskName.trim().isEmpty()) {
            return "task";
        }

        // Split by spaces and convert to camelCase
        String[] words = taskName.trim().split("\\s+");
        StringBuilder variableName = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            if (i == 0) {
                variableName.append(word);
            } else {
                if (!word.isEmpty()) {
                    variableName.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) {
                        variableName.append(word.substring(1));
                    }
                }
            }
        }

        return variableName.toString();
    }
}
