package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObject.models;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class SimplePlantUmlParser {

    private static final Pattern CLASS_START = Pattern.compile("^class\\s+(\\w+)\\s*\\{\\s*$");
    private static final Pattern ENUM_START  = Pattern.compile("^enum\\s+(\\w+)\\s*\\{\\s*$");
    private static final Pattern BLOCK_END   = Pattern.compile("^}\\s*$");

    // A "1" *-- "many" B
    // A *-- "many" B
    // A "many" *-- B
    // A *-- B
    private static final Pattern COMPOSITION_WITH_LABELS = Pattern.compile(
            "^(\\w+)\\s*(?:\"(1|many)\"\\s*)?\\*--\\s*(?:\"(1|many)\"\\s*)?(\\w+)\\s*$"
    );
    // field line inside class: name: Type   OR name
    // examples:
    //   requestId: int
    //   id
    private static final Pattern FIELD = Pattern.compile("^([a-zA-Z_]\\w*)(?:\\s*:\\s*([a-zA-Z_]\\w*))?\\s*$");

    public PumlDiagram parse(InputStream in) throws IOException {
        Map<String, List<PumlField>> classFields = new LinkedHashMap<>();
        Map<String, List<String>> enumValues = new LinkedHashMap<>();
        List<Composition> compositions = new ArrayList<>();

        String currentClass = null;
        String currentEnum  = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                String line = stripComment(raw).trim();
                if (line.isEmpty()) continue;

                // wrappers
                if (line.equalsIgnoreCase("@startuml") || line.equalsIgnoreCase("@enduml")) continue;

                // inside a class block
                if (currentClass != null) {
                    if (BLOCK_END.matcher(line).matches()) {
                        currentClass = null;
                        continue;
                    }
                    Matcher fm = FIELD.matcher(line);
                    if (!fm.matches()) {
                        throw new IllegalArgumentException("Unsupported class field line: " + line);
                    }
                    String fieldName = fm.group(1);
                    String fieldType = fm.group(2); // may be null
                    classFields.get(currentClass).add(new PumlField(fieldName, fieldType));
                    continue;
                }

                // inside an enum block
                if (currentEnum != null) {
                    if (BLOCK_END.matcher(line).matches()) {
                        currentEnum = null;
                        continue;
                    }
                    // enum value can be "Submitted" etc. (first token)
                    String v = firstToken(line);
                    if (v != null && !v.isBlank()) enumValues.get(currentEnum).add(v);
                    continue;
                }

                // start class
                Matcher cs = CLASS_START.matcher(line);
                if (cs.matches()) {
                    currentClass = cs.group(1);
                    classFields.putIfAbsent(currentClass, new ArrayList<>());
                    continue;
                }

                // start enum
                Matcher es = ENUM_START.matcher(line);
                if (es.matches()) {
                    currentEnum = es.group(1);
                    enumValues.putIfAbsent(currentEnum, new ArrayList<>());
                    continue;
                }

                Matcher comp = COMPOSITION_WITH_LABELS.matcher(line);
                if (comp.matches()) {
                    String whole = comp.group(1);
                    String wholeLabel = comp.group(2); // may be null
                    String partLabel  = comp.group(3); // may be null
                    String part  = comp.group(4);

                    compositions.add(new Composition(
                            whole,
                            part,
                            wholeLabel != null ? wholeLabel : "1",
                            partLabel  != null ? partLabel  : "1"
                    ));
                    continue;
                }

                // anything else is not allowed in your subset
                throw new IllegalArgumentException("Unsupported PlantUML line: " + line);
            }
        }

        if (currentClass != null) throw new IllegalArgumentException("Unclosed class block: " + currentClass);
        if (currentEnum != null)  throw new IllegalArgumentException("Unclosed enum block: " + currentEnum);

        // Build final records
        Map<String, PumlClass> classes = new LinkedHashMap<>();
        for (var e : classFields.entrySet()) {
            classes.put(e.getKey(), new PumlClass(e.getKey(), List.copyOf(e.getValue())));
        }

        Map<String, PumlEnum> enums = new LinkedHashMap<>();
        for (var e : enumValues.entrySet()) {
            enums.put(e.getKey(), new PumlEnum(e.getKey(), List.copyOf(e.getValue())));
        }

        return new PumlDiagram(classes, enums, List.copyOf(compositions));
    }

    private static String stripComment(String line) {
        // PlantUML comment in your file: lines starting with '
        int idx = line.indexOf("'");
        if (idx >= 0) return line.substring(0, idx);
        return line;
    }

    private static String firstToken(String line) {
        String s = line.trim();
        if (s.isEmpty()) return null;
        // tolerate separators
        s = s.replace(",", "").replace(";", "").trim();
        String[] parts = s.split("\\s+");
        return parts.length == 0 ? null : parts[0];
    }
}
