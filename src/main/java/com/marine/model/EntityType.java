package com.marine.model;

import java.util.List;
import static com.marine.model.Field.*;

/** Whitelist of editable tables and their fields, matching 01_create_tables.sql. */
public enum EntityType {
    SPECIES("Fish_Species", "Fish Species", "common_name", List.of(
            id("speciesID", "Species ID"), text("common_name", "Common name", 50, true),
            text("scientific_name", "Scientific name", 100, true),
            choice("conservation_status", "Conservation status", 30,
                    "Not Evaluated", "Data Deficient", "Least Concern", "Near Threatened",
                    "Vulnerable", "Endangered", "Critically Endangered", "Extinct in the Wild", "Extinct"))),
    RESEARCHER("Researcher", "Researchers", "first_name", List.of(
            id("researcherID", "Researcher ID"), text("first_name", "First name", 50, true),
            text("middle_name", "Middle name", 50, false), text("last_name", "Last name", 50, true),
            text("researcher_specialization", "Specialization", 100, true),
            text("email_address", "Email", 100, true), date("date_of_birth", "Date of birth (YYYY-MM-DD)"))),
    CONTACT("Researcher_Contact", "Contacts", "phone_number", List.of(
            ref("researcherID", "Researcher", true, "RESEARCHER"),
            keyText("contact_type", "Contact type", 20, List.of("Mobile", "Home", "Work", "Other")),
            keyText("phone_number", "Phone number", 15, List.of()))),
    BIOLOGIST("Marine_Biologist", "Marine Biologists", "researcherID", List.of(
            ref("researcherID", "Researcher", true, "RESEARCHER"),
            number("field_experience", "Experience (years)", 0),
            choice("diving_certified", "Diving certified", 10, "Yes", "No"))),
    GENETICIST("Geneticist", "Geneticists", "researcherID", List.of(
            ref("researcherID", "Researcher", true, "RESEARCHER"),
            text("laboratory_name", "Laboratory", 100, false), text("research_focus", "Research focus", 100, false))),
    VESSEL("Research_Vehicle", "Research Vessels", "vessel_name", List.of(
            id("vesselID", "Vessel ID"), text("vessel_name", "Vessel name", 100, true),
            number("vessel_capacity", "Capacity", 1), text("home_port", "Home port", 100, true))),
    LOCATION("Ocean_Sampling_Region", "Sampling Locations", "location_name", List.of(
            id("locationID", "Location ID"), text("location_name", "Location name", 100, true),
            text("ocean_region", "Ocean region", 100, true))),
    SAMPLE("DNA_Sample", "DNA Samples", "gene_marker", List.of(
            ref("speciesID", "Species", true, "SPECIES"), id("sampleID", "Sample ID (within species)"),
            date("collection_date", "Collection date (YYYY-MM-DD)"), text("gene_marker", "Gene marker", 50, true),
            choice("DNA_quality", "DNA quality", 20, "High", "Medium", "Low"),
            ref("researcherID", "Researcher", false, "RESEARCHER"), ref("vesselID", "Vessel", false, "VESSEL"),
            ref("locationID", "Sampling location", false, "LOCATION")));

    private final String table;
    private final String title;
    private final String displayColumn;
    private final List<Field> fields;

    EntityType(String table, String title, String displayColumn, List<Field> fields) {
        this.table = table;
        this.title = title;
        this.displayColumn = displayColumn;
        this.fields = List.copyOf(fields);
    }
    public String table() { return table; }
    public String title() { return title; }
    public String displayColumn() { return displayColumn; }
    public List<Field> fields() { return fields; }
    public List<Field> keys() { return fields.stream().filter(Field::key).toList(); }
    public List<Field> editableFields() {
        // Every contact column is part of its key; editing changes that tuple.
        return this == CONTACT ? fields : fields.stream().filter(f -> !f.key()).toList();
    }
}
