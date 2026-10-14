package com.marine.search;

import com.marine.model.*;
import java.util.*;
import java.util.stream.Collectors;

/** Closed registry for all eight editable entities and the read-only ninth table. */
public final class SearchDefinition {
    private final EntityType entity;
    private final String title, from;
    private final List<SearchField> fields;
    private final List<String> keys;
    private SearchDefinition(EntityType entity, String title, String from, List<SearchField> fields, List<String> keys) {
        this.entity = entity; this.title = title; this.from = from;
        this.fields = List.copyOf(fields); this.keys = List.copyOf(keys);
    }
    public EntityType entity() { return entity; }
    public String title() { return title; }
    public String from() { return from; }
    public List<SearchField> fields() { return fields; }
    public List<String> keys() { return keys; }
    public SearchField field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown search field: " + name));
    }
    public String select() {
        return fields.stream().map(f -> f.expression() + " AS `" + f.name() + "`").collect(Collectors.joining(", "));
    }
    public static String fullName(String alias) {
        return "TRIM(REGEXP_REPLACE(CONCAT_WS(' ', " + alias + ".first_name, " + alias
                + ".middle_name, " + alias + ".last_name), '[[:space:]]+', ' '))";
    }
    private static SearchField name(String column, String label, String expression, String ref, String key) {
        return new SearchField(column, label, expression, SearchField.Kind.TEXT, List.of(),
                ref != null && ref.equals("RESEARCHER"), ref, key);
    }
    public static SearchDefinition entity(EntityType type) {
        List<SearchField> fields = new ArrayList<>();
        for (Field f : type.fields()) {
            SearchField.Kind kind = switch (f.kind()) {
                case INTEGER, REFERENCE -> SearchField.Kind.INTEGER;
                case DATE -> SearchField.Kind.DATE;
                case CHOICE -> SearchField.Kind.CHOICE;
                default -> SearchField.Kind.TEXT;
            };
            fields.add(new SearchField(f.name(), f.label(), "e.`" + f.name() + "`", kind, f.options(), false, null, null));
        }
        String from = "`" + type.table() + "` e";
        if (type == EntityType.RESEARCHER) {
            fields.add(1, name("full_name", "Full name", fullName("e"), "RESEARCHER", "researcherID"));
        }
        if (Set.of(EntityType.CONTACT, EntityType.BIOLOGIST, EntityType.GENETICIST, EntityType.SAMPLE).contains(type)) {
            from += " LEFT JOIN Researcher r ON r.researcherID = e.researcherID";
            fields.add(type == EntityType.SAMPLE ? fields.size() : 1,
                    name("researcher_name", "Researcher Name", fullName("r"), "RESEARCHER", "researcherID"));
            fields.add(name("researcher_specialization", "Specialization", "r.researcher_specialization", null, null));
            fields.add(name("email_address", "Researcher email", "r.email_address", null, null));
        }
        if (type == EntityType.SAMPLE) {
            from += " LEFT JOIN Fish_Species f ON f.speciesID = e.speciesID"
                    + " LEFT JOIN Research_Vehicle v ON v.vesselID = e.vesselID"
                    + " LEFT JOIN Ocean_Sampling_Region l ON l.locationID = e.locationID";
            fields.add(name("species_name", "Species name", "f.common_name", "SPECIES", "speciesID"));
            fields.add(name("scientific_name", "Scientific name", "f.scientific_name", null, null));
            fields.add(name("vessel_name", "Vessel name", "v.vessel_name", "VESSEL", "vesselID"));
            fields.add(name("location_name", "Region name", "l.location_name", "LOCATION", "locationID"));
        }
        // Names on the parent tables also offer SQL-backed suggestions.
        String display = type.displayColumn();
        if (Set.of(EntityType.SPECIES, EntityType.VESSEL, EntityType.LOCATION).contains(type)) {
            for (int i = 0; i < fields.size(); i++) if (fields.get(i).name().equals(display)) {
                SearchField f = fields.get(i);
                fields.set(i, name(f.name(), f.label(), f.expression(), type.name(), type.keys().get(0).name()));
            }
        }
        return new SearchDefinition(type, type.title(), from, fields, type.keys().stream().map(Field::name).toList());
    }
    public static SearchDefinition audit() {
        List<SearchField> fields = new ArrayList<>();
        fields.add(new SearchField("auditID", "Audit ID", "e.auditID", SearchField.Kind.INTEGER, List.of(), false, null, null));
        fields.add(new SearchField("speciesID", "Species ID", "e.speciesID", SearchField.Kind.INTEGER, List.of(), false, null, null));
        fields.add(name("species_name", "Species name", "f.common_name", "SPECIES", "speciesID"));
        fields.add(name("scientific_name", "Scientific name", "f.scientific_name", null, null));
        List<String> statuses = EntityType.SPECIES.fields().get(3).options();
        for (String column : List.of("old_status", "new_status"))
            fields.add(new SearchField(column, column.equals("old_status") ? "Old status" : "New status",
                    "e." + column, SearchField.Kind.CHOICE, statuses, false, null, null));
        fields.add(new SearchField("changed_at", "Change date", "e.changed_at", SearchField.Kind.TIMESTAMP, List.of(), false, null, null));
        return new SearchDefinition(null, "Conservation Audit", "Species_Status_Audit e LEFT JOIN Fish_Species f ON f.speciesID = e.speciesID",
                fields, List.of("auditID"));
    }
}
