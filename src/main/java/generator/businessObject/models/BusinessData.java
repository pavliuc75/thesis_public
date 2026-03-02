package generator.businessObject.models;

import java.util.List;
import java.util.Map;

public record BusinessData(
        Map<String, PumlClass> classes,
        Map<String, PumlEnum> enums,
        List<Composition> compositions
) {}

