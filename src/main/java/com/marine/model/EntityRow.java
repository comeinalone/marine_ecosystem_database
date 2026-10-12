package com.marine.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A typed table identity plus column values; SQL NULL is preserved. */
public record EntityRow(EntityType type, Map<String, Object> values) {
    public EntityRow {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    public Object get(String column) { return values.get(column); }
    public String identity() {
        return String.join(", ", type.keys().stream().map(f -> f.label() + "=" + get(f.name())).toList());
    }
}
