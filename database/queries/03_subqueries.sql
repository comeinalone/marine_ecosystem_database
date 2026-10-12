USE Marine_Ecosystem_Database;
-- Nested subqueries: researchers who collected endangered-species samples.
-- Expected researcher IDs: 1,2.
SELECT researcherID, first_name, last_name FROM Researcher
WHERE researcherID IN (
    SELECT researcherID FROM DNA_Sample WHERE speciesID IN (
        SELECT speciesID FROM Fish_Species
        WHERE conservation_status IN ('Endangered', 'Critically Endangered')
    )
) ORDER BY researcherID;

-- Correlated NOT EXISTS: species with no sample. Expected empty result.
SELECT f.speciesID, f.common_name FROM Fish_Species f
WHERE NOT EXISTS (SELECT 1 FROM DNA_Sample d WHERE d.speciesID = f.speciesID);

-- Correlated count: expected researchers 1 and 2 with two samples each.
SELECT r.researcherID, r.first_name FROM Researcher r
WHERE (SELECT COUNT(*) FROM DNA_Sample d WHERE d.researcherID = r.researcherID) > 1
ORDER BY r.researcherID;

-- Reusable functions: expected 2 and 2; unknown IDs return zero.
SELECT CountSpeciesSamples(301) AS whale_shark_samples,
       CountResearcherSamples(1) AS researcher_samples;
