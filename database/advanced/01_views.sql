
USE Marine_Ecosystem_Database;

-- VIEW 1: Endangered fish species

CREATE OR REPLACE VIEW EndangeredSpeciesView AS
SELECT
    speciesID,
    common_name,
    scientific_name,
    conservation_status
FROM Fish_Species
WHERE conservation_status IN (
    'Endangered',
    'Critically Endangered'
);


-- VIEW 2: Number of DNA samples per species

CREATE OR REPLACE VIEW SpeciesSampleSummary AS
SELECT
    f.speciesID,
    f.common_name,
    f.scientific_name,
    f.conservation_status,
    COUNT(d.sampleID) AS total_samples
FROM Fish_Species f
LEFT JOIN DNA_Sample d
    ON f.speciesID = d.speciesID
GROUP BY
    f.speciesID,
    f.common_name,
    f.scientific_name,
    f.conservation_status;


-- VIEW 3: Researcher sample report

CREATE OR REPLACE VIEW ResearcherSampleReport AS
SELECT
    r.researcherID,
    r.first_name,
    r.last_name,
    r.researcher_specialization,
    COUNT(d.sampleID) AS total_samples_collected
FROM Researcher r
LEFT JOIN DNA_Sample d
    ON r.researcherID = d.researcherID
GROUP BY
    r.researcherID,
    r.first_name,
    r.last_name,
    r.researcher_specialization;


-- VIEW 4: Sampling location report

CREATE OR REPLACE VIEW SamplingLocationReport AS
SELECT
    l.locationID,
    l.location_name,
    l.ocean_region,
    COUNT(d.sampleID) AS total_samples_collected,
    COUNT(DISTINCT d.speciesID) AS species_sampled
FROM Ocean_Sampling_Region l
LEFT JOIN DNA_Sample d
    ON l.locationID = d.locationID
GROUP BY
    l.locationID,
    l.location_name,
    l.ocean_region;

