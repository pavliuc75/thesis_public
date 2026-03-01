package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

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
import java.util.*;

public class BonitaOrganizationGenerator {

    private static final String ORG_NS = "http://documentation.bonitasoft.com/organization-xml-schema/1.1";
    private static final String DEFAULT_PASSWORD = "bpm";
    private static final String DEFAULT_GROUP = "Default";

    public static void generateOrganizationFile(ArchimateData archimateData, String outputDirPath) {
        try {
            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(outputDirPath);
            Files.createDirectories(outputDir);

            // Create XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Create root element in the organization namespace
            Element root = doc.createElementNS(ORG_NS, "Organization");
            // Set namespace declaration - the transformer will handle the prefix
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:organization", ORG_NS);
            doc.appendChild(root);

            // Create customUserInfoDefinitions (empty)
            Element customUserInfoDefinitions = doc.createElementNS(ORG_NS, "customUserInfoDefinitions");
            root.appendChild(customUserInfoDefinitions);

            // Collect all unique actors and their roles
            Map<String, Set<String>> actorToRoles = new LinkedHashMap<>(); // Preserve insertion order
            List<Role> roles = archimateData.getArchimateRoles();
            
            if (roles == null || roles.isEmpty()) {
                throw new IllegalStateException("No roles found in archimateData. Cannot generate organization file.");
            }

            // Build map of actors to their roles
            for (Role role : roles) {
                String roleName = role.getName();
                for (Actor actor : role.getActors()) {
                    String actorName = actor.getName();
                    actorToRoles.computeIfAbsent(actorName, k -> new LinkedHashSet<>()).add(roleName);
                }
            }

            // Create users element
            Element usersElement = doc.createElementNS(ORG_NS, "users");
            root.appendChild(usersElement);

            // Generate users (sorted by name for consistency)
            List<String> sortedActorNames = new ArrayList<>(actorToRoles.keySet());
            Collections.sort(sortedActorNames);
            
            for (String actorName : sortedActorNames) {
                Element userElement = doc.createElementNS(ORG_NS, "user");
                userElement.setAttribute("userName", actorName);
                usersElement.appendChild(userElement);

                Element personalData = doc.createElementNS(ORG_NS, "personalData");
                userElement.appendChild(personalData);

                Element professionalData = doc.createElementNS(ORG_NS, "professionalData");
                userElement.appendChild(professionalData);

                Element password = doc.createElementNS(ORG_NS, "password");
                password.setAttribute("encrypted", "false");
                password.setTextContent(DEFAULT_PASSWORD);
                userElement.appendChild(password);

                Element customUserInfoValues = doc.createElementNS(ORG_NS, "customUserInfoValues");
                userElement.appendChild(customUserInfoValues);
            }

            // Create roles element
            Element rolesElement = doc.createElementNS(ORG_NS, "roles");
            root.appendChild(rolesElement);

            // Generate roles (sorted by name for consistency)
            List<String> sortedRoleNames = new ArrayList<>();
            for (Role role : roles) {
                sortedRoleNames.add(role.getName());
            }
            Collections.sort(sortedRoleNames);

            for (String roleName : sortedRoleNames) {
                Element roleElement = doc.createElementNS(ORG_NS, "role");
                roleElement.setAttribute("name", roleName);
                rolesElement.appendChild(roleElement);

                Element displayName = doc.createElementNS(ORG_NS, "displayName");
                displayName.setTextContent(roleName);
                roleElement.appendChild(displayName);
            }

            // Create groups element
            Element groupsElement = doc.createElementNS(ORG_NS, "groups");
            root.appendChild(groupsElement);

            // Generate Default group
            Element groupElement = doc.createElementNS(ORG_NS, "group");
            groupElement.setAttribute("name", DEFAULT_GROUP);
            groupsElement.appendChild(groupElement);

            Element groupDisplayName = doc.createElementNS(ORG_NS, "displayName");
            groupDisplayName.setTextContent(DEFAULT_GROUP);
            groupElement.appendChild(groupDisplayName);

            // Create memberships element
            Element membershipsElement = doc.createElementNS(ORG_NS, "memberships");
            root.appendChild(membershipsElement);

            // Generate memberships for each actor-role pair
            for (String actorName : sortedActorNames) {
                Set<String> actorRoles = actorToRoles.get(actorName);
                for (String roleName : actorRoles) {
                    Element membershipElement = doc.createElementNS(ORG_NS, "membership");
                    membershipsElement.appendChild(membershipElement);

                    Element userName = doc.createElementNS(ORG_NS, "userName");
                    userName.setTextContent(actorName);
                    membershipElement.appendChild(userName);

                    Element roleNameElement = doc.createElementNS(ORG_NS, "roleName");
                    roleNameElement.setTextContent(roleName);
                    membershipElement.appendChild(roleNameElement);

                    Element groupName = doc.createElementNS(ORG_NS, "groupName");
                    groupName.setTextContent(DEFAULT_GROUP);
                    membershipElement.appendChild(groupName);
                }
            }

            // Write XML to file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "ASCII");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            // Transform to StringWriter first to process the output
            StringWriter stringWriter = new StringWriter();
            DOMSource source = new DOMSource(doc);
            StreamResult stringResult = new StreamResult(stringWriter);
            transformer.transform(source, stringResult);

            // Post-process the XML to fix the root element namespace
            String xmlContent = stringWriter.toString();
            // Replace the root element to use organization: prefix and remove default namespace
            // Use regex to handle different attribute orders and whitespace
            xmlContent = xmlContent.replaceFirst(
                "<Organization\\s+xmlns:organization=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\"\\s+xmlns=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\"\\s*>",
                "<organization:Organization xmlns:organization=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\">"
            );
            // Also handle case where xmlns might come first
            xmlContent = xmlContent.replaceFirst(
                "<Organization\\s+xmlns=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\"\\s+xmlns:organization=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\"\\s*>",
                "<organization:Organization xmlns:organization=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\">"
            );
            // Handle case with just default namespace (shouldn't happen but be safe)
            xmlContent = xmlContent.replaceFirst(
                "<Organization\\s+xmlns=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\"\\s*>",
                "<organization:Organization xmlns:organization=\"http://documentation.bonitasoft.com/organization-xml-schema/1.1\">"
            );
            // Fix the closing tag as well
            xmlContent = xmlContent.replace("</Organization>", "</organization:Organization>");
            // Remove standalone="no" from XML declaration
            xmlContent = xmlContent.replaceFirst(
                "<\\?xml version=\"1.0\" encoding=\"ASCII\" standalone=\"no\"\\?>",
                "<?xml version=\"1.0\" encoding=\"ASCII\"?>"
            );

            Path outputPath = outputDir.resolve("Organization.xml");
            Files.write(outputPath, xmlContent.getBytes(StandardCharsets.US_ASCII));

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Bonita organization file: " + e.getMessage(), e);
        }
    }
}
