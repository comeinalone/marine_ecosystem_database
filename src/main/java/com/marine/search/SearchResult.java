package com.marine.search;

import com.marine.model.*;
import java.util.*;

public record SearchResult(SearchDefinition definition, List<Hit> hits, long total, int offset, int limit) {
    public SearchResult { hits = List.copyOf(hits); }
    public record Hit(SearchDefinition definition, Map<String, Object> values) {
        public Hit { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
        public Object get(String name) { return values.get(name); }
        public Map<String, Object> identity() {
            Map<String, Object> key = new LinkedHashMap<>();
            for (String column : definition.keys()) key.put(column, Objects.requireNonNull(get(column), "Missing key " + column));
            return Collections.unmodifiableMap(key);
        }
        public EntityRow entityRow() {
            if (definition.entity() == null) throw new IllegalStateException("Audit history is read-only");
            identity(); return new EntityRow(definition.entity(), values);
        }
        public SampleKey sampleKey() {
            if (definition.entity() != EntityType.SAMPLE) throw new IllegalStateException("Not a DNA sample");
            return new SampleKey(((Number) get("speciesID")).intValue(), ((Number) get("sampleID")).intValue());
        }
        public String label() {
            String name = List.of("full_name", "researcher_name", "species_name", "common_name", "vessel_name", "location_name")
                    .stream().map(this::get).filter(Objects::nonNull).map(Object::toString).findFirst().orElse("");
            String detail = List.of("researcher_specialization", "email_address", "scientific_name", "ocean_region")
                    .stream().map(this::get).filter(Objects::nonNull).map(Object::toString).reduce((a, b) -> a + " / " + b).orElse("");
            return identity() + " — " + name + (detail.isBlank() ? "" : " / " + detail);
        }
    }
}
