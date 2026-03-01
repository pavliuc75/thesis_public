package org.camunda.bpm.getstarted.loanapproval.delegates.validation.processConfig;

import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.BpmnData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.Lane;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.bpmn.models.ProcessDef;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.Actor;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.OrganizationData;
import org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization.Role;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
import java.util.List;

public class BonitaProcessConfigGenerator {

    private static final String CONFIG_NS = "http://www.bonitasoft.org/ns/bpm/configuration";

    /**
     * Generates a Bonita process configuration file (.conf) based on the proc document and BPMN definitions.
     * The filename is based on the pool ID from the proc document.
     *
     * @param procDocument    the proc Document containing the pool
     * @param bpmnDefinitions the BPMN definitions containing lane information
     * @param outputDirPath   the output directory path
     * @param archimateData   the Archimate data containing actors/users
     */
    public static void generateBonitaProcessConfigFile(
            Document procDocument,
            BpmnData bpmnDefinitions,
            String outputDirPath,
            OrganizationData archimateData) {
        try {
            // Find the pool element and get its ID
            String poolId = extractPoolId(procDocument);
            if (poolId == null || poolId.isEmpty()) {
                throw new RuntimeException("No pool ID found in proc document");
            }

            // Extract the first user from archimateData
            String firstUserName = extractFirstUser(archimateData);

            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            // Create XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Create root element
            Element root = doc.createElementNS(CONFIG_NS, "configuration:Configuration");
            root.setAttribute("xmlns:configuration", CONFIG_NS);
            root.setAttribute("name", "Local");
            root.setAttribute("version", "9");
            root.setAttribute("username", firstUserName);
            doc.appendChild(root);

            // Create actorMappings container
            Element actorMappings = doc.createElement("actorMappings");
            root.appendChild(actorMappings);

            // Add actorMapping for each lane
            if (bpmnDefinitions.processes() != null) {
                for (ProcessDef process : bpmnDefinitions.processes()) {
                    if (process.laneSet() != null && process.laneSet().lanes() != null) {
                        for (Lane lane : process.laneSet().lanes()) {
                            Element actorMapping = createActorMapping(doc, lane);
                            actorMappings.appendChild(actorMapping);
                        }
                    }
                }
            }

            // Add connector definition mappings
            addConnectorDefinitionMappings(doc, root);

            // Add connector process dependencies
            addEmailConnectorDependency(doc, root);
            addRestConnectorDependency(doc, root);

            // Add empty processDependencies elements
            addProcessDependency(doc, root, "ACTOR_FILTER");
            addProcessDependency(doc, root, "OTHER");

            // Write to file with pool ID as filename
            String outputFilePath = outputDir.resolve(poolId + ".conf").toString();
            writeBonitaConfigDocument(doc, outputFilePath);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Bonita process config file", e);
        }
    }

    /**
     * Extracts the pool ID from the proc document.
     */
    private static String extractPoolId(Document procDocument) {
        NodeList allElements = procDocument.getElementsByTagName("elements");

        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String xmiType = element.getAttribute("xmi:type");

            if ("process:Pool".equals(xmiType)) {
                return element.getAttribute("xmi:id");
            }
        }

