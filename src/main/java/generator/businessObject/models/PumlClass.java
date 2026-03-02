package generator.businessObject.models;

import java.util.List;

public record PumlClass(String name, List<PumlField> fields) {}
