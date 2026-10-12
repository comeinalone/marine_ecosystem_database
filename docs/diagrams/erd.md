# Entity relationships

The diagram reflects the existing schema. DNA sample IDs are species-local; researchers may have either, both or neither subtype. Contacts have a three-column key. Audit entries restrict deletion of their species.

```mermaid
erDiagram
    Researcher ||--o{ Researcher_Contact : has
    Researcher ||--o| Marine_Biologist : specializes
    Researcher ||--o| Geneticist : specializes
    Researcher ||--o{ DNA_Sample : collects
    Research_Vehicle ||--o{ DNA_Sample : supports
    Ocean_Sampling_Region ||--o{ DNA_Sample : hosts
    Fish_Species ||--o{ DNA_Sample : sampled
    Fish_Species ||--o{ Species_Status_Audit : has_history
    Researcher {
        int researcherID PK
        varchar email_address UK
        varchar researcher_specialization
    }
    Researcher_Contact {
        int researcherID PK,FK
        varchar contact_type PK
        varchar phone_number PK
    }
    Marine_Biologist {
        int researcherID PK,FK
        int field_experience
        varchar diving_certified
    }
    Geneticist {
        int researcherID PK,FK
        varchar laboratory_name
        varchar research_focus
    }
    Research_Vehicle {
        int vesselID PK
        varchar vessel_name
        int vessel_capacity
    }
    Ocean_Sampling_Region {
        int locationID PK
        varchar location_name
        varchar ocean_region
    }
    Fish_Species {
        int speciesID PK
        varchar scientific_name UK
        varchar conservation_status
    }
    DNA_Sample {
        int speciesID PK,FK
        int sampleID PK
        date collection_date
        varchar gene_marker
        varchar DNA_quality
        int researcherID FK
        int vesselID FK
        int locationID FK
    }
    Species_Status_Audit {
        int auditID PK
        int speciesID FK
        varchar old_status
        varchar new_status
        timestamp changed_at
    }
```

For full columns, types, nullability, CHECKs and indexes, use `database/schema/01_create_tables.sql`; this compact diagram intentionally shows the key relationships and selected descriptive attributes.
