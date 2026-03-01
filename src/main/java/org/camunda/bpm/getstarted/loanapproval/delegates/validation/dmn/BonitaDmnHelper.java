package org.camunda.bpm.getstarted.loanapproval.delegates.validation.dmn;

import org.w3c.dom.Document;
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
 * Helper class for Bonita-specific DMN transformations.
 */
public class BonitaDmnHelper {
    private static final Pattern TILDE_EXPRESSION_PATTERN = Pattern.compile("\\$\\{~([^}]+)~\\}");
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * Resolves DMN expressions for Bonita by removing ${...} wrappers.
     *
     * @param processedDmnFiles map of file names to processed DMN content strings
     * @return updated map with Bonita-compatible expressions
     */
    public static Map<String, String> resolveExpressionsInDmn(Map<String, String> processedDmnFiles) {
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
                String processedXml = writeDmnDocumentToString(doc);
                resolvedDmnFiles.put(fileName, processedXml);
            } catch (Exception e) {
                System.err.println("Error processing DMN content for '" + fileName + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        return resolvedDmnFiles;
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
                if (value != null && value.contains("${")) {
                    attr.setNodeValue(stripExpressionWrappers(value));
                }
            }
        }

        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String text = node.getNodeValue();
            if (text != null && text.contains("${")) {
                node.setNodeValue(stripExpressionWrappers(text));
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            processNode(children.item(i));
        }
    }

    private static String stripExpressionWrappers(String text) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        Matcher matcher = TILDE_EXPRESSION_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(expression));
        }
        matcher.appendTail(result);

        String stripped = result.toString();

        matcher = EXPRESSION_PATTERN.matcher(stripped);
        result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(expression));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
