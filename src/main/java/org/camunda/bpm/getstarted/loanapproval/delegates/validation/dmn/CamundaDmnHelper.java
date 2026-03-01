package org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for Camunda-specific DMN transformations.
 */
public class CamundaDmnHelper {
    private static final Pattern PRE_RUNTIME_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");
    private static final Pattern CAMUNDA_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final String CAMUNDA_DMN_NS = "http://camunda.org/schema/1.0/dmn";

    /**
     * Resolves DMN expressions for Camunda.
     * Converts ${~variable.field~} to ${variable_field}.
     *
     * @param processedDmnFiles map of file names to processed DMN content strings
     * @return updated map with Camunda-compatible expressions
     */
    public static Map<String, String> resolveDmnExpressionsForCamunda(Map<String, String> processedDmnFiles) {
        Map<String, String> resolvedDmnFiles = new HashMap<>();
        if (processedDmnFiles == null || processedDmnFiles.isEmpty()) {
            return resolvedDmnFiles;
        }

        for (Map.Entry<String, String> entry : processedDmnFiles.entrySet()) {
            String fileName = entry.getKey();
            String xmlContent = entry.getValue();
            if (xmlContent == null) {
                continue;
            }

            try {
                Document doc = parseDmnDocument(xmlContent);
                processNode(doc.getDocumentElement());
                ensureCamundaNamespace(doc);
                addCamundaInputVariables(doc);
                String processedXml = writeDmnDocumentToString(doc);
                resolvedDmnFiles.put(fileName, processedXml);
            } catch (Exception e) {
                System.err.println("Error processing DMN content for '" + fileName + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        return resolvedDmnFiles;
    }

    /**
     * Adds Camunda-specific attributes to DMN decision elements.
     * Sets camunda:historyTimeToLive="P180D".
     *
     * @param processedDmnFiles map of file names to processed DMN content strings
     * @return updated map with Camunda-specific attributes applied
     */
    public static Map<String, String> generateCamundaSpecificAttributesInDmnFiles(
            Map<String, String> processedDmnFiles) {
        Map<String, String> updatedDmnFiles = new HashMap<>();
        if (processedDmnFiles == null || processedDmnFiles.isEmpty()) {
            return updatedDmnFiles;
        }

        for (Map.Entry<String, String> entry : processedDmnFiles.entrySet()) {
            String fileName = entry.getKey();
            String xmlContent = entry.getValue();
            if (xmlContent == null) {
                continue;
            }

            try {
                Document doc = parseDmnDocument(xmlContent);
                ensureCamundaNamespace(doc);

                String dmnNamespace = doc.getDocumentElement() != null
                        ? doc.getDocumentElement().getNamespaceURI()
                        : null;

                NodeList decisionNodes = dmnNamespace != null && !dmnNamespace.trim().isEmpty()
                        ? doc.getElementsByTagNameNS(dmnNamespace, "decision")
                        : doc.getElementsByTagName("decision");

                for (int i = 0; i < decisionNodes.getLength(); i++) {
                    Node decisionNode = decisionNodes.item(i);
                    if (decisionNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element decisionEl = (Element) decisionNode;
                    decisionEl.setAttributeNS(CAMUNDA_DMN_NS, "camunda:historyTimeToLive", "P180D");
                }

                String processedXml = writeDmnDocumentToString(doc);
                updatedDmnFiles.put(fileName, processedXml);
            } catch (Exception e) {
                System.err.println("Error processing DMN content for '" + fileName + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        return updatedDmnFiles;
    }

    private static Document parseDmnDocument(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DMN XML content.", e);
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

    private static void processNode(Node node) {
        if (node == null) {
            return;
        }

        if (node.hasAttributes()) {
            NamedNodeMap attributes = node.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String value = attr.getNodeValue();
                if (value != null && value.contains("${~")) {
                    attr.setNodeValue(resolveCamundaExpression(value));
                }
            }
        }

        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String text = node.getNodeValue();
            if (text != null && text.contains("${~")) {
                node.setNodeValue(resolveCamundaExpression(text));
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            processNode(children.item(i));
        }
    }

    private static String resolveCamundaExpression(String text) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        String resolved = text;

        Matcher matcher = PRE_RUNTIME_EXPRESSION_PATTERN.matcher(resolved);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String flattened = expression.replace('.', '_');
            matcher.appendReplacement(result, Matcher.quoteReplacement(flattened));
        }
        matcher.appendTail(result);

        resolved = result.toString();

        matcher = CAMUNDA_EXPRESSION_PATTERN.matcher(resolved);
        result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String flattened = expression.replace('.', '_');
            matcher.appendReplacement(result, Matcher.quoteReplacement(flattened));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static void ensureCamundaNamespace(Document doc) {
        if (doc == null || doc.getDocumentElement() == null) {
            return;
        }

        Element definitionsEl = doc.getDocumentElement();
        if (!definitionsEl.hasAttribute("xmlns:camunda")) {
            definitionsEl.setAttribute("xmlns:camunda", CAMUNDA_DMN_NS);
        }
    }

    private static void addCamundaInputVariables(Document doc) {
        if (doc == null || doc.getDocumentElement() == null) {
            return;
        }

        String dmnNamespace = doc.getDocumentElement().getNamespaceURI();
        if (dmnNamespace == null || dmnNamespace.trim().isEmpty()) {
            return;
        }

        NodeList inputNodes = doc.getElementsByTagNameNS(dmnNamespace, "input");
        for (int i = 0; i < inputNodes.getLength(); i++) {
            Node inputNode = inputNodes.item(i);
            if (inputNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element inputEl = (Element) inputNode;
            NodeList inputExpressionNodes = inputEl.getElementsByTagNameNS(dmnNamespace, "inputExpression");
            if (inputExpressionNodes.getLength() == 0) {
                continue;
            }

            Element inputExpressionEl = (Element) inputExpressionNodes.item(0);
            NodeList textNodes = inputExpressionEl.getElementsByTagNameNS(dmnNamespace, "text");
            if (textNodes.getLength() == 0) {
                continue;
            }

            String expression = textNodes.item(0).getTextContent();
            if (expression == null) {
                continue;
            }

            String normalizedExpression = resolveCamundaExpression(expression).trim();
            if (normalizedExpression.isEmpty()) {
                continue;
            }

            inputEl.setAttributeNS(CAMUNDA_DMN_NS, "camunda:inputVariable", normalizedExpression);
        }
    }
}
