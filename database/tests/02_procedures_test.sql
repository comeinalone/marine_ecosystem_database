-- Phase B manual tests. Actual results: NOT RUN (no local MySQL runtime).
-- Prerequisites: schema, seed data and advanced/02_procedures.sql loaded.
-- Run in one session, with no other writers, on a disposable test database.
-- Never load the procedure definitions inside a test transaction: DDL commits.
USE Marine_Ecosystem_Database;

-- T1: expected two rows: (301, 1) and (304, 1), in date order.
CALL GetSamplesByResearcher(1);

START TRANSACTION;
SET @before_samples = (SELECT COUNT(*) FROM DNA_Sample);
SET @before_status = (SELECT conservation_status FROM Fish_Species WHERE speciesID = 301);

-- T2: expected both inserts succeed: the same sampleID belongs to two species.
-- These IDs must be unused; the supplied seed data meets that requirement.
CALL AddDNASample(301, 900001, '2026-04-01', 'COI', 'High', 1, 101, 201);
CALL AddDNASample(302, 900001, '2026-04-02', '16S', NULL, 1, 101, 201);
SELECT COUNT(*) = 2 AS composite_key_and_nullable_quality_pass
FROM DNA_Sample WHERE speciesID IN (301, 302) AND sampleID = 900001;

-- T3: expected 1 for each assertion, including repeated identical updates.
CALL UpdateSpeciesStatus(301, 'Vulnerable');
SELECT conservation_status = 'Vulnerable' AS status_update_pass
FROM Fish_Species WHERE speciesID = 301;
CALL UpdateSpeciesStatus(301, 'Vulnerable');
CALL UpdateSpeciesStatus(301, NULL);
SELECT conservation_status IS NULL AS nullable_status_pass
FROM Fish_Species WHERE speciesID = 301;
ROLLBACK;

-- T4: expected 1 for both assertions; baseline data has been restored.
SELECT COUNT(*) = @before_samples AS sample_rollback_pass FROM DNA_Sample;
SELECT conservation_status <=> @before_status AS status_rollback_pass
FROM Fish_Species WHERE speciesID = 301;

-- T5: existing researcher without samples returns an empty result set.
START TRANSACTION;
INSERT INTO Researcher (
    researcherID, first_name, last_name, researcher_specialization,
    email_address, date_of_birth
) VALUES (900001, 'Test', 'Researcher', 'Genetics',
          'procedure-test@example.invalid', '2000-01-01');
CALL GetSamplesByResearcher(900001);
ROLLBACK;

-- Negative tests: run EACH commented CALL individually in an interactive client.
-- Expected SQLSTATE 45000 and the indicated message; no row should be changed.
-- These are intentionally commented so a batch client stopping on an expected
-- error cannot skip the ROLLBACK statements in the success tests above.

-- T6: duplicate composite key: 'A sample with this speciesID and sampleID already exists'.
-- CALL AddDNASample(301, 1, '2026-04-01', 'COI', 'High', 1, 101, 201);
-- T7: missing species: 'Species does not exist'.
-- CALL AddDNASample(-1, 1, '2026-04-01', 'COI', 'High', 1, 101, 201);
-- T8: missing researcher: 'Researcher does not exist'.
-- CALL AddDNASample(301, 900001, '2026-04-01', 'COI', 'High', -1, 101, 201);
-- T9: missing vessel: 'Research vessel does not exist'.
-- CALL AddDNASample(301, 900001, '2026-04-01', 'COI', 'High', 1, -1, 201);
-- T10: missing location: 'Sampling location does not exist'.
-- CALL AddDNASample(301, 900001, '2026-04-01', 'COI', 'High', 1, 101, -1);
-- T11: required NULL: 'Required DNA sample parameters cannot be NULL'.
-- CALL AddDNASample(301, NULL, '2026-04-01', 'COI', 'High', 1, 101, 201);
-- T12: invalid quality: 'DNA quality must be High, Medium, Low or NULL'.
-- CALL AddDNASample(301, 900001, '2026-04-01', 'COI', 'Invalid', 1, 101, 201);
-- T13: invalid date: 'Collection date must be on or after 1900-01-01'.
-- CALL AddDNASample(301, 900001, '1899-12-31', 'COI', 'High', 1, 101, 201);
-- T14: missing species: 'Species does not exist'.
-- CALL UpdateSpeciesStatus(-1, 'Vulnerable');
-- T15: invalid status: 'Invalid conservation status'.
-- CALL UpdateSpeciesStatus(301, 'Invalid');
-- T16: missing researcher: 'Researcher does not exist'.
-- CALL GetSamplesByResearcher(-1);
-- T17: NULL researcher: 'Researcher does not exist'.
-- CALL GetSamplesByResearcher(NULL);
