package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class ArchimateHelper {
    private static final ResourceLoader resourceLoader = new org.springframework.core.io.DefaultResourceLoader();

    private static List<Role> parseRolesWithActors(Document doc) {
        doc.getDocumentElement().normalize();

        Map<String, Actor> actorsById = new HashMap<>();
        Map<String, Role> rolesById = new HashMap<>();

        // 1) Collect all BusinessActor and BusinessRole elements
        NodeList elementNodes = doc.getElementsByTagName("element");
        for (int i = 0; i < elementNodes.getLength(); i++) {
            Node node = elementNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element element = (Element) node;
            String type = element.getAttribute("xsi:type");     // e.g. "archimate:BusinessActor"
            String id = element.getAttribute("id");
            String name = element.getAttribute("name");

            if ("archimate:BusinessActor".equals(type)) {
                actorsById.put(id, new Actor(id, name));
            } else if ("archimate:BusinessRole".equals(type)) {
                rolesById.put(id, new Role(id, name));
            }
        }

        // 2) Process AssignmentRelationship elements and wire actors to roles
        for (int i = 0; i < elementNodes.getLength(); i++) {
            Node node = elementNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element element = (Element) node;
            String type = element.getAttribute("xsi:type");
            if (!"archimate:AssignmentRelationship".equals(type)) {
                continue;
            }

            String sourceId = element.getAttribute("source");
            String targetId = element.getAttribute("target");

            Actor sourceActor = actorsById.get(sourceId);
            Role targetRole = rolesById.get(targetId);
            if (sourceActor != null && targetRole != null) {
                targetRole.addActor(sourceActor);
            }
        }

        return new ArrayList<>(rolesById.values());
    }

    public static List<Role> loadFromResources(String resourcePath) throws Exception {
        try (InputStream in = ArchimateHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);

            return parseRolesWithActors(doc);
        }
    }

    public static void main(String[] args) throws Exception {
        ArchimateHelper parser = new ArchimateHelper();

        List<Role> roles = parser.loadFromResources("organization/organization.archimate");

        for (Role role : roles) {
            System.out.println(role.getName() + " (" + role.getId() + ")");
            for (Actor a : role.getActors()) {
                System.out.println("  - " + a.getName() + " (" + a.getId() + ")");
            }
        }
    }

    public static void validate(String classpathResource) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathResource);

        if (!resource.exists()) {
            throw new IllegalArgumentException("Resource not found: " + classpathResource);
        }

        try (InputStream in = resource.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // basic security hardening (important even for "just XML")
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            factory.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML in resource: " + classpathResource, e);
        }
    }
}
