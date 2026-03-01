package org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.ConfigFile;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig.models.Variable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DmnHelper {
    private static final Set<String> KNOWN_DMN_NAMESPACES = Set.of(
            "https://www.omg.org/spec/DMN/20191111/MODEL/",
            "https://www.omg.org/spec/DMN/20180521/MODEL/",
            "https://www.omg.org/spec/DMN/20151101/MODEL/",
            "https://www.omg.org/spec/DMN/20151101/dmn.xsd",
            "http://www.omg.org/spec/DMN/20151101/dmn.xsd"
    );
    private static final Pattern PRE_RUNTIME_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");

    public static void validate(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("DMN path is required.");
        }

        try (InputStream inputStream = openDmnStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);

            Element root = doc.getDocumentElement();
            if (root == null) {
                throw new IllegalArgumentException("DMN XML is empty.");
            }

            String rootLocalName = root.getLocalName();
            if (!"definitions".equals(rootLocalName)) {
                throw new IllegalArgumentException("DMN root element must be <definitions>, found <"
                        + root.getNodeName() + ">.");
            }

            String dmnNamespace = root.getNamespaceURI();
            if (dmnNamespace == null || dmnNamespace.trim().isEmpty()) {
                throw new IllegalArgumentException("DMN definitions element must declare a DMN namespace.");
            }

            if (!isKnownDmnNamespace(dmnNamespace)) {
                throw new IllegalArgumentException("Unrecognized DMN namespace: " + dmnNamespace);
            }

            NodeList decisions = doc.getElementsByTagNameNS(dmnNamespace, "decision");
            if (decisions.getLength() == 0) {
                decisions = doc.getElementsByTagName("decision");
            }
            if (decisions.getLength() == 0) {
                throw new IllegalArgumentException("DMN must contain at least one <decision> element.");
            }

            validateDecisionElements(decisions);

            System.out.println("✅ DMN file is valid XML and looks like a DMN definitions document.");
        } catch (Exception e) {
            throw new RuntimeException("DMN validation failed for '" + path + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resolves global variables in DMN files.
     * Replaces ${~globalVariables.xxx~} with the actual value from config.
     * Leaves other ${~...~} expressions unchanged.
     *
     * @param dmnPaths list of DMN file paths
     * @param configFileData the config file containing globalVariables
     * @return map of original DMN file names to processed DMN content strings
     */
    public static Map<String, String> resolveGlobalVariablesInDmnFiles(List<String> dmnPaths,
                                                                       ConfigFile configFileData) {
        Map<String, String> processedDmnFiles = new HashMap<>();
        if (dmnPaths == null || dmnPaths.isEmpty()) {
            return processedDmnFiles;
        }

        Map<String, String> globalVarsMap = new HashMap<>();
        if (configFileData != null && configFileData.globalVariables != null) {
            for (Variable var : configFileData.globalVariables) {
                if (var.name != null && var.value != null) {
                    globalVarsMap.put(var.name, var.value);
                }
            }
        }

        for (String dmnPath : dmnPaths) {
            if (dmnPath == null || dmnPath.trim().isEmpty()) {
                continue;
            }

            try {
                String resourcePath = dmnPath.startsWith("src/main/resources/")
                        ? dmnPath
                        : "src/main/resources/" + dmnPath;

                File dmnFile = new File(resourcePath);
                if (!dmnFile.exists()) {
                    System.err.println("Warning: DMN file not found: " + resourcePath);
                    continue;
                }

                Document doc = parseDmnDocument(dmnFile);
                processNode(doc.getDocumentElement(), globalVarsMap);
                String processedXml = writeDmnDocumentToString(doc);
                processedDmnFiles.put(dmnFile.getName(), processedXml);
            } catch (Exception e) {
                System.err.println("Error processing DMN file '" + dmnPath + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        return processedDmnFiles;
    }

    /**
     * Saves processed DMN files to the output directory.
     *
     * @param processedDmnFiles map of file names to processed DMN content strings
     * @param outputDir the output directory path (e.g., "src/main/resources/out")
     */
    public static void saveProcessedDmnFiles(Map<String, String> processedDmnFiles, String outputDir) {
        if (processedDmnFiles == null || processedDmnFiles.isEmpty()) {
            return;
        }

        try {
            Path outputDirPath = Paths.get(outputDir);
            Files.createDirectories(outputDirPath);

            for (Map.Entry<String, String> entry : processedDmnFiles.entrySet()) {
                String fileName = entry.getKey();
                String xmlContent = entry.getValue();
                if (xmlContent == null) {
                    continue;
                }

                Path outputPath = outputDirPath.resolve(fileName);
                Files.write(outputPath, xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("Processed DMN: " + outputPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save processed DMN files: " + e.getMessage(), e);
        }
    }

    private static Document parseDmnDocument(File dmnFile) {
        try (InputStream inputStream = Files.newInputStream(dmnFile.toPath())) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DMN file: " + dmnFile.getPath(), e);
        }
    }

    private static String writeDmnDocumentToString(Document doc) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(stringWriter));

            String xmlContent = stringWriter.toString();
            xmlContent = xmlContent.replaceAll(" standalone=\"yes\"", "");
            xmlContent = xmlContent.replaceAll(" standalone=\"no\"", "");
            xmlContent = xmlContent.replaceAll("(\r?\n)\\s*\r?\n", "$1");

            return xmlContent;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write DMN document to string.", e);
        }
    }

    private static void processNode(Node node, Map<String, String> globalVarsMap) {
        if (node == null) {
            return;
        }

        if (node.hasAttributes()) {
            NamedNodeMap attributes = node.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String value = attr.getNodeValue();
                if (value != null && value.contains("${~")) {
                    attr.setNodeValue(resolveGlobalVariablesInExpression(value, globalVarsMap));
                }
            }
        }

        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String text = node.getNodeValue();
            if (text != null && text.contains("${~")) {
                node.setNodeValue(resolveGlobalVariablesInExpression(text, globalVarsMap));
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            processNode(children.item(i), globalVarsMap);
        }
    }

    private static String resolveGlobalVariablesInExpression(String text, Map<String, String> globalVarsMap) {
        if (text == null || !text.contains("${~")) {
            return text;
        }

        Matcher matcher = PRE_RUNTIME_EXPRESSION_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
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

    private static InputStream openDmnStream(String path) {
        ClassLoader cl = DmnHelper.class.getClassLoader();
        InputStream resourceStream = cl.getResourceAsStream(path);
        if (resourceStream != null) {
            return resourceStream;
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            Path resourcePath = Paths.get("src/main/resources", path);
            if (Files.exists(resourcePath)) {
                filePath = resourcePath;
            }
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("DMN file not found: " + path);
        }

        try {
            return Files.newInputStream(filePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read DMN file: " + path, e);
        }
    }

    private static boolean isKnownDmnNamespace(String namespace) {
        if (KNOWN_DMN_NAMESPACES.contains(namespace)) {
            return true;
        }
        String normalized = namespace.toLowerCase();
        return normalized.contains("omg.org/spec/dmn") && (normalized.contains("model") || normalized.contains("dmn.xsd"));
    }

    private static void validateDecisionElements(NodeList decisions) {
        for (int i = 0; i < decisions.getLength(); i++) {
            Node node = decisions.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element decisionEl = (Element) node;

            String decisionId = decisionEl.getAttribute("id");
            if (decisionId == null || decisionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Decision element is missing required id attribute.");
            }

            boolean hasDecisionTable = hasChildWithLocalName(decisionEl, "decisionTable");
            boolean hasLiteralExpression = hasChildWithLocalName(decisionEl, "literalExpression");
            if (!hasDecisionTable && !hasLiteralExpression) {
                throw new IllegalArgumentException("Decision '" + decisionId
                        + "' must contain a decisionTable or literalExpression.");
            }
        }
    }

    private static boolean hasChildWithLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            String childLocalName = child.getLocalName();
            if (localName.equals(childLocalName)) {
                return true;
            }
            String nodeName = child.getNodeName();
            if (nodeName != null && nodeName.endsWith(":" + localName)) {
                return true;
            }
        }
        return false;
    }
}
