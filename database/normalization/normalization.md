# Marine Ecosystem Management System — Normalization Analysis

## 1. Objective
Verify that the database schema satisfies First Normal Form (1NF), Second Normal Form (2NF), and Third Normal Form (3NF), using candidate keys and intended functional dependencies (FDs).

> **Scope:** This analysis is based on the project's nine-table schema and the stated assumptions. SQL `PRIMARY KEY` and `UNIQUE` constraints enforce some keys, but do not establish every business-rule FD. Verify the assumptions against real requirements before calling 3NF conclusively proven.

## 2. Normal Forms

- **1NF:** Each column contains one value from its domain per row; no repeating groups.
- **2NF:** In 1NF, with no non-prime attribute partially dependent on any candidate key.
- **3NF:** In 2NF, and for every nontrivial FD `X → A`, `X` is a superkey or `A` is prime (part of a candidate key).

## 3. Table-by-Table Analysis

| Relation | Candidate key(s) based on declared constraints | Important intended FD(s) | 3NF verdict |
|---|---|---|---|
| `Researcher` | `researcherID`, `email_address` | `researcherID → first_name, middle_name, last_name, researcher_specialization, email_address, date_of_birth`; `email_address →` remaining researcher attributes | 3NF under stated FDs |
| `Researcher_Contact` | `(researcherID, contact_type, phone_number)` | No nontrivial FDs with non-key RHS assumed beyond the key | 3NF under stated FDs |
| `Marine_Biologist` | `researcherID` | `researcherID → field_experience, diving_certified` | 3NF under stated FDs |
| `Geneticist` | `researcherID` | `researcherID → laboratory_name, research_focus` | 3NF under stated FDs |
| `Research_Vehicle` | `vesselID` | `vesselID → vessel_name, vessel_capacity, home_port` | 3NF under stated FDs |
| `Ocean_Sampling_Region` | `locationID` | `locationID → location_name, ocean_region` | 3NF under stated FDs |
| `Fish_Species` | `speciesID`, `scientific_name` | `speciesID → common_name, scientific_name, conservation_status`; `scientific_name →` remaining species attributes | 3NF under stated FDs |
| `DNA_Sample` | `(speciesID, sampleID)` | `(speciesID, sampleID) → collection_date, gene_marker, DNA_quality, researcherID, vesselID, locationID` | 3NF **if** sample ID is species-local and no other relevant FDs apply |
| `Species_Status_Audit` | `auditID` | `auditID → speciesID, old_status, new_status, changed_at` | 3NF under stated FDs |

### 3.1 1NF verification

All attributes are designed to hold single scalar values. Multiple researcher contact numbers are represented by **multiple rows** in `Researcher_Contact`, not repeating columns in `Researcher`. Therefore the nine relations are designed in 1NF.

### 3.2 2NF verification

Relations with a single-attribute candidate key have no possibility of a partial dependency on that key. `Researcher` and `Fish_Species` have additional single-column candidate keys (`email_address` and `scientific_name` respectively), also not susceptible to partial-key dependency.

`Researcher_Contact` has a three-attribute composite candidate key, but **no non-prime attributes**, so it satisfies 2NF regardless of additional FDs between prime attributes.

`DNA_Sample` uses composite `(speciesID, sampleID)` as its declared primary key. For 2NF, verify that neither `speciesID` alone nor `sampleID` alone determines any non-prime sample attribute under the real business rules. The examples of sample ID 1 for more than one species are consistent with species-local numbering, but **example data cannot prove functional dependencies**.

### 3.3 3NF verification

Under the FDs listed in the table, each determinant is a declared candidate key; therefore the relations satisfy 3NF. The design avoids storing dependent entity descriptions in `DNA_Sample`: for example, researcher name belongs to `Researcher`, and vessel capacity belongs to `Research_Vehicle`, linked by foreign keys instead.

`Researcher_Contact` also satisfies 3NF under the stated constraints and FDs because all its attributes are prime (part of its candidate key). Other meaningful FDs should still be checked with domain experts.

## 4. Important business-rule questions

1. **Is `sampleID` unique only within its fish species?** Confirmed project requirement: yes. Keep `(speciesID, sampleID)`; sample IDs are not globally unique. The remaining FD assumptions still require the stated domain interpretation.
2. **Can a DNA sample represent more than one species?** Current schema says no; if yes, a separate sample-to-species relationship may be needed.
3. **Can one phone number have more than one contact type for a researcher?** Current schema allows it; verify that this matches the intended rules.
4. **Are researcher specializations stored as one value or multiple per researcher?** Current schema allows only one scalar value per row. If multiple independently managed specializations are required, introduce a separate relation.
5. **Should historical audit records survive deletion of a species?** Current foreign key from `Species_Status_Audit` to `Fish_Species` may prevent deletion of referenced species; decide an intentional retention policy.

## 5. Final conclusion for the report

Based on the declared keys and the intended functional dependencies, the Marine Ecosystem Management System is **designed to satisfy 3NF**. Entity details are separated into independent relations, multi-valued researcher contacts are modeled in their own table, and the `DNA_Sample` relation references related entities through foreign keys. The final 3NF claim is conditional on confirmation of the business rules, especially the meaning and uniqueness scope of `sampleID`.

## 6. Suggested evidence for Chapter 4.6

- Include the relations, candidate keys and FD table above.
- Show the ER diagram with keys and cardinalities.
- Include `SHOW CREATE TABLE DNA_Sample;` and `SHOW CREATE TABLE Researcher_Contact;` screenshots to evidence declared keys.
- Explain why no schema decomposition was needed **if** the business-rule verification confirms these FDs.