        return null;
    }

    /**
     * Extracts the first user (actor) from the archimate data.
     * Returns the name of the first actor found in the first role.
     */
    private static String extractFirstUser(OrganizationData archimateData) {
        if (archimateData == null || archimateData.getArchimateRoles() == null) {
            return "walter.bates"; // fallback default
        }

        List<Role> roles = archimateData.getArchimateRoles();
        for (Role role : roles) {
            if (role.getActors() != null && !role.getActors().isEmpty()) {
                Actor firstActor = role.getActors().get(0);
                return firstActor.getName();
            }
        }

        return "walter.bates"; // fallback default if no actors found
    }

    /**
     * Creates an actorMapping element for a lane.
     */
    private static Element createActorMapping(Document doc, Lane lane) {
        Element actorMapping = doc.createElement("actorMapping");
        actorMapping.setAttribute("name", lane.name());

        // Add empty groups, memberships, roles
        actorMapping.appendChild(doc.createElement("groups"));
        actorMapping.appendChild(doc.createElement("memberships"));
        actorMapping.appendChild(doc.createElement("roles"));

        // Add users element with the resolved actor
        Element users = doc.createElement("users");
        actorMapping.appendChild(users);

        if (lane.resolvedActor() != null && !lane.resolvedActor().isEmpty()) {
            Element user = doc.createElement("user");
            user.setTextContent(lane.resolvedActor());
            users.appendChild(user);
        }

        return actorMapping;
    }

    /**
     * Adds a processDependencies element with the specified id.
     */
    private static void addProcessDependency(Document doc, Element root, String id) {
        Element processDependency = doc.createElement("processDependencies");
        processDependency.setAttribute("id", id);
        root.appendChild(processDependency);
    }

    private static void addConnectorDefinitionMappings(Document doc, Element root) {
        Element emailMapping = doc.createElement("definitionMappings");
        emailMapping.setAttribute("type", "CONNECTOR");
        emailMapping.setAttribute("definitionId", "email");
        emailMapping.setAttribute("definitionVersion", "1.2.0");
        emailMapping.setAttribute("implementationId", "email-impl");
        emailMapping.setAttribute("implementationVersion", "1.3.0");
        root.appendChild(emailMapping);

        Element restPostMapping = doc.createElement("definitionMappings");
        restPostMapping.setAttribute("type", "CONNECTOR");
        restPostMapping.setAttribute("definitionId", "rest-post");
        restPostMapping.setAttribute("definitionVersion", "1.5.0");
        restPostMapping.setAttribute("implementationId", "rest-post-impl");
        restPostMapping.setAttribute("implementationVersion", "1.5.0");
        root.appendChild(restPostMapping);
    }

    private static void addEmailConnectorDependency(Document doc, Element root) {
        Element processDependency = doc.createElement("processDependencies");
        processDependency.setAttribute("id", "CONNECTOR");

        Element children = doc.createElement("children");
        children.setAttribute("id", "email-impl-1.3.0");

        children.appendChild(createConnectorFragment(doc,
                "email-impl -- 1.3.0", "bonita-connector-email-1.3.0.jar"));
        children.appendChild(createConnectorFragment(doc,
                "email-impl -- 1.3.0", "javax.mail-1.6.2.jar"));
        children.appendChild(createConnectorFragment(doc,
                "email-impl -- 1.3.0", "javax.mail-api-1.6.2.jar"));

        processDependency.appendChild(children);
        root.appendChild(processDependency);
    }

    private static void addRestConnectorDependency(Document doc, Element root) {
        Element processDependency = doc.createElement("processDependencies");
        processDependency.setAttribute("id", "CONNECTOR");

        Element children = doc.createElement("children");
        children.setAttribute("id", "rest-post-impl-1.5.0");
        children.appendChild(createConnectorFragment(doc,
                "rest-post-impl -- 1.5.0", "bonita-connector-rest-1.5.0.jar"));

        processDependency.appendChild(children);
        root.appendChild(processDependency);
    }

    private static Element createConnectorFragment(Document doc, String key, String value) {
        Element fragment = doc.createElement("fragments");
        fragment.setAttribute("key", key);
        fragment.setAttribute("value", value);
        fragment.setAttribute("type", "CONNECTOR");
        return fragment;
    }

    /**
     * Writes a Bonita config Document to a file.
     */
    private static void writeBonitaConfigDocument(Document doc, String outputFilePath) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter stringWriter = new StringWriter();
            DOMSource source = new DOMSource(doc);
            StreamResult stringResult = new StreamResult(stringWriter);
            transformer.transform(source, stringResult);

            String xmlContent = stringWriter.toString();
            
            // Remove standalone attribute if present
            xmlContent = xmlContent.replaceAll(" standalone=\"yes\"", "");
            xmlContent = xmlContent.replaceAll(" standalone=\"no\"", "");
            
            // Remove extra blank lines
            xmlContent = xmlContent.replaceAll("(\r?\n)\\s*\r?\n", "$1");

            Files.write(Paths.get(outputFilePath), xmlContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write Bonita config document: " + outputFilePath, e);
        }
    }
}
