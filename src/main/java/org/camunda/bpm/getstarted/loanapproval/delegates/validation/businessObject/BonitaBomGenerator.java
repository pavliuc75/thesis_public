package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlClass;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.BusinessData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models.PumlField;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BonitaBomGenerator {
    
    private static final String BOM_NS = "http://documentation.bonitasoft.com/bdm-xml-schema/1.0";

    /**
     * Generates a Bonita Business Object Model (BOM) file from PUML business object data.
     *
     * @param pumlDiagram   the PUML diagram containing business objects
     * @param outputDirPath the output directory path
     */
    public static void generateBomFile(BusinessData pumlDiagram, String outputDirPath) {
        try {
            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            // Create XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Create root element
            Element root = doc.createElementNS(BOM_NS, "businessObjectModel");
            root.setAttribute("xmlns", BOM_NS);
            root.setAttribute("modelVersion", "1.0");
            doc.appendChild(root);

            // Create businessObjects container
            Element businessObjects = doc.createElement("businessObjects");
            root.appendChild(businessObjects);

            // Generate businessObject elements for each class
            if (pumlDiagram.classes() != null) {
                for (PumlClass pumlClass : pumlDiagram.classes().values()) {
                    Element businessObject = createBusinessObject(doc, pumlClass, pumlDiagram);
                    businessObjects.appendChild(businessObject);
                }
            }

            // Write to file
            writeBonitaBomDocument(doc, outputDir.resolve("bom.xml").toString());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate BOM file", e);
        }
    }

    /**
     * Creates a businessObject element for a PUML class.
     */
    private static Element createBusinessObject(Document doc, PumlClass pumlClass, BusinessData pumlDiagram) {
        Element businessObject = doc.createElement("businessObject");
        businessObject.setAttribute("qualifiedName", "com.company.model." + pumlClass.name());

        // Create fields container
        Element fieldsElement = doc.createElement("fields");
        businessObject.appendChild(fieldsElement);

        // Add fields
        if (pumlClass.fields() != null) {
            for (PumlField field : pumlClass.fields()) {
                Element fieldElement = createField(doc, field, pumlDiagram);
                fieldsElement.appendChild(fieldElement);
            }
        }

        // Add empty constraint/query/index elements
        businessObject.appendChild(doc.createElement("uniqueConstraints"));
        businessObject.appendChild(doc.createElement("queries"));
        businessObject.appendChild(doc.createElement("indexes"));

        return businessObject;
    }

    /**
     * Creates a field or relationField element for a PUML field.
     */
    private static Element createField(Document doc, PumlField pumlField, BusinessData pumlDiagram) {
        String fieldType = pumlField.type();
        
        // Check if this is a reference to another class (use relationField)
        if (isClassReference(fieldType, pumlDiagram)) {
            Element relationField = doc.createElement("relationField");
            relationField.setAttribute("type", "COMPOSITION");
            relationField.setAttribute("reference", "com.company.model." + fieldType);
            relationField.setAttribute("fetchType", "LAZY");
            relationField.setAttribute("name", pumlField.name());
            relationField.setAttribute("nullable", "true");
            relationField.setAttribute("collection", "false");
            return relationField;
        }

        // Regular field
        Element field = doc.createElement("field");
        String bonitaType = mapPumlTypeToBonitaType(fieldType, pumlDiagram);
        field.setAttribute("type", bonitaType);
        field.setAttribute("length", "255");
        field.setAttribute("name", pumlField.name());
        field.setAttribute("nullable", "true");
        field.setAttribute("collection", "false");
        
        return field;
    }

    /**
     * Checks if a type is a reference to another class.
     */
    private static boolean isClassReference(String type, BusinessData pumlDiagram) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        // Check if the type exists as a class in the diagram
        return pumlDiagram.classes() != null && pumlDiagram.classes().containsKey(type);
    }

    /**
     * Maps PUML types to Bonita BOM types.
     */
    private static String mapPumlTypeToBonitaType(String pumlType, BusinessData pumlDiagram) {
        if (pumlType == null || pumlType.isEmpty()) {
            return "STRING"; // Default type
        }

        // Check if it's an enum - use STRING for enums
        if (pumlDiagram.enums() != null && pumlDiagram.enums().containsKey(pumlType)) {
            return "STRING";
        }

        // Map primitive types
        return switch (pumlType.toLowerCase()) {
            case "string" -> "STRING";
            case "boolean", "bool" -> "BOOLEAN";
            case "localdate" -> "LOCALDATE";
            case "localdatetime" -> "LOCALDATETIME";
            case "offsetdatetime" -> "OFFSETDATETIME";
            case "double" -> "DOUBLE";
            case "float" -> "FLOAT";
            case "integer", "int" -> "INTEGER";
            case "long" -> "LONG";
            case "text" -> "TEXT";
            default -> "STRING"; // Default fallback
        };
    }

    /**
     * Writes a Bonita BOM Document to a file.
     */
    private static void writeBonitaBomDocument(Document doc, String outputFilePath) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");

            StringWriter stringWriter = new StringWriter();
            DOMSource source = new DOMSource(doc);
            StreamResult stringResult = new StreamResult(stringWriter);
            transformer.transform(source, stringResult);

            String xmlContent = stringWriter.toString();
            
            // Ensure standalone="yes" in XML declaration
            xmlContent = xmlContent.replaceAll("standalone=\"no\"", "standalone=\"yes\"");
            if (!xmlContent.contains("standalone=")) {
                xmlContent = xmlContent.replaceFirst("\\?>", " standalone=\"yes\"?>");
            }
            
            // Remove extra blank lines
            xmlContent = xmlContent.replaceAll("(\r?\n)\\s*\r?\n", "$1");

            Files.write(Paths.get(outputFilePath), xmlContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write BOM document: " + outputFilePath, e);
        }
    }
}
