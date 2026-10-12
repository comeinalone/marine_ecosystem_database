USE Marine_Ecosystem_Database;
-- DQL: expected five species and six sample rows in the original seed data.
SELECT speciesID, common_name, conservation_status FROM Fish_Species ORDER BY speciesID;
SELECT speciesID, sampleID, collection_date FROM DNA_Sample ORDER BY speciesID, sampleID;
SELECT * FROM Fish_Species WHERE conservation_status = 'Endangered' ORDER BY speciesID;
-- Expected IDs: 301, 305.

-- DDL isolated to a session-local temporary table; no persistent schema changes.
CREATE TEMPORARY TABLE Query_Demo (id INT PRIMARY KEY, note VARCHAR(80) NOT NULL) ENGINE=InnoDB;
ALTER TABLE Query_Demo ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT FALSE;
-- DML: INSERT, UPDATE and DELETE. Expected final result: (1, 'Reviewed sample', 1).
INSERT INTO Query_Demo (id, note) VALUES (1, 'Example sample'), (2, 'Discard me');
UPDATE Query_Demo SET note = 'Reviewed sample', reviewed = TRUE WHERE id = 1;
DELETE FROM Query_Demo WHERE id = 2;
SELECT * FROM Query_Demo;
DROP TEMPORARY TABLE Query_Demo;

-- Views: expected row counts 3, 5, 4 and 3 respectively.
SELECT * FROM EndangeredSpeciesView ORDER BY speciesID;
SELECT * FROM SpeciesSampleSummary ORDER BY speciesID;
SELECT * FROM ResearcherSampleReport ORDER BY researcherID;
SELECT * FROM SamplingLocationReport ORDER BY locationID;
