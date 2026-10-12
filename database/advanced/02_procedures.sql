-- MySQL 8.0.16+. Requires database/schema/01_create_tables.sql.
-- Run with the mysql client (or a SQL editor supporting DELIMITER).
-- Replaces only these procedures; does not change existing table data.
-- Procedures use invoker privileges and never commit or roll back the caller's
-- transaction. Constraints remain the final safeguard against concurrent writes.
USE Marine_Ecosystem_Database;

DELIMITER $$

DROP PROCEDURE IF EXISTS AddDNASample$$
CREATE PROCEDURE AddDNASample(
    IN p_speciesID INT,
    IN p_sampleID INT,
    IN p_collection_date DATE,
    IN p_gene_marker VARCHAR(50),
    IN p_DNA_quality VARCHAR(20),
    IN p_researcherID INT,
    IN p_vesselID INT,
    IN p_locationID INT
)
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    DECLARE EXIT HANDLER FOR 1062
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A sample with this speciesID and sampleID already exists';
    DECLARE EXIT HANDLER FOR 1452
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A referenced species, researcher, vessel or location no longer exists';

    IF p_speciesID IS NULL OR p_sampleID IS NULL
       OR p_collection_date IS NULL OR p_gene_marker IS NULL
       OR p_researcherID IS NULL OR p_vesselID IS NULL OR p_locationID IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Required DNA sample parameters cannot be NULL';
    END IF;
    IF p_collection_date < '1900-01-01' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Collection date must be on or after 1900-01-01';
    END IF;
    IF p_DNA_quality IS NOT NULL AND p_DNA_quality NOT IN ('High', 'Medium', 'Low') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'DNA quality must be High, Medium, Low or NULL';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Fish_Species WHERE speciesID = p_speciesID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Species does not exist';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Researcher WHERE researcherID = p_researcherID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Researcher does not exist';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Research_Vehicle WHERE vesselID = p_vesselID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Research vessel does not exist';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Ocean_Sampling_Region WHERE locationID = p_locationID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sampling location does not exist';
    END IF;

    -- sampleID is unique within a species, not across the whole table.
    INSERT INTO DNA_Sample (
        speciesID, sampleID, collection_date, gene_marker, DNA_quality,
        researcherID, vesselID, locationID
    ) VALUES (
        p_speciesID, p_sampleID, p_collection_date, p_gene_marker, p_DNA_quality,
        p_researcherID, p_vesselID, p_locationID
    );
END$$

DROP PROCEDURE IF EXISTS UpdateSpeciesStatus$$
CREATE PROCEDURE UpdateSpeciesStatus(
    IN p_speciesID INT,
    IN p_conservation_status VARCHAR(30)
)
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    IF p_speciesID IS NULL
       OR NOT EXISTS (SELECT 1 FROM Fish_Species WHERE speciesID = p_speciesID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Species does not exist';
    END IF;
    -- NULL is allowed by the existing schema and represents an unknown status.
    IF p_conservation_status IS NOT NULL AND p_conservation_status NOT IN (
        'Not Evaluated', 'Data Deficient', 'Least Concern', 'Near Threatened',
        'Vulnerable', 'Endangered', 'Critically Endangered',
        'Extinct in the Wild', 'Extinct'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid conservation status';
    END IF;

    UPDATE Fish_Species
    SET conservation_status = p_conservation_status
    WHERE speciesID = p_speciesID;
    -- No manual audit insert: automatic auditing belongs to Phase D's trigger.
END$$

DROP PROCEDURE IF EXISTS GetSamplesByResearcher$$
CREATE PROCEDURE GetSamplesByResearcher(IN p_researcherID INT)
SQL SECURITY INVOKER
READS SQL DATA
BEGIN
    IF p_researcherID IS NULL
       OR NOT EXISTS (SELECT 1 FROM Researcher WHERE researcherID = p_researcherID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Researcher does not exist';
    END IF;

    -- An existing researcher with no samples receives an empty result set.
    SELECT d.speciesID, d.sampleID, d.collection_date, d.gene_marker,
           d.DNA_quality, d.researcherID, f.common_name, f.scientific_name,
           d.vesselID, v.vessel_name, d.locationID, l.location_name, l.ocean_region
    FROM DNA_Sample d
    INNER JOIN Fish_Species f ON f.speciesID = d.speciesID
    INNER JOIN Research_Vehicle v ON v.vesselID = d.vesselID
    INNER JOIN Ocean_Sampling_Region l ON l.locationID = d.locationID
    WHERE d.researcherID = p_researcherID
    ORDER BY d.collection_date, d.speciesID, d.sampleID;
END$$

DELIMITER ;
