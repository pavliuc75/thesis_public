package org.camunda.bpm.getstarted.loanapproval.delegates.validation.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Node;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Process;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class BonitaFormGenerator {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static int generateFormsFromConfigAndBpmn(
            ConfigFile configFileData,
            BpmnData bpmnDefinitions,
            Document procDocument,
            String outputDir,
            List<String> formPaths,
            String assetsDirPath) {

        if (configFileData == null || configFileData.processes == null) {
            System.out.println("No configuration data available for Bonita form generation.");
            return 0;
        }

        if (procDocument == null) {
            System.out.println("No proc document provided for Bonita form generation.");
            return 0;
        }

        int formsGenerated = 0;
        ObjectNode indexJson = mapper.createObjectNode();

        for (Process process : configFileData.processes) {
            if (process.config == null || process.config.tasks == null) {
                continue;
            }

            for (Node task : process.config.tasks) {
                if (task.formRef == null || task.formRef.trim().isEmpty()) {
                    continue;
                }

                Element taskElement = findTaskElementByName(procDocument, task.name);
                if (taskElement == null) {
                    System.out.println("Warning: No task found in proc document for '" + task.name + "'.");
                    continue;
                }

                Element contractElement = findDirectChild(taskElement, "contract");
                if (contractElement == null) {
                    System.out.println("Warning: No contract found for task '" + task.name + "'.");
                    continue;
                }

                Element topInput = findDirectChild(contractElement, "inputs");
                if (topInput == null) {
                    System.out.println("Warning: No contract inputs found for task '" + task.name + "'.");
                    continue;
                }

                String topInputName = topInput.getAttribute("name");
                String variableName = topInput.getAttribute("dataReference");
                if (variableName == null || variableName.isEmpty()) {
                    variableName = deriveVariableNameFromInput(topInputName);
                }
                if (variableName == null || variableName.isEmpty()) {
                    variableName = taskNameToVariableName(task.name);
                }
                if (topInputName == null || topInputName.isEmpty()) {
                    topInputName = variableName + "Input";
                }

                String formPath = extractFormPathFromRef(task.formRef);
                if (formPath == null) {
                    System.out.println("Warning: Invalid formRef format for task '" + task.name + "': " + task.formRef);
                    continue;
                }

                JsonNode schema = loadFormSchema(formPath, formPaths);
                Set<String> requiredPaths = extractRequiredPaths(schema);
                List<FieldInfo> fields = parseContractInputs(topInput, "", requiredPaths);

                String formFileName = Paths.get(formPath).getFileName().toString();
                String formId = toFormId(stripExtension(formFileName));
                String formUuid = UUID.randomUUID().toString();
                ObjectNode formJson = buildFormJson(formId, formUuid, variableName, topInputName, fields);
                String outputFileName = formId + ".json";
                writeFormJson(outputDir, formId, outputFileName, formJson);
                copyAssets(assetsDirPath, outputDir, formId);
                indexJson.put(formUuid, formId);
                formsGenerated++;
            }
        }

        writeIndexJson(outputDir, indexJson);
        return formsGenerated;
    }

    private static Element findTaskElementByName(Document doc, String taskName) {
        if (doc == null || taskName == null || taskName.trim().isEmpty()) {
            return null;
        }

        NodeList elements = doc.getElementsByTagName("elements");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String xmiType = element.getAttribute("xmi:type");
            if (!isUserTaskType(xmiType)) {
                continue;
            }
            String name = element.getAttribute("name");
            if (taskName.equals(name)) {
                return element;
            }
        }

        return null;
    }

    private static boolean isUserTaskType(String xmiType) {
        return "process:Task".equals(xmiType) ||
               "process:UserTask".equals(xmiType) ||
               "process:HumanTask".equals(xmiType);
    }

    private static Element findDirectChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static List<FieldInfo> parseContractInputs(Element parentInput, String prefix, Set<String> requiredPaths) {
        List<FieldInfo> fields = new ArrayList<>();
        NodeList children = parentInput.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if (!"inputs".equals(child.getNodeName())) {
                continue;
            }

            FieldInfo field = new FieldInfo();
            field.name = child.getAttribute("name");
            field.label = toLabel(field.name);
            field.type = child.getAttribute("type");
            String fullPath = prefix.isEmpty() ? field.name : prefix + "." + field.name;
            field.required = requiredPaths.contains(fullPath);

            if ("COMPLEX".equalsIgnoreCase(field.type)) {
                field.relation = true;
                field.children = parseContractInputs(child, fullPath, requiredPaths);
            }

            fields.add(field);
        }

        return fields;
    }

    private static String deriveVariableNameFromInput(String inputName) {
        if (inputName == null || inputName.isEmpty()) {
            return null;
        }
        if (inputName.endsWith("Input") && inputName.length() > "Input".length()) {
            return inputName.substring(0, inputName.length() - "Input".length());
        }
        return inputName;
    }

    private static String extractFormPathFromRef(String formRef) {
        if (formRef == null || !formRef.startsWith("${file:") || !formRef.endsWith("}")) {
            return null;
        }
        return formRef.substring(7, formRef.length() - 1);
    }

    private static JsonNode loadFormSchema(String formPath, List<String> formPaths) {
        if (formPath == null || formPath.isEmpty()) {
            return null;
        }

        String normalizedPath = normalizeFormPath(formPath);
        String resolvedPath = resolveFormPath(normalizedPath, formPaths);

        try {
            if (resolvedPath != null) {
                try (var stream = BonitaFormGenerator.class.getClassLoader().getResourceAsStream(resolvedPath)) {
                    if (stream != null) {
                        return mapper.readTree(stream);
                    }
                }

                Path resourcePath = Paths.get("src/main/resources").resolve(resolvedPath);
                if (Files.exists(resourcePath)) {
                    return mapper.readTree(resourcePath.toFile());
                }
            }

            Path fallbackPath = Paths.get(formPath);
            if (Files.exists(fallbackPath)) {
                return mapper.readTree(fallbackPath.toFile());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load form schema for path: " + formPath, e);
        }

        System.out.println("Warning: Could not resolve form schema for path: " + formPath);
        return null;
    }

    private static String normalizeFormPath(String formPath) {
        if (formPath == null) {
            return null;
        }
        if (formPath.startsWith("forms/")) {
            return "models/form/" + formPath.substring(6);
        }
        return formPath;
    }

    private static String resolveFormPath(String normalizedPath, List<String> formPaths) {
        if (normalizedPath == null || formPaths == null) {
            return normalizedPath;
        }
        String fileName = Paths.get(normalizedPath).getFileName().toString();
        for (String candidate : formPaths) {
            if (candidate.equals(normalizedPath) || candidate.endsWith("/" + fileName)) {
                return candidate;
            }
        }
        return normalizedPath;
    }

    private static Set<String> extractRequiredPaths(JsonNode schema) {
        Set<String> requiredPaths = new HashSet<>();
        if (schema == null) {
            return requiredPaths;
        }
        collectRequiredPaths(schema, "", requiredPaths, schema);
        return requiredPaths;
    }

    private static void collectRequiredPaths(JsonNode node, String basePath, Set<String> requiredPaths, JsonNode root) {
        if (node == null) {
            return;
        }

        JsonNode resolved = resolveSchemaNode(node, root);
        if (resolved == null) {
            return;
        }

        JsonNode requiredNode = resolved.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode req : requiredNode) {
                String name = req.asText();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String path = basePath.isEmpty() ? name : basePath + "." + name;
                requiredPaths.add(path);
            }
        }

        JsonNode properties = resolved.get("properties");
        if (properties == null || !properties.isObject()) {
            return;
        }

        properties.fieldNames().forEachRemaining(fieldName -> {
            JsonNode propertyNode = properties.get(fieldName);
            if (propertyNode == null) {
                return;
            }
            JsonNode resolvedProperty = resolveSchemaNode(propertyNode, root);
            if (resolvedProperty == null) {
                return;
            }
            if (resolvedProperty.has("properties") || resolvedProperty.has("required")) {
                String nextPath = basePath.isEmpty() ? fieldName : basePath + "." + fieldName;
                collectRequiredPaths(resolvedProperty, nextPath, requiredPaths, root);
            }
        });
    }

    private static JsonNode resolveSchemaNode(JsonNode node, JsonNode root) {
        if (node == null) {
            return null;
        }
        JsonNode refNode = node.get("$ref");
        if (refNode == null) {
            return node;
        }
        String ref = refNode.asText();
        if (ref == null || !ref.startsWith("#/")) {
            return node;
        }
        String[] segments = ref.substring(2).split("/");
        JsonNode current = root;
        for (String segment : segments) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private static void writeFormJson(String outputDir, String formId, String outputFileName, ObjectNode formJson) {
        try {
            Path outputDirPath = Paths.get(outputDir).resolve(formId);
            Files.createDirectories(outputDirPath);

            Path outputPath = outputDirPath.resolve(outputFileName);

            mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), formJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write Bonita form JSON.", e);
        }
    }

    private static void copyAssets(String assetsDirPath, String outputDir, String formId) {
        if (assetsDirPath == null || assetsDirPath.trim().isEmpty()) {
            return;
        }

        Path sourceDir = Paths.get(assetsDirPath);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.out.println("Warning: Assets directory not found: " + assetsDirPath);
            return;
        }

        Path targetDir = Paths.get(outputDir).resolve(formId).resolve("assets");
        try {
            Files.createDirectories(targetDir);
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.filter(Files::isRegularFile).forEach(sourceFile -> {
                    Path relative = sourceDir.relativize(sourceFile);
                    Path targetFile = targetDir.resolve(relative);
                    try {
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to copy asset: " + sourceFile, e);
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy assets into Bonita form directory.", e);
        }
    }

    private static void writeIndexJson(String outputDir, ObjectNode indexJson) {
        try {
            Path outputDirPath = Paths.get(outputDir);
            Files.createDirectories(outputDirPath);
            Path indexPath = outputDirPath.resolve(".index.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), indexJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write Bonita forms index file.", e);
        }
    }

    private static ObjectNode buildFormJson(String formId, String formUuid, String variableName, String topInputName,
                                            List<FieldInfo> fields) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", formId);
        root.put("name", formId);
        root.put("type", "form");
        root.put("uuid", formUuid);
        root.put("modelVersion", "2.6");
        root.put("lastUpdate", Instant.now().toEpochMilli());
        root.put("description", "Page generated with Bonita UI designer");

        ArrayNode rows = mapper.createArrayNode();
        rows.add(buildHeaderRow());
        rows.add(buildFormRow(variableName, fields));
        root.set("rows", rows);

        root.set("variables", buildVariables(variableName, topInputName, fields));
        root.set("assets", buildAssets());
        root.set("inactiveAssets", mapper.createArrayNode());
        root.set("webResources", mapper.createArrayNode());
        root.put("hasValidationError", false);

        return root;
    }

    private static ArrayNode buildHeaderRow() {
        ArrayNode row = mapper.createArrayNode();
        ObjectNode headerContainer = createContainer(xsDimension(), mapper.createObjectNode());

        ArrayNode headerRows = mapper.createArrayNode();
        headerRows.add(wrapInRow(createTitleComponent("{{ task.displayName }}", "Level 1", "center")));
        headerRows.add(wrapInRow(createTextComponent("{{ task.displayDescription }}")));
        headerContainer.set("rows", headerRows);

        row.add(headerContainer);
        return row;
    }

    private static ArrayNode buildFormRow(String variableName, List<FieldInfo> fields) {
        ArrayNode row = mapper.createArrayNode();
        ObjectNode formContainer = mapper.createObjectNode();
        formContainer.put("type", "formContainer");
        formContainer.set("dimension", xsDimension());
        formContainer.set("propertyValues", mapper.createObjectNode());
        formContainer.put("reference", UUID.randomUUID().toString());
        formContainer.put("hasValidationError", false);
        formContainer.put("id", "pbFormContainer");

        ObjectNode innerContainer = createContainer(xsDimension(), mapper.createObjectNode());
        ArrayNode rows = mapper.createArrayNode();

        rows.add(wrapInRow(createTitleComponent(toLabel(variableName), "Level 4", "left")));

        ObjectNode fieldsContainer = createContainer(fullDimension(), buildRepeatedContainerProps());
        ArrayNode fieldRows = mapper.createArrayNode();
        for (FieldInfo field : fields) {
            if (field.relation) {
                fieldRows.add(wrapInRow(createTitleComponent(field.label, "Level 4", "left")));
                ObjectNode nestedContainer = createContainer(fullDimension(), buildRepeatedContainerProps());
                ArrayNode nestedRows = mapper.createArrayNode();
                String nestedVarName = variableName + "_" + field.name;
                for (FieldInfo nestedField : field.children) {
                    nestedRows.add(wrapInRow(createInputForField(nestedField, nestedVarName + "." + nestedField.name)));
                }
                nestedContainer.set("rows", nestedRows);
                fieldRows.add(wrapInRow(nestedContainer));
            } else {
                fieldRows.add(wrapInRow(createInputForField(field, variableName + "." + field.name)));
            }
        }
        fieldsContainer.set("rows", fieldRows);
        rows.add(wrapInRow(fieldsContainer));

        rows.add(wrapInRow(createSubmitButtonComponent()));
        rows.add(wrapInRow(createSubmitErrorComponent()));

        innerContainer.set("rows", rows);
        formContainer.set("container", innerContainer);
        row.add(formContainer);
        return row;
    }

    private static ObjectNode buildVariables(String variableName, String topInputName, List<FieldInfo> fields) {
        ObjectNode variables = mapper.createObjectNode();

        variables.set("task", createUrlVariable("../API/bpm/userTask/{{taskId}}"));
        variables.set("submit_errors_list", createSubmitErrorsList());
        variables.set("formOutput", createFormOutput(variableName, topInputName, fields));
        variables.set("context", createUrlVariable("../API/bpm/userTask/{{taskId}}/context"));
        variables.set(variableName, createUrlVariable("../{{context." + variableName + "_ref.link}}"));
        variables.set("taskId", createUrlParameterVariable("id"));

        for (FieldInfo field : fields) {
            if (!field.relation) {
                continue;
            }
            String nestedVar = variableName + "_" + field.name;
            variables.set(nestedVar, createUrlVariable("{{" + variableName + "|lazyRef:'" + field.name + "'}}"));
        }

        return variables;
    }

    private static ObjectNode createFormOutput(String variableName, String topInputName, List<FieldInfo> fields) {
        ObjectNode formOutput = mapper.createObjectNode();
        formOutput.put("type", "expression");
        ArrayNode lines = mapper.createArrayNode();

        List<FieldInfo> relationFields = new ArrayList<>();
        for (FieldInfo field : fields) {
            if (field.relation) {
                relationFields.add(field);
            }
        }

        String condition = "$data." + variableName;
        for (FieldInfo relationField : relationFields) {
            condition += " && $data." + variableName + "_" + relationField.name;
        }

        lines.add("if( " + condition + " ){");
        if (!relationFields.isEmpty()) {
            lines.add("\t//attach lazy references variables to parent variables");
            for (FieldInfo relationField : relationFields) {
                lines.add("\t$data." + variableName + "." + relationField.name + " = $data." + variableName + "_" + relationField.name + ";");
            }
        }
        lines.add("\treturn {");
        lines.add("\t\t//map " + variableName + " variable to expected task contract input");
        lines.add("\t\t" + topInputName + ": {");

        for (int i = 0; i < fields.size(); i++) {
            FieldInfo field = fields.get(i);
            boolean isLast = i == fields.size() - 1;
            if (field.relation) {
                lines.add("\t\t\t" + field.name + ": $data." + variableName + "." + field.name + " ? {");
                for (int j = 0; j < field.children.size(); j++) {
                    FieldInfo nested = field.children.get(j);
                    boolean nestedLast = j == field.children.size() - 1;
                    String line = "\t\t\t\t" + nested.name + ": $data." + variableName + "." + field.name + "." + nested.name
                            + " !== undefined ? $data." + variableName + "." + field.name + "." + nested.name + " : null";
                    if (!nestedLast) {
                        line += ",";
                    }
                    lines.add(line);
                }
                String closing = "\t\t\t} : null";
                if (!isLast) {
                    closing += ",";
                }
                lines.add(closing);
            } else {
                String line = "\t\t\t" + field.name + ": $data." + variableName + "." + field.name
                        + " !== undefined ? $data." + variableName + "." + field.name + " : null";
                if (!isLast) {
                    line += ",";
                }
                lines.add(line);
            }
        }

        lines.add("\t\t}");
        lines.add("\t}");
        lines.add("}");

        formOutput.set("value", lines);
        formOutput.put("exposed", false);
        return formOutput;
    }

    private static ObjectNode createUrlVariable(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "url");
        ArrayNode values = mapper.createArrayNode();
        values.add(value);
        node.set("value", values);
        node.put("exposed", false);
        return node;
    }

    private static ObjectNode createUrlParameterVariable(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "urlparameter");
        ArrayNode values = mapper.createArrayNode();
        values.add(value);
        node.set("value", values);
        node.put("exposed", false);
        return node;
    }

    private static ObjectNode createSubmitErrorsList() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "expression");
        ArrayNode values = mapper.createArrayNode();
        values.add("if($data.formOutput && $data.formOutput._submitError && $data.formOutput._submitError.explanations){");
        values.add("\tconst liElements = $data.formOutput._submitError.explanations");
        values.add("\t\t.filter(cause => cause !== null)");
        values.add("\t\t.map(cause => \"<li>\" + cause + \"</li>\")");
        values.add("\t\t.join('');");
        values.add("\tif(liElements){");
        values.add("\t\treturn \"<ul>\" + liElements + \"</ul>\";");
        values.add("\t}");
        values.add("}");
        node.set("value", values);
        node.put("exposed", false);
        return node;
    }

    private static ArrayNode buildAssets() {
        ArrayNode assets = mapper.createArrayNode();
        assets.add(createAsset("localization.json", "json"));
        assets.add(createAsset("style.css", "css"));
        return assets;
    }

    private static ObjectNode createAsset(String name, String type) {
        ObjectNode asset = mapper.createObjectNode();
        asset.put("id", UUID.randomUUID().toString());
        asset.put("name", name);
        asset.put("type", type);
        asset.put("order", 0);
        asset.put("external", false);
        return asset;
    }

    private static ObjectNode createTitleComponent(String textValue, String level, String alignment) {
        ObjectNode props = mapper.createObjectNode();
        props.set("hidden", createConstant(false));
        props.set("level", createConstant(level));
        props.set("cssClasses", createConstant(""));
        props.set("text", createInterpolation(textValue));
        props.set("alignment", createConstant(alignment));
        props.set("dimension", createConstant(12));

        return createComponent("pbTitle", props);
    }

    private static ObjectNode createTextComponent(String textValue) {
        ObjectNode props = mapper.createObjectNode();
        props.set("allowHtml", createConstant(true));
        props.set("labelHidden", createConstant(true));
        props.set("hidden", createConstant(false));
        props.set("cssClasses", createConstant(""));
        props.set("label", createInterpolation());
        props.set("text", createInterpolation(textValue));
        props.set("alignment", createConstant("left"));
        props.set("dimension", createConstant(12));

        return createComponent("pbText", props);
    }

    private static ObjectNode createInputForField(FieldInfo field, String valuePath) {
        String widgetType = mapFieldToWidget(field);
        switch (widgetType) {
            case "checkbox" -> {
                return createCheckboxComponent(field.label, valuePath);
            }
            case "date" -> {
                return createDatePickerComponent(field.label, valuePath, field.required);
            }
            case "datetime" -> {
                return createDateTimePickerComponent(field.label, valuePath, false, field.required);
            }
            case "offsetdatetime" -> {
                return createDateTimePickerComponent(field.label, valuePath, true, field.required);
            }
            default -> {
                String inputType = "number".equals(widgetType) ? "number" : "text";
                return createTextInputComponent(field.label, valuePath, inputType, field.required);
            }
        }
    }

    private static ObjectNode createTextInputComponent(String label, String valuePath, String inputType, boolean required) {
        ObjectNode props = mapper.createObjectNode();
        props.set("labelHidden", createConstant(false));
        props.set("hidden", createConstant(false));
        props.set("labelPosition", createConstant("top"));
        props.set("cssClasses", createConstant(""));
        props.set("labelWidth", createConstant(1));
        props.set("readOnly", createConstant(false));
        props.set("label", createInterpolation(label));
        props.set("placeholder", createConstant());
        props.set("type", createConstant(inputType));
        props.set("dimension", createConstant(12));
        props.set("value", createVariable(valuePath));
        props.set("required", createConstant(required));

        return createComponent("pbInput", props);
    }

    private static ObjectNode createCheckboxComponent(String label, String valuePath) {
        ObjectNode props = mapper.createObjectNode();
        props.set("labelHidden", createConstant(false));
        props.set("hidden", createConstant(false));
        props.set("cssClasses", createConstant(""));
        props.set("disabled", createConstant(false));
        props.set("label", createInterpolation(label));
        props.set("dimension", createConstant(12));
        props.set("value", createVariable(valuePath));

        return createComponent("pbCheckbox", props);
    }

    private static ObjectNode createDatePickerComponent(String label, String valuePath, boolean required) {
        ObjectNode props = mapper.createObjectNode();
        props.set("hidden", createConstant(false));
        props.set("dateFormat", createConstant("MM/dd/yyyy"));
        props.set("cssClasses", createConstant(""));
        props.set("labelWidth", createConstant(1));
        props.set("readOnly", createConstant(false));
        props.set("label", createInterpolation(label));
        props.set("type", createConstant());
        props.set("required", createConstant(required));
        props.set("todayLabel", createConstant("Today"));
        props.set("labelHidden", createConstant(false));
        props.set("showToday", createConstant(true));
        props.set("labelPosition", createConstant("top"));
        props.set("placeholder", createConstant("Enter a date (mm/dd/yyyy)"));
        props.set("dimension", createConstant(12));
        props.set("value", createVariable(valuePath));

        return createComponent("pbDatePicker", props);
    }

    private static ObjectNode createDateTimePickerComponent(String label, String valuePath, boolean withTimeZone,
                                                            boolean required) {
        ObjectNode props = mapper.createObjectNode();
        props.set("showNow", createConstant(true));
        props.set("hidden", createConstant(false));
        props.set("inlineInput", createConstant(true));
        props.set("dateFormat", createConstant("MM/dd/yyyy"));
        props.set("cssClasses", createConstant(""));
        props.set("labelWidth", createConstant(1));
        props.set("readOnly", createConstant(false));
        props.set("label", createInterpolation(label));
        props.set("type", createConstant());
        props.set("required", createConstant(required));
        props.set("todayLabel", createConstant("Today"));
        props.set("timePlaceholder", createConstant("Enter a time (h:mm:ss a)"));
        props.set("labelHidden", createConstant(false));
        props.set("showToday", createConstant(true));
        props.set("labelPosition", createConstant("top"));
        props.set("timeFormat", createConstant("h:mm:ss a"));
        props.set("placeholder", createConstant("Enter a date (mm/dd/yyyy)"));
        props.set("nowLabel", createConstant("Now"));
        props.set("dimension", createConstant(12));
        props.set("value", createVariable(valuePath));
        props.set("withTimeZone", createConstant(withTimeZone));

        return createComponent("pbDateTimePicker", props);
    }

    private static ObjectNode createSubmitButtonComponent() {
        ObjectNode props = mapper.createObjectNode();
        props.set("removeItem", createVariable());
        props.set("hidden", createConstant(false));
        props.set("cssClasses", createConstant(""));
        props.set("buttonStyle", createConstant("primary"));
        props.set("label", createInterpolation("Submit"));
        props.set("dataToSend", createExpression("formOutput"));
        props.set("dataFromError", createVariable("formOutput._submitError"));
        props.set("allowHTML", createConstant(false));
        props.set("labelHidden", createConstant(false));
        props.set("collectionPosition", createConstant());
        props.set("targetUrlOnSuccess", createInterpolation("/bonita"));
        props.set("action", createConstant("Submit task"));
        props.set("collectionToModify", createVariable());
        props.set("valueToAdd", createExpression());
        props.set("disabled", createExpression("$form.$invalid"));
        props.set("alignment", createConstant("center"));
        props.set("dimension", createConstant(12));

        return createComponent("pbButton", props);
    }

    private static ObjectNode createSubmitErrorComponent() {
        ObjectNode props = mapper.createObjectNode();
        props.set("allowHTML", createConstant(true));
        props.set("allowHtml", createConstant(true));
        props.set("labelHidden", createConstant(true));
        props.set("hidden", createExpression("!formOutput._submitError.message"));
        props.set("cssClasses", createConstant("alert alert-danger col-lg-6 col-lg-offset-3"));
        props.set("label", createInterpolation());
        props.set("text", createInterpolation("<strong>Debug message</strong>\n<br/>\n{{formOutput._submitError.message}}\n{{submit_errors_list}}"));
        props.set("alignment", createConstant("left"));
        props.set("dimension", createConstant(12));

        return createComponent("pbText", props);
    }

    private static ObjectNode createComponent(String id, ObjectNode propertyValues) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "component");
        node.set("dimension", fullDimension());
        node.set("propertyValues", propertyValues);
        node.put("reference", UUID.randomUUID().toString());
        node.put("hasValidationError", false);
        node.put("id", id);
        node.put("description", "");
        return node;
    }

    private static ObjectNode createContainer(ObjectNode dimension, ObjectNode propertyValues) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "container");
        node.set("dimension", dimension);
        node.set("propertyValues", propertyValues);
        node.put("reference", UUID.randomUUID().toString());
        node.put("hasValidationError", false);
        node.put("id", "pbContainer");
        return node;
    }

    private static ObjectNode buildRepeatedContainerProps() {
        ObjectNode props = mapper.createObjectNode();
        props.set("repeatedCollection", createVariable());
        props.set("hidden", createConstant(false));
        props.set("cssClasses", createConstant(""));
        props.set("dimension", createConstant(12));
        return props;
    }

    private static ArrayNode wrapInRow(ObjectNode component) {
        ArrayNode row = mapper.createArrayNode();
        row.add(component);
        return row;
    }

    private static ObjectNode xsDimension() {
        ObjectNode dimension = mapper.createObjectNode();
        dimension.put("xs", 12);
        return dimension;
    }

    private static ObjectNode fullDimension() {
        ObjectNode dimension = mapper.createObjectNode();
        dimension.put("md", 12);
        dimension.put("sm", 12);
        dimension.put("xs", 12);
        dimension.put("lg", 12);
        return dimension;
    }

    private static ObjectNode createConstant() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "constant");
        return node;
    }

    private static ObjectNode createConstant(Object value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "constant");
        if (value instanceof Boolean bool) {
            node.put("value", bool);
        } else if (value instanceof Integer i) {
            node.put("value", i);
        } else if (value instanceof Long l) {
            node.put("value", l);
        } else if (value instanceof String s) {
            node.put("value", s);
        }
        return node;
    }

    private static ObjectNode createInterpolation() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "interpolation");
        return node;
    }

    private static ObjectNode createInterpolation(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "interpolation");
        node.put("value", value);
        return node;
    }

    private static ObjectNode createVariable() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "variable");
        return node;
    }

    private static ObjectNode createVariable(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "variable");
        node.put("value", value);
        return node;
    }

    private static ObjectNode createExpression() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "expression");
        return node;
    }

    private static ObjectNode createExpression(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "expression");
        node.put("value", value);
        return node;
    }

    private static String mapFieldToWidget(FieldInfo field) {
        String type = field.type != null ? field.type.toUpperCase(Locale.ROOT) : "";

        if ("BOOLEAN".equals(type)) {
            return "checkbox";
        }
        if ("LOCALDATE".equals(type)) {
            return "date";
        }
        if ("LOCALDATETIME".equals(type)) {
            return "datetime";
        }
        if ("OFFSETDATETIME".equals(type)) {
            return "offsetdatetime";
        }
        if ("DECIMAL".equals(type) || "INTEGER".equals(type)) {
            return "number";
        }
        if ("LONG".equals(type)) {
            return "text";
        }
        return "text";
    }

    private static String taskNameToVariableName(String taskName) {
        if (taskName == null || taskName.trim().isEmpty()) {
            return "task";
        }

        String[] words = taskName.trim().split("\\s+");
        StringBuilder variableName = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase(Locale.ROOT);
            if (i == 0) {
                variableName.append(word);
            } else {
                variableName.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    variableName.append(word.substring(1));
                }
            }
        }

        return variableName.toString();
    }

    private static String toLabel(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String withSpaces = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return withSpaces.substring(0, 1).toUpperCase(Locale.ROOT) + withSpaces.substring(1);
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx == -1) {
            return fileName;
        }
        return fileName.substring(0, idx);
    }

    private static String toFormId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "form";
        }

        String normalized = value.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return "form";
        }

        String[] parts = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (i == 0) {
                result.append(part.substring(0, 1).toLowerCase(Locale.ROOT));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            } else {
                result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }

        if (result.length() == 0) {
            return "form";
        }

        return result.toString();
    }

    private static class FieldInfo {
        String name;
        String label;
        String type;
        boolean required;
        boolean relation;
        List<FieldInfo> children = new ArrayList<>();
    }
}
