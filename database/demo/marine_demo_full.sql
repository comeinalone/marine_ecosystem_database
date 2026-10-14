-- Separate demo database: never touches Marine_Ecosystem_Database.


-- SOURCE: database/schema/01_create_tables.sql
-- Marine Ecosystem Management System
-- Complete schema for MySQL 8.0.16+
-- Creates a fresh database; does not drop an existing database.

CREATE DATABASE Marine_Ecosystem_Demo;
USE Marine_Ecosystem_Demo;

CREATE TABLE Researcher (
    researcherID INT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    middle_name VARCHAR(50),
    last_name VARCHAR(50) NOT NULL,
    researcher_specialization VARCHAR(100) NOT NULL,
    email_address VARCHAR(100) NOT NULL UNIQUE,
    date_of_birth DATE NOT NULL,
    CONSTRAINT chk_researcher_dob CHECK (date_of_birth >= '1900-01-01')
);

CREATE TABLE Researcher_Contact (
    researcherID INT NOT NULL,
    contact_type VARCHAR(20) NOT NULL,
    phone_number VARCHAR(15) NOT NULL,
    PRIMARY KEY (researcherID, contact_type, phone_number),
    CONSTRAINT fk_contact_researcher FOREIGN KEY (researcherID)
        REFERENCES Researcher(researcherID),
    CONSTRAINT chk_contact_type CHECK (contact_type IN ('Mobile', 'Home', 'Work', 'Other'))
);

CREATE TABLE Marine_Biologist (
    researcherID INT PRIMARY KEY,
    field_experience INT CHECK (field_experience >= 0),
    diving_certified VARCHAR(10),
    CONSTRAINT fk_biologist_researcher FOREIGN KEY (researcherID)
        REFERENCES Researcher(researcherID),
    CONSTRAINT chk_diving_certified CHECK (diving_certified IN ('Yes', 'No'))
);

CREATE TABLE Geneticist (
    researcherID INT PRIMARY KEY,
    laboratory_name VARCHAR(100),
    research_focus VARCHAR(100),
    CONSTRAINT fk_geneticist_researcher FOREIGN KEY (researcherID)
        REFERENCES Researcher(researcherID)
);

CREATE TABLE Research_Vehicle (
    vesselID INT PRIMARY KEY,
    vessel_name VARCHAR(100) NOT NULL,
    vessel_capacity INT CHECK (vessel_capacity > 0),
    home_port VARCHAR(100) NOT NULL
);

CREATE TABLE Ocean_Sampling_Region (
    locationID INT PRIMARY KEY,
    location_name VARCHAR(100) NOT NULL,
    ocean_region VARCHAR(100) NOT NULL
);

CREATE TABLE Fish_Species (
    speciesID INT PRIMARY KEY,
    common_name VARCHAR(50) NOT NULL,
    scientific_name VARCHAR(100) NOT NULL UNIQUE,
    conservation_status VARCHAR(30),
    CONSTRAINT chk_conservation_status CHECK (
        conservation_status IN (
            'Not Evaluated',
            'Data Deficient',
            'Least Concern',
            'Near Threatened',
            'Vulnerable',
            'Endangered',
            'Critically Endangered',
            'Extinct in the Wild',
            'Extinct'
        )
    )
);

CREATE TABLE DNA_Sample (
    speciesID INT NOT NULL,
    sampleID INT NOT NULL,
    collection_date DATE NOT NULL,
    gene_marker VARCHAR(50) NOT NULL,
    DNA_quality VARCHAR(20),
    researcherID INT NOT NULL,
    vesselID INT NOT NULL,
    locationID INT NOT NULL,
    PRIMARY KEY (speciesID, sampleID),
    INDEX idx_dna_researcher (researcherID),
    INDEX idx_dna_vessel (vesselID),
    INDEX idx_dna_location (locationID),
    CONSTRAINT fk_sample_species FOREIGN KEY (speciesID)
        REFERENCES Fish_Species(speciesID),
    CONSTRAINT fk_sample_researcher FOREIGN KEY (researcherID)
        REFERENCES Researcher(researcherID),
    CONSTRAINT fk_sample_vessel FOREIGN KEY (vesselID)
        REFERENCES Research_Vehicle(vesselID),
    CONSTRAINT fk_sample_location FOREIGN KEY (locationID)
        REFERENCES Ocean_Sampling_Region(locationID),
    CONSTRAINT chk_dna_quality CHECK (DNA_quality IN ('High', 'Medium', 'Low')),
    CONSTRAINT chk_collection_date CHECK (collection_date >= '1900-01-01')
);

-- Future trigger target: historical conservation-status changes.
CREATE TABLE Species_Status_Audit (
    auditID INT AUTO_INCREMENT PRIMARY KEY,
    speciesID INT NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_species FOREIGN KEY (speciesID)
        REFERENCES Fish_Species(speciesID)
);

-- Verification queries
SHOW TABLES;
SHOW CREATE TABLE DNA_Sample;
SHOW INDEX FROM DNA_Sample;



-- SOURCE: database/advanced/01_views.sql

USE Marine_Ecosystem_Demo;

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




-- SOURCE: database/advanced/02_procedures.sql
-- MySQL 8.0.16+. Requires database/schema/01_create_tables.sql.
-- Run with the mysql client (or a SQL editor supporting DELIMITER).
-- Replaces only these procedures; does not change existing table data.
-- Procedures use invoker privileges and never commit or roll back the caller's
-- transaction. Constraints remain the final safeguard against concurrent writes.
USE Marine_Ecosystem_Demo;

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



-- SOURCE: database/advanced/03_functions.sql
-- Install after the schema. These functions read current data, so they are
-- NOT DETERMINISTIC. Missing IDs (including NULL) return zero.
-- With binary logging, the DBA may need routine-creation privileges or a
-- deliberate log_bin_trust_function_creators configuration; see README.
USE Marine_Ecosystem_Demo;
DELIMITER $$
DROP FUNCTION IF EXISTS CountSpeciesSamples$$
CREATE FUNCTION CountSpeciesSamples(p_speciesID INT)
RETURNS BIGINT
NOT DETERMINISTIC
READS SQL DATA
SQL SECURITY INVOKER
BEGIN
    RETURN (SELECT COUNT(*) FROM DNA_Sample WHERE speciesID = p_speciesID);
END$$

DROP FUNCTION IF EXISTS CountResearcherSamples$$
CREATE FUNCTION CountResearcherSamples(p_researcherID INT)
RETURNS BIGINT
NOT DETERMINISTIC
READS SQL DATA
SQL SECURITY INVOKER
BEGIN
    RETURN (SELECT COUNT(*) FROM DNA_Sample WHERE researcherID = p_researcherID);
END$$
DELIMITER ;



-- SOURCE: database/advanced/04_triggers.sql
USE Marine_Ecosystem_Demo;
-- AFTER UPDATE ensures rejected updates do not create audit entries.
-- The audit insert is part of the same transaction as the species update.
DELIMITER $$
DROP TRIGGER IF EXISTS AuditSpeciesStatusChange$$
CREATE TRIGGER AuditSpeciesStatusChange
AFTER UPDATE ON Fish_Species
FOR EACH ROW
BEGIN
    IF NOT (OLD.conservation_status <=> NEW.conservation_status) THEN
        INSERT INTO Species_Status_Audit (speciesID, old_status, new_status, changed_at)
        VALUES (NEW.speciesID, OLD.conservation_status, NEW.conservation_status, CURRENT_TIMESTAMP);
    END IF;
END$$
DELIMITER ;



-- SOURCE: synthetic demo dataset
-- Fresh demonstration dataset. Synthetic records, NOT scientific observations.

USE `Marine_Ecosystem_Demo`;

START TRANSACTION;

INSERT INTO `Researcher` VALUES
(1, 'Sana', NULL, 'Khan', 'Marine Biology', 'sana.khan1@example.org', '1983-02-02'),
(2, 'Sana', NULL, 'Khan', 'Genetics', 'sana.khan2@example.org', '1986-03-03'),
(3, 'Aarav', NULL, 'Shah', 'Oceanography', 'aarav.shah3@example.org', '1989-04-04'),
(4, 'Priya', NULL, 'Nair', 'Fisheries Science', 'priya.nair4@example.org', '1992-05-05'),
(5, 'Rohan', NULL, 'Mehta', 'Marine Biology', 'rohan.mehta5@example.org', '1995-06-06'),
(6, 'Ananya', NULL, 'Rao', 'Genetics', 'ananya.rao6@example.org', '1998-07-07'),
(7, 'Meera', NULL, 'Iyer', 'Marine Ecology', 'meera.iyer7@example.org', '1982-08-08'),
(8, 'Ishaan', NULL, 'Sen', 'Molecular Genetics', 'ishaan.sen8@example.org', '1985-09-09'),
(9, 'Kabir', NULL, 'Kapoor', 'Conservation Biology', 'kabir.kapoor9@example.org', '1988-10-10'),
(10, 'Tara', NULL, 'Desai', 'Oceanography', 'tara.desai10@example.org', '1991-11-11'),
(11, 'Zoya', NULL, 'Ali', 'Fisheries Science', 'zoya.ali11@example.org', '1994-12-12'),
(12, 'Dev', NULL, 'Patel', 'Marine Biology', 'dev.patel12@example.org', '1997-01-13'),
(13, 'Neha', NULL, 'Fernandes', 'Genetics', 'neha.fernandes13@example.org', '1981-02-14'),
(14, 'Arjun', NULL, 'Verma', 'Marine Ecology', 'arjun.verma14@example.org', '1984-03-15'),
(15, 'Aisha', NULL, 'Sheikh', 'Molecular Genetics', 'aisha.sheikh15@example.org', '1987-04-16'),
(16, 'Vivaan', NULL, 'Joshi', 'Conservation Biology', 'vivaan.joshi16@example.org', '1990-05-17'),
(17, 'Riya', NULL, 'Menon', 'Oceanography', 'riya.menon17@example.org', '1993-06-18'),
(18, 'Aditya', NULL, 'Kulkarni', 'Fisheries Science', 'aditya.kulkarni18@example.org', '1996-07-19'),
(19, 'Nisha', NULL, 'Singh', 'Marine Biology', 'nisha.singh19@example.org', '1980-08-20'),
(20, 'Farhan', NULL, 'Ansari', 'Genetics', 'farhan.ansari20@example.org', '1983-09-21'),
(21, 'Ira', NULL, 'Das', 'Marine Ecology', 'ira.das21@example.org', '1986-10-22'),
(22, 'Anika', NULL, 'Sethi', 'Molecular Genetics', 'anika.sethi22@example.org', '1989-11-23'),
(23, 'Rahul', NULL, 'Bose', 'Conservation Biology', 'rahul.bose23@example.org', '1992-12-24'),
(24, 'Pooja', NULL, 'Kamat', 'Oceanography', 'pooja.kamat24@example.org', '1995-01-25'),
(25, 'Yash', NULL, 'Naik', 'Fisheries Science', 'yash.naik25@example.org', '1998-02-26'),
(26, 'Shreya', NULL, 'Pillai', 'Marine Biology', 'shreya.pillai26@example.org', '1982-03-27'),
(27, 'Nikhil', NULL, 'Bhat', 'Genetics', 'nikhil.bhat27@example.org', '1985-04-01'),
(28, 'Rehan', NULL, 'Qureshi', 'Marine Ecology', 'rehan.qureshi28@example.org', '1988-05-02'),
(29, 'Avni', NULL, 'Gupta', 'Molecular Genetics', 'avni.gupta29@example.org', '1991-06-03'),
(30, 'Sameer', NULL, 'Chaudhary', 'Conservation Biology', 'sameer.chaudhary30@example.org', '1994-07-04');

INSERT INTO `Researcher_Contact` VALUES
(1, 'Mobile', '9860000001'),
(2, 'Mobile', '9860000002'),
(3, 'Mobile', '9860000003'),
(4, 'Mobile', '9860000004'),
(5, 'Mobile', '9860000005'),
(6, 'Mobile', '9860000006'),
(7, 'Mobile', '9860000007'),
(8, 'Mobile', '9860000008'),
(9, 'Mobile', '9860000009'),
(10, 'Mobile', '9860000010'),
(11, 'Mobile', '9860000011'),
(12, 'Mobile', '9860000012'),
(13, 'Mobile', '9860000013'),
(14, 'Mobile', '9860000014'),
(15, 'Mobile', '9860000015'),
(16, 'Mobile', '9860000016'),
(17, 'Mobile', '9860000017'),
(18, 'Mobile', '9860000018'),
(19, 'Mobile', '9860000019'),
(20, 'Mobile', '9860000020'),
(21, 'Mobile', '9860000021'),
(22, 'Mobile', '9860000022'),
(23, 'Mobile', '9860000023'),
(24, 'Mobile', '9860000024'),
(25, 'Mobile', '9860000025'),
(26, 'Mobile', '9860000026'),
(27, 'Mobile', '9860000027'),
(28, 'Mobile', '9860000028'),
(29, 'Mobile', '9860000029'),
(30, 'Mobile', '9860000030'),
(1, 'Work', '02240000001'),
(2, 'Work', '02240000002'),
(3, 'Work', '02240000003'),
(4, 'Work', '02240000004'),
(5, 'Work', '02240000005'),
(10, 'Work', '02240000010'),
(15, 'Work', '02240000015');

INSERT INTO `Marine_Biologist` VALUES
(1, 5, 'Yes'),
(4, 14, 'Yes'),
(5, 17, 'Yes'),
(7, 2, 'Yes'),
(9, 8, 'No'),
(11, 14, 'Yes'),
(12, 17, 'No'),
(14, 2, 'Yes'),
(16, 8, 'Yes'),
(18, 14, 'No'),
(19, 17, 'Yes'),
(21, 2, 'No'),
(23, 8, 'Yes'),
(25, 14, 'Yes'),
(26, 17, 'Yes'),
(28, 2, 'Yes'),
(30, 8, 'No');

INSERT INTO `Geneticist` VALUES
(2, 'Ocean Research Lab', 'Molecular ecology'),
(6, 'Coastal Genomics Lab', 'Population genetics'),
(8, 'Ocean Research Lab', 'Molecular ecology'),
(13, 'Marine DNA Centre', 'DNA barcoding'),
(15, 'Coastal Genomics Lab', 'Population genetics'),
(20, 'Ocean Research Lab', 'Molecular ecology'),
(22, 'Marine DNA Centre', 'DNA barcoding'),
(27, 'Coastal Genomics Lab', 'Population genetics'),
(29, 'Ocean Research Lab', 'Molecular ecology');

INSERT INTO `Research_Vehicle` VALUES
(101, 'Ocean Explorer', 24, 'Mumbai'),
(102, 'Marine Discovery', 16, 'Kochi'),
(103, 'Blue Horizon', 30, 'Goa'),
(104, 'Coral Voyager', 20, 'Port Blair'),
(105, 'Sea Quest', 12, 'Mangaluru'),
(106, 'Nereid Surveyor', 18, 'Chennai'),
(107, 'Sagar Shakti', 22, 'Visakhapatnam'),
(108, 'Reef Guardian', 14, 'Kavaratti'),
(109, 'Tide Seeker', 28, 'Tuticorin'),
(110, 'Pelagic Star', 26, 'Karwar');

INSERT INTO `Ocean_Sampling_Region` VALUES
(201, 'Arabian Sea Station A', 'Arabian Sea'),
(202, 'Bay of Bengal Station B', 'Bay of Bengal'),
(203, 'Lakshadweep Reef', 'Indian Ocean'),
(204, 'Andaman Shelf Site 1', 'Bay of Bengal'),
(205, 'Mumbai Coastal Transect', 'Arabian Sea'),
(206, 'Goa Offshore Station', 'Arabian Sea'),
(207, 'Kochi Estuary Mouth', 'Arabian Sea'),
(208, 'Chennai Coastal Station', 'Bay of Bengal'),
(209, 'Gulf of Mannar Reef', 'Indian Ocean'),
(210, 'Nicobar Deep Station', 'Bay of Bengal'),
(211, 'Malvan Marine Reserve', 'Arabian Sea'),
(212, 'Minicoy Sampling Site', 'Indian Ocean');

INSERT INTO `Fish_Species` VALUES
(301, 'Whale Shark', 'Rhincodon typus', 'Endangered'),
(302, 'Great White Shark', 'Carcharodon carcharias', 'Vulnerable'),
(303, 'Yellowfin Tuna', 'Thunnus albacares', 'Least Concern'),
(304, 'Scalloped Hammerhead', 'Sphyrna lewini', 'Critically Endangered'),
(305, 'Giant Manta Ray', 'Mobula birostris', 'Endangered'),
(306, 'Longtail Tuna', 'Thunnus tonggol', 'Least Concern'),
(307, 'Indian Mackerel', 'Rastrelliger kanagurta', 'Least Concern'),
(308, 'Indian Oil Sardine', 'Sardinella longiceps', 'Least Concern'),
(309, 'Zebra Shark', 'Stegostoma tigrinum', 'Endangered'),
(310, 'Green Sea Turtle', 'Chelonia mydas', 'Endangered'),
(311, 'Clown Anemonefish', 'Amphiprion ocellaris', 'Least Concern'),
(312, 'Blacktip Reef Shark', 'Carcharhinus melanopterus', 'Vulnerable'),
(313, 'Hawksbill Turtle', 'Eretmochelys imbricata', 'Critically Endangered'),
(314, 'Bluefin Trevally', 'Caranx melampygus', 'Least Concern'),
(315, 'Reef Manta Ray', 'Mobula alfredi', 'Vulnerable'),
(316, 'Tiger Shark', 'Galeocerdo cuvier', 'Near Threatened'),
(317, 'Spotted Eagle Ray', 'Aetobatus ocellatus', 'Endangered'),
(318, 'Barramundi', 'Lates calcarifer', 'Least Concern'),
(319, 'Giant Grouper', 'Epinephelus lanceolatus', 'Data Deficient'),
(320, 'Threadfin Bream', 'Nemipterus japonicus', 'Least Concern'),
(321, 'Bumphead Parrotfish', 'Bolbometopon muricatum', 'Vulnerable'),
(322, 'Whitetip Reef Shark', 'Triaenodon obesus', 'Vulnerable'),
(323, 'Common Dolphinfish', 'Coryphaena hippurus', 'Least Concern'),
(324, 'Skipjack Tuna', 'Katsuwonus pelamis', 'Least Concern'),
(325, 'Bigeye Tuna', 'Thunnus obesus', 'Vulnerable'),
(326, 'Silver Pomfret', 'Pampus argenteus', 'Least Concern'),
(327, 'Blue Swimming Crab', 'Portunus pelagicus', 'Not Evaluated'),
(328, 'Porcupinefish', 'Diodon holocanthus', 'Least Concern'),
(329, 'Red Lionfish', 'Pterois volitans', 'Least Concern'),
(330, 'Moorish Idol', 'Zanclus cornutus', 'Least Concern'),
(331, 'Emperor Angelfish', 'Pomacanthus imperator', 'Least Concern'),
(332, 'Napoleon Wrasse', 'Cheilinus undulatus', 'Endangered'),
(333, 'Orange-spotted Grouper', 'Epinephelus coioides', 'Near Threatened'),
(334, 'Yellowtail Scad', 'Atule mate', 'Least Concern'),
(335, 'Lined Surgeonfish', 'Acanthurus lineatus', 'Least Concern'),
(336, 'Blue Tang', 'Paracanthurus hepatus', 'Least Concern'),
(337, 'Sailfish', 'Istiophorus platypterus', 'Least Concern'),
(338, 'Swordfish', 'Xiphias gladius', 'Near Threatened'),
(339, 'Olive Ridley Turtle', 'Lepidochelys olivacea', 'Vulnerable'),
(340, 'Cobia', 'Rachycentron canadum', 'Least Concern'),
(341, 'Shortfin Mako Shark', 'Isurus oxyrinchus', 'Endangered'),
(342, 'Dugong', 'Dugong dugon', 'Vulnerable'),
(343, 'Guitarfish', 'Glaucostegus granulatus', 'Critically Endangered'),
(344, 'Needlefish', 'Tylosurus crocodilus', 'Least Concern'),
(345, 'Milkfish', 'Chanos chanos', 'Least Concern'),
(346, 'Sea Horse', 'Hippocampus kuda', 'Vulnerable'),
(347, 'Mudskipper', 'Periophthalmus argentilineatus', 'Least Concern'),
(348, 'Butterflyfish', 'Chaetodon auriga', 'Least Concern'),
(349, 'Yellow Boxfish', 'Ostracion cubicus', 'Least Concern'),
(350, 'Stonefish', 'Synanceia verrucosa', 'Least Concern');

INSERT INTO `DNA_Sample` VALUES
(301, 1, '2025-01-14', '16S', 'High', 1, 102, 201),
(301, 2, '2025-01-27', 'CYTB', 'High', 1, 103, 201),
(301, 3, '2025-02-09', '12S', 'High', 1, 104, 201),
(301, 4, '2027-03-15', 'ND2', 'Medium', 1, 105, 201),
(301, 5, '2025-03-07', 'COI', 'High', 1, 106, 201),
(301, 6, '2025-03-20', '16S', 'High', 1, 107, 201),
(301, 7, '2025-04-02', 'CYTB', 'High', 24, 108, 201),
(301, 8, '2025-04-15', '12S', 'Medium', 27, 109, 201),
(301, 9, '2025-04-28', 'ND2', 'High', 5, 110, 201),
(301, 10, '2025-05-11', 'COI', 'High', 8, 101, 201),
(301, 11, '2025-05-24', '16S', 'High', 11, 102, 203),
(301, 12, '2025-06-06', 'CYTB', 'Medium', 14, 103, 203),
(301, 13, '2025-06-19', '12S', 'Low', 17, 104, 203),
(301, 14, '2025-07-02', 'ND2', 'High', 20, 105, 203),
(302, 1, '2025-02-02', 'CYTB', 'High', 13, 103, 209),
(302, 2, '2025-02-15', '12S', 'High', 16, 104, 212),
(302, 3, '2025-02-28', 'ND2', 'Medium', 19, 105, 203),
(302, 4, '2025-03-13', 'COI', 'High', 22, 106, 206),
(302, 5, '2025-03-26', '16S', 'High', 25, 107, 209),
(302, 6, '2025-04-08', 'CYTB', 'High', 3, 108, 212),
(302, 7, '2025-04-21', '12S', 'Medium', 6, 109, 203),
(302, 8, '2025-05-04', 'ND2', 'High', 9, 110, 206),
(303, 1, '2025-02-21', '12S', NULL, 2, 104, 201),
(303, 2, '2025-03-06', 'ND2', 'Medium', 2, 105, 201),
(303, 3, '2025-03-19', 'COI', 'High', 2, 106, 201),
(303, 4, '2025-04-01', '16S', 'High', 2, 107, 201),
(303, 5, '2025-04-14', 'CYTB', 'Low', 2, 108, 201),
(303, 6, '2025-04-27', '12S', 'Medium', 10, 109, 201),
(303, 7, '2025-05-10', 'ND2', 'High', 13, 110, 201),
(303, 8, '2025-05-23', 'COI', 'High', 16, 101, 201),
(303, 9, '2025-06-05', '16S', 'High', 19, 102, 201),
(303, 10, '2025-06-18', 'CYTB', 'Medium', 22, 103, 201),
(303, 11, '2025-07-01', '12S', 'High', 25, 104, 201),
(303, 12, '2025-07-14', 'ND2', 'High', 3, 105, 202),
(303, 13, '2025-07-27', 'COI', 'High', 6, 106, 204),
(303, 14, '2025-08-09', '16S', 'Medium', 9, 107, 205),
(303, 15, '2025-08-22', 'CYTB', 'High', 12, 108, 202),
(303, 16, '2025-09-04', '12S', 'High', 15, 109, 204),
(303, 17, '2025-09-17', 'ND2', 'High', 18, 110, 205),
(303, 18, '2025-09-30', 'COI', 'Low', 21, 101, 202),
(303, 19, '2025-10-13', '16S', 'High', 24, 102, 204),
(303, 20, '2025-10-26', 'CYTB', 'High', 27, 103, 205),
(303, 21, '2025-11-08', '12S', 'High', 5, 104, 202),
(304, 1, '2025-03-12', 'ND2', 'Low', 27, 105, 207),
(304, 2, '2025-03-25', 'COI', 'High', 5, 106, 210),
(304, 3, '2025-04-07', '16S', 'High', 8, 107, 201),
(304, 4, '2025-04-20', 'CYTB', 'High', 11, 108, 204),
(304, 5, '2025-05-03', '12S', 'Medium', 14, 109, 207),
(304, 6, '2025-05-16', 'ND2', 'High', 17, 110, 210),
(304, 7, '2025-05-29', 'COI', 'High', 20, 101, 201),
(305, 1, '2025-03-31', 'COI', 'High', 9, 106, 212),
(305, 2, '2025-04-13', '16S', NULL, 12, 107, 203),
(305, 3, '2025-04-26', 'CYTB', 'High', 15, 108, 206),
(305, 4, '2025-05-09', '12S', 'Medium', 18, 109, 209),
(305, 5, '2025-05-22', 'ND2', 'High', 21, 110, 212),
(305, 6, '2025-06-04', 'COI', 'High', 24, 101, 203),
(305, 7, '2025-06-17', '16S', 'High', 27, 102, 206),
(305, 8, '2025-06-30', 'CYTB', 'Medium', 5, 103, 209),
(305, 9, '2025-07-13', '12S', 'High', 8, 104, 212),
(306, 1, '2025-04-19', '16S', 'High', 16, 107, 201),
(306, 2, '2025-05-02', 'CYTB', 'High', 19, 108, 201),
(306, 3, '2025-05-15', '12S', 'Medium', 22, 109, 201),
(306, 4, '2025-05-28', 'ND2', 'High', 25, 110, 201),
(306, 5, '2025-06-10', 'COI', 'High', 3, 101, 201),
(306, 6, '2025-06-23', '16S', 'Low', 6, 102, 201),
(306, 7, '2025-07-06', 'CYTB', 'Medium', 9, 103, 201),
(306, 8, '2025-07-19', '12S', 'High', 12, 104, 201),
(306, 9, '2025-08-01', 'ND2', 'High', 15, 105, 201),
(306, 10, '2025-08-14', 'COI', 'High', 18, 106, 201),
(306, 11, '2025-08-27', '16S', 'Medium', 21, 107, 201),
(306, 12, '2025-09-09', 'CYTB', 'High', 24, 108, 201),
(306, 13, '2025-09-22', '12S', 'High', 27, 109, 207),
(306, 14, '2025-10-05', 'ND2', NULL, 5, 110, 208),
(306, 15, '2025-10-18', 'COI', 'Medium', 8, 101, 206),
(306, 16, '2025-10-31', '16S', 'High', 11, 102, 207);

INSERT INTO `DNA_Sample` VALUES
(306, 17, '2025-11-13', 'CYTB', 'High', 14, 103, 208),
(306, 18, '2025-11-26', '12S', 'High', 17, 104, 206),
(306, 19, '2025-12-09', 'ND2', 'Low', 20, 105, 207),
(306, 20, '2025-12-22', 'COI', 'High', 23, 106, 208),
(307, 1, '2025-05-08', 'CYTB', 'High', 23, 108, 210),
(307, 2, '2025-05-21', '12S', 'Low', 26, 109, 201),
(307, 3, '2025-06-03', 'ND2', NULL, 4, 110, 204),
(307, 4, '2025-06-16', 'COI', 'High', 7, 101, 207),
(307, 5, '2025-06-29', '16S', 'High', 10, 102, 210),
(307, 6, '2025-07-12', 'CYTB', 'Medium', 13, 103, 201),
(307, 7, '2025-07-25', '12S', 'High', 16, 104, 204),
(307, 8, '2025-08-07', 'ND2', 'High', 19, 105, 207),
(307, 9, '2025-08-20', 'COI', 'High', 22, 106, 210),
(307, 10, '2025-09-02', '16S', 'Medium', 25, 107, 201),
(307, 11, '2025-09-15', 'CYTB', 'High', 3, 108, 204),
(307, 12, '2027-03-15', '12S', 'High', 6, 109, 207),
(307, 13, '2025-10-11', 'ND2', 'High', 9, 110, 210),
(307, 14, '2025-10-24', 'COI', 'Medium', 12, 101, 201),
(307, 15, '2025-11-06', '16S', 'Low', 15, 102, 204),
(307, 16, '2025-11-19', 'CYTB', 'High', 18, 103, 207),
(307, 17, '2025-12-02', '12S', 'High', 21, 104, 210),
(307, 18, '2025-12-15', 'ND2', 'Medium', 24, 105, 201),
(307, 19, '2025-12-28', 'COI', 'High', 27, 106, 204),
(307, 20, '2026-01-10', '16S', 'High', 5, 107, 207),
(307, 21, '2026-01-23', 'CYTB', 'High', 8, 108, 210),
(307, 22, '2026-02-05', '12S', 'Medium', 11, 109, 201),
(307, 23, '2026-02-18', 'ND2', 'High', 14, 110, 204),
(307, 24, '2026-03-03', 'COI', 'High', 17, 101, 207),
(308, 1, '2025-05-27', '12S', 'Medium', 5, 109, 202),
(308, 2, '2025-06-09', 'ND2', 'High', 8, 110, 202),
(308, 3, '2025-06-22', 'COI', 'High', 11, 101, 202),
(308, 4, '2025-07-05', '16S', 'High', 14, 102, 202),
(308, 5, '2025-07-18', 'CYTB', 'Medium', 17, 103, 202),
(308, 6, '2025-07-31', '12S', 'High', 20, 104, 202),
(308, 7, '2025-08-13', 'ND2', 'High', 23, 105, 202),
(308, 8, '2025-08-26', 'COI', 'High', 26, 106, 202),
(308, 9, '2025-09-08', '16S', 'Medium', 4, 107, 202),
(308, 10, '2025-09-21', 'CYTB', 'High', 7, 108, 209),
(308, 11, '2025-10-04', '12S', 'Low', 10, 109, 209),
(308, 12, '2025-10-17', 'ND2', 'High', 13, 110, 209),
(308, 13, '2025-10-30', 'COI', 'Medium', 16, 101, 209),
(308, 14, '2025-11-12', '16S', 'High', 19, 102, 209),
(308, 15, '2025-11-25', 'CYTB', NULL, 22, 103, 209),
(309, 1, '2025-06-15', 'ND2', 'High', 12, 110, 208),
(309, 2, '2025-06-28', 'COI', 'High', 15, 101, 211),
(309, 3, '2025-07-11', '16S', 'High', 18, 102, 202),
(309, 4, '2025-07-24', 'CYTB', NULL, 21, 103, 205),
(309, 5, '2025-08-06', '12S', 'High', 24, 104, 208),
(309, 6, '2025-08-19', 'ND2', 'High', 27, 105, 211),
(309, 7, '2025-09-01', 'COI', 'Low', 5, 106, 202),
(309, 8, '2025-09-14', '16S', 'Medium', 8, 107, 205),
(309, 9, '2025-09-27', 'CYTB', 'High', 11, 108, 208),
(309, 10, '2025-10-10', '12S', 'High', 14, 109, 211),
(310, 1, '2025-07-04', 'COI', 'High', 19, 101, 201),
(310, 2, '2025-07-17', '16S', 'High', 22, 102, 204),
(310, 3, '2025-07-30', 'CYTB', 'Low', 25, 103, 207),
(310, 4, '2025-08-12', '12S', 'High', 3, 104, 210),
(310, 5, '2025-08-25', 'ND2', 'High', 6, 105, 201),
(310, 6, '2025-09-07', 'COI', 'High', 9, 106, 204),
(310, 7, '2025-09-20', '16S', 'Medium', 12, 107, 207),
(310, 8, '2025-10-03', 'CYTB', 'High', 15, 108, 210),
(311, 1, '2025-07-23', '16S', 'High', 26, 102, 206),
(311, 2, '2025-08-05', '   ', 'Medium', 4, 103, 209),
(311, 3, '2025-08-18', '12S', 'High', 7, 104, 212),
(311, 4, '2025-08-31', 'ND2', 'High', 10, 105, 203),
(311, 5, '2025-09-13', 'COI', NULL, 13, 106, 206),
(311, 6, '2025-09-26', '16S', 'Medium', 16, 107, 209),
(311, 7, '2025-10-09', 'CYTB', 'High', 19, 108, 212),
(311, 8, '2025-10-22', '12S', 'High', 22, 109, 203),
(311, 9, '2025-11-04', 'ND2', 'High', 25, 110, 206),
(312, 1, '2025-08-11', 'CYTB', 'Medium', 8, 103, 211),
(312, 2, '2025-08-24', '12S', 'High', 11, 104, 202),
(312, 3, '2025-09-06', 'ND2', 'High', 14, 105, 205),
(312, 4, '2025-09-19', 'COI', 'High', 17, 106, 208),
(312, 5, '2025-10-02', '16S', 'Medium', 20, 107, 211);

INSERT INTO `DNA_Sample` VALUES
(312, 6, '2025-10-15', 'CYTB', 'High', 23, 108, 202),
(312, 7, '2025-10-28', '12S', 'High', 26, 109, 205),
(312, 8, '2025-11-10', 'ND2', 'Low', 4, 110, 208),
(312, 9, '2025-11-23', 'COI', 'Medium', 7, 101, 211),
(313, 1, '2025-08-30', '12S', 'High', 15, 104, 204),
(313, 2, '2025-09-12', 'ND2', 'High', 18, 105, 207),
(313, 3, '2025-09-25', 'COI', 'High', 21, 106, 210),
(313, 4, '2025-10-08', '16S', 'Low', 24, 107, 201),
(313, 5, '2025-10-21', 'CYTB', 'High', 27, 108, 204),
(313, 6, '2025-11-03', '12S', NULL, 5, 109, 207),
(313, 7, '2025-11-16', 'ND2', 'High', 8, 110, 210),
(313, 8, '2025-11-29', 'COI', 'Medium', 11, 101, 201),
(313, 9, '2025-12-12', '16S', 'High', 14, 102, 204),
(314, 1, '2025-09-18', 'ND2', 'High', 22, 105, 209),
(314, 2, '2025-10-01', 'COI', 'High', 25, 106, 212),
(314, 3, '2025-10-14', '16S', 'Medium', 3, 107, 203),
(314, 4, '2025-10-27', 'CYTB', 'High', 6, 108, 206),
(314, 5, '2025-11-09', '12S', 'High', 9, 109, 209),
(314, 6, '2025-11-22', 'ND2', 'High', 12, 110, 212),
(314, 7, '2025-12-05', 'COI', 'Medium', 15, 101, 203),
(314, 8, '2025-12-18', '16S', 'High', 18, 102, 206),
(314, 9, '2025-12-31', 'CYTB', 'High', 21, 103, 209),
(315, 1, '2025-10-07', 'COI', 'High', 4, 106, 202),
(315, 2, '2025-10-20', '16S', 'Medium', 7, 107, 205),
(315, 3, '2025-11-02', 'CYTB', 'High', 10, 108, 208),
(315, 4, '2025-11-15', '12S', 'High', 13, 109, 211),
(315, 5, '2025-11-28', 'ND2', 'High', 16, 110, 202),
(315, 6, '2025-12-11', 'COI', 'Medium', 19, 101, 205),
(315, 7, '2025-12-24', '16S', NULL, 22, 102, 208),
(315, 8, '2026-01-06', 'CYTB', 'High', 25, 103, 211),
(315, 9, '2026-01-19', '12S', 'Low', 3, 104, 202),
(316, 1, '2025-10-26', '16S', 'Medium', 11, 107, 207),
(316, 2, '2025-11-08', 'CYTB', 'High', 14, 108, 210),
(316, 3, '2025-11-21', '12S', 'High', 17, 109, 201),
(316, 4, '2025-12-04', 'ND2', 'High', 20, 110, 204),
(316, 5, '2025-12-17', 'COI', 'Low', 23, 101, 207),
(316, 6, '2025-12-30', '16S', 'High', 26, 102, 210),
(316, 7, '2026-01-12', 'CYTB', 'High', 4, 103, 201),
(316, 8, '2026-01-25', '12S', 'High', 7, 104, 204),
(316, 9, '2026-02-07', 'ND2', 'Medium', 10, 105, 207),
(317, 1, '2025-11-14', 'CYTB', 'Low', 18, 108, 212),
(317, 2, '2025-11-27', '12S', 'High', 21, 109, 203),
(317, 3, '2025-12-10', 'ND2', 'High', 24, 110, 206),
(317, 4, '2025-12-23', 'COI', 'Medium', 27, 101, 209),
(317, 5, '2026-01-05', '16S', 'High', 5, 102, 212),
(317, 6, '2026-01-18', 'CYTB', 'High', 8, 103, 203),
(317, 7, '2026-01-31', '12S', 'High', 11, 104, 206),
(317, 8, '2026-02-13', 'ND2', NULL, 14, 105, 209),
(317, 9, '2026-02-26', 'COI', 'High', 17, 106, 212),
(318, 1, '2025-12-03', '12S', 'High', 25, 109, 205),
(318, 2, '2025-12-16', 'ND2', 'High', 3, 110, 208),
(318, 3, '2025-12-29', 'COI', 'Medium', 6, 101, 211),
(318, 4, '2026-01-11', '16S', 'High', 9, 102, 202),
(318, 5, '2026-01-24', 'CYTB', 'High', 12, 103, 205),
(318, 6, '2026-02-06', '12S', 'High', 15, 104, 208),
(318, 7, '2026-02-19', 'ND2', 'Medium', 18, 105, 211),
(318, 8, '2026-03-04', 'COI', 'High', 21, 106, 202),
(318, 9, '2026-03-17', '16S', 'High', 24, 107, 205),
(319, 1, '2025-12-22', 'ND2', 'High', 7, 110, 210),
(319, 2, '2026-01-04', 'COI', 'Medium', 10, 101, 201),
(319, 3, '2026-01-17', '16S', 'High', 13, 102, 204),
(319, 4, '2026-01-30', 'CYTB', 'High', 16, 103, 207),
(319, 5, '2026-02-12', '12S', 'High', 19, 104, 210),
(319, 6, '2026-02-25', 'ND2', 'Low', 22, 105, 201),
(319, 7, '2026-03-10', 'COI', 'High', 25, 106, 204),
(319, 8, '2026-03-23', '16S', 'High', 3, 107, 207),
(319, 9, '2026-04-05', 'CYTB', NULL, 6, 108, 210),
(320, 1, '2026-01-10', 'COI', 'Medium', 14, 101, 203),
(320, 2, '2026-01-23', '16S', 'Low', 17, 102, 206),
(320, 3, '2026-02-05', 'CYTB', 'High', 20, 103, 209),
(320, 4, '2026-02-18', '12S', 'High', 23, 104, 212),
(320, 5, '2026-03-03', 'ND2', 'Medium', 26, 105, 203),
(320, 6, '2026-03-16', 'COI', 'High', 4, 106, 206),
(320, 7, '2026-03-29', '16S', 'High', 7, 107, 209),
(320, 8, '2026-04-11', 'CYTB', 'High', 10, 108, 212);

INSERT INTO `DNA_Sample` VALUES
(320, 9, '2026-04-24', '12S', 'Medium', 13, 109, 203),
(321, 1, '2026-01-29', '16S', 'High', 21, 102, 208),
(321, 2, '2026-02-11', 'CYTB', 'High', 24, 103, 211),
(321, 3, '2026-02-24', '12S', 'High', 27, 104, 202),
(321, 4, '2026-03-09', 'ND2', 'Medium', 5, 105, 205),
(321, 5, '2026-03-22', 'COI', 'High', 8, 106, 208),
(321, 6, '2026-04-04', '16S', 'High', 11, 107, 211),
(321, 7, '2026-04-17', 'CYTB', 'High', 14, 108, 202),
(321, 8, '2026-04-30', '12S', 'Medium', 17, 109, 205),
(321, 9, '2026-05-13', 'ND2', 'High', 20, 110, 208),
(322, 1, '2026-02-17', 'CYTB', 'High', 3, 103, 201),
(322, 2, '2026-03-02', '12S', 'High', 6, 104, 204),
(322, 3, '2026-03-15', 'ND2', 'Medium', 9, 105, 207),
(322, 4, '2026-03-28', 'COI', 'High', 12, 106, 210),
(322, 5, '2026-04-10', '16S', 'High', 15, 107, 201),
(322, 6, '2026-04-23', 'CYTB', 'High', 18, 108, 204),
(322, 7, '2026-05-06', '12S', 'Low', 21, 109, 207),
(322, 8, '2026-05-19', 'ND2', 'High', 24, 110, 210),
(322, 9, '2026-06-01', 'COI', 'High', 27, 101, 201),
(323, 1, '2026-03-08', '12S', 'High', 10, 104, 206),
(323, 2, '2026-03-21', 'ND2', 'Medium', 13, 105, 209),
(323, 3, '2026-04-03', 'COI', 'Low', 16, 106, 212),
(323, 4, '2026-04-16', '16S', 'High', 19, 107, 203),
(323, 5, '2026-04-29', 'CYTB', 'High', 22, 108, 206),
(323, 6, '2026-05-12', '12S', 'Medium', 25, 109, 209),
(323, 7, '2027-03-15', 'ND2', 'High', 3, 110, 212),
(323, 8, '2026-06-07', 'COI', 'High', 6, 101, 203),
(323, 9, '2026-06-20', '16S', 'High', 9, 102, 206),
(324, 1, '2026-03-27', 'ND2', 'Medium', 17, 105, 211),
(324, 2, '2026-04-09', 'COI', 'High', 20, 106, 202),
(324, 3, '2026-04-22', '16S', 'High', 23, 107, 205),
(324, 4, '2026-05-05', 'CYTB', 'High', 26, 108, 208),
(324, 5, '2026-05-18', '12S', 'Medium', 4, 109, 211),
(324, 6, '2026-05-31', 'ND2', 'High', 7, 110, 202),
(324, 7, '2026-06-13', 'COI', 'High', 10, 101, 205),
(324, 8, '2026-06-26', '16S', 'High', 13, 102, 208),
(324, 9, '2026-07-09', 'CYTB', 'Medium', 16, 103, 211),
(325, 1, '2026-04-15', 'COI', 'High', 24, 106, 204),
(325, 2, '2026-04-28', '16S', 'High', 27, 107, 207),
(325, 3, '2026-05-11', 'CYTB', 'High', 5, 108, 210),
(325, 4, '2026-05-24', '12S', 'Medium', 8, 109, 201),
(325, 5, '2026-06-06', 'ND2', 'High', 11, 110, 204),
(325, 6, '2026-06-19', 'COI', 'High', 14, 101, 207),
(325, 7, '2026-07-02', '16S', 'High', 17, 102, 210),
(325, 8, '2026-07-15', 'CYTB', 'Low', 20, 103, 201),
(325, 9, '2026-07-28', '12S', 'High', 23, 104, 204),
(326, 1, '2026-05-04', '16S', NULL, 6, 107, 209),
(326, 2, '2026-05-17', 'CYTB', 'High', 9, 108, 212),
(326, 3, '2026-05-30', '12S', 'Medium', 12, 109, 203),
(326, 4, '2026-06-12', 'ND2', 'Low', 15, 110, 206),
(326, 5, '2026-06-25', 'COI', 'High', 18, 101, 209),
(326, 6, '2026-07-08', '16S', 'High', 21, 102, 212),
(326, 7, '2026-07-21', 'CYTB', 'Medium', 24, 103, 203),
(326, 8, '2026-08-03', '12S', 'High', 27, 104, 206),
(326, 9, '2026-08-16', 'ND2', 'High', 5, 105, 209),
(327, 1, '2026-05-23', 'CYTB', 'High', 13, 108, 202),
(327, 2, '2026-06-05', '12S', 'Medium', 16, 109, 205),
(327, 3, '2026-06-18', 'ND2', 'High', 19, 110, 208),
(327, 4, '2026-07-01', 'COI', 'High', 22, 101, 211),
(327, 5, '2026-07-14', '16S', 'High', 25, 102, 202),
(327, 6, '2026-07-27', 'CYTB', 'Medium', 3, 103, 205),
(327, 7, '2026-08-09', '12S', 'High', 6, 104, 208),
(327, 8, '2026-08-22', 'ND2', 'High', 9, 105, 211),
(327, 9, '2026-09-04', 'COI', 'High', 12, 106, 202),
(328, 1, '2026-06-11', '12S', 'Medium', 20, 109, 207),
(328, 2, '2026-06-24', 'ND2', NULL, 23, 110, 210),
(328, 3, '2026-07-07', 'COI', 'High', 26, 101, 201),
(328, 4, '2026-07-20', '16S', 'High', 4, 102, 204),
(328, 5, '2026-08-02', 'CYTB', 'Medium', 7, 103, 207),
(328, 6, '2026-08-15', '12S', 'High', 10, 104, 210),
(328, 7, '2026-08-28', 'ND2', 'High', 13, 105, 201),
(328, 8, '2026-09-10', 'COI', 'High', 16, 106, 204),
(328, 9, '2025-01-11', '16S', 'Low', 19, 107, 207),
(329, 1, '2026-06-30', 'ND2', 'High', 27, 110, 212),
(329, 2, '2026-07-13', 'COI', 'High', 5, 101, 203);

INSERT INTO `DNA_Sample` VALUES
(329, 3, '2026-07-26', '16S', 'High', 8, 102, 206),
(329, 4, '2026-08-08', 'CYTB', 'Medium', 11, 103, 209),
(329, 5, '2026-08-21', '12S', 'Low', 14, 104, 212),
(329, 6, '2026-09-03', 'ND2', 'High', 17, 105, 203),
(329, 7, '2025-01-04', 'COI', 'High', 20, 106, 206),
(329, 8, '2025-01-17', '16S', 'Medium', 23, 107, 209),
(329, 9, '2025-01-30', 'CYTB', 'High', 26, 108, 212),
(330, 1, '2026-07-19', 'COI', 'Low', 9, 101, 205),
(330, 2, '2026-08-01', '16S', 'High', 12, 102, 208),
(330, 3, '2026-08-14', 'CYTB', NULL, 15, 103, 211),
(330, 4, '2026-08-27', '12S', 'High', 18, 104, 202),
(330, 5, '2026-09-09', 'ND2', 'High', 21, 105, 205),
(330, 6, '2025-01-10', 'COI', 'High', 24, 106, 208),
(330, 7, '2025-01-23', '16S', 'Medium', 27, 107, 211),
(330, 8, '2025-02-05', 'CYTB', 'High', 5, 108, 202),
(330, 9, '2025-02-18', '12S', 'High', 8, 109, 205),
(331, 1, '2026-08-07', '16S', 'High', 16, 102, 210),
(331, 2, '2026-08-20', 'CYTB', 'Medium', 19, 103, 201),
(331, 3, '2026-09-02', '12S', 'High', 22, 104, 204),
(331, 4, '2025-01-03', 'ND2', 'High', 25, 105, 207),
(331, 5, '2025-01-16', 'COI', 'High', 3, 106, 210),
(331, 6, '2025-01-29', '16S', 'Medium', 6, 107, 201),
(331, 7, '2025-02-11', 'CYTB', 'High', 9, 108, 204),
(331, 8, '2025-02-24', '12S', 'High', 12, 109, 207),
(331, 9, '2025-03-09', 'ND2', 'High', 15, 110, 210),
(332, 1, '2026-08-26', 'CYTB', 'Medium', 23, 103, 203),
(332, 2, '2026-09-08', '12S', 'High', 26, 104, 206),
(332, 3, '2025-01-09', 'ND2', 'High', 4, 105, 209),
(332, 4, '2025-01-22', 'COI', NULL, 7, 106, 212),
(332, 5, '2025-02-04', '16S', 'Medium', 10, 107, 203),
(332, 6, '2025-02-17', 'CYTB', 'Low', 13, 108, 206),
(332, 7, '2025-03-02', '12S', 'High', 16, 109, 209),
(332, 8, '2025-03-15', 'ND2', 'High', 19, 110, 212),
(332, 9, '2025-03-28', 'COI', 'Medium', 22, 101, 203),
(333, 1, '2025-01-02', '12S', 'High', 5, 104, 208),
(333, 2, '2025-01-15', 'ND2', 'Low', 8, 105, 211),
(333, 3, '2025-01-28', 'COI', 'High', 11, 106, 202),
(333, 4, '2025-02-10', '16S', 'Medium', 14, 107, 205),
(333, 5, '2025-02-23', 'CYTB', 'High', 17, 108, 208),
(333, 6, '2025-03-08', '12S', 'High', 20, 109, 211),
(333, 7, '2025-03-21', 'ND2', 'High', 23, 110, 202),
(333, 8, '2025-04-03', 'COI', 'Medium', 26, 101, 205),
(333, 9, '2025-04-16', '16S', 'High', 4, 102, 208),
(334, 1, '2025-01-21', 'ND2', 'High', 12, 105, 201),
(334, 2, '2025-02-03', 'COI', 'High', 15, 106, 204),
(334, 3, '2025-02-16', '16S', 'Medium', 18, 107, 207),
(334, 4, '2025-03-01', 'CYTB', 'High', 21, 108, 210),
(334, 5, '2025-03-14', '12S', NULL, 24, 109, 201),
(334, 6, '2025-03-27', 'ND2', 'High', 27, 110, 204),
(334, 7, '2025-04-09', 'COI', 'Medium', 5, 101, 207),
(334, 8, '2025-04-22', '16S', 'High', 8, 102, 210),
(334, 9, '2025-05-05', 'CYTB', 'High', 11, 103, 201),
(335, 1, '2025-02-09', 'COI', 'High', 19, 106, 206),
(335, 2, '2025-02-22', '16S', 'Medium', 22, 107, 209),
(335, 3, '2025-03-07', 'CYTB', 'High', 25, 108, 212),
(335, 4, '2025-03-20', '12S', 'High', 3, 109, 203),
(335, 5, '2025-04-02', 'ND2', 'High', 6, 110, 206),
(335, 6, '2025-04-15', 'COI', 'Medium', 9, 101, 209),
(335, 7, '2025-04-28', '16S', 'Low', 12, 102, 212),
(335, 8, '2025-05-11', 'CYTB', 'High', 15, 103, 203),
(335, 9, '2025-05-24', '12S', 'High', 18, 104, 206),
(336, 1, '2025-02-28', '16S', 'Medium', 26, 107, 211),
(336, 2, '2025-03-13', 'CYTB', 'High', 4, 108, 202),
(336, 3, '2025-03-26', '12S', 'Low', 7, 109, 205),
(336, 4, '2025-04-08', 'ND2', 'High', 10, 110, 208),
(336, 5, '2025-04-21', 'COI', 'Medium', 13, 101, 211),
(336, 6, '2025-05-04', '16S', NULL, 16, 102, 202),
(336, 7, '2025-05-17', 'CYTB', 'High', 19, 103, 205),
(336, 8, '2025-05-30', '12S', 'High', 22, 104, 208),
(336, 9, '2025-06-12', 'ND2', 'Medium', 25, 105, 211),
(337, 1, '2025-03-19', 'CYTB', 'High', 8, 108, 204),
(337, 2, '2025-04-01', '12S', 'High', 11, 109, 207),
(337, 3, '2025-04-14', 'ND2', 'High', 14, 110, 210),
(337, 4, '2025-04-27', 'COI', 'Medium', 17, 101, 201),
(337, 5, '2025-05-10', '16S', 'High', 20, 102, 204);

INSERT INTO `DNA_Sample` VALUES
(337, 6, '2025-05-23', 'CYTB', 'High', 23, 103, 207),
(337, 7, '2025-06-05', '12S', 'High', 26, 104, 210),
(337, 8, '2025-06-18', 'ND2', 'Medium', 4, 105, 201),
(337, 9, '2025-07-01', 'COI', 'High', 7, 106, 204),
(338, 1, '2025-04-07', '12S', 'High', 15, 109, 209),
(338, 2, '2025-04-20', 'ND2', 'High', 18, 110, 212),
(338, 3, '2025-05-03', 'COI', 'Medium', 21, 101, 203),
(338, 4, '2025-05-16', '16S', 'High', 24, 102, 206),
(338, 5, '2025-05-29', 'CYTB', 'High', 27, 103, 209),
(338, 6, '2025-06-11', '12S', 'High', 5, 104, 212),
(338, 7, '2025-06-24', 'ND2', NULL, 8, 105, 203),
(338, 8, '2025-07-07', 'COI', 'Low', 11, 106, 206),
(338, 9, '2025-07-20', '16S', 'High', 14, 107, 209),
(339, 1, '2025-04-26', 'ND2', 'High', 22, 110, 202),
(339, 2, '2025-05-09', 'COI', 'Medium', 25, 101, 205),
(339, 3, '2025-05-22', '16S', 'High', 3, 102, 208),
(339, 4, '2025-06-04', 'CYTB', 'Low', 6, 103, 211),
(339, 5, '2025-06-17', '12S', 'High', 9, 104, 202),
(339, 6, '2025-06-30', 'ND2', 'Medium', 12, 105, 205),
(339, 7, '2025-07-13', 'COI', 'High', 15, 106, 208),
(339, 8, '2025-07-26', '16S', 'High', 18, 107, 211),
(339, 9, '2025-08-08', 'CYTB', 'High', 21, 108, 202),
(340, 1, '2025-05-15', 'COI', 'Medium', 4, 101, 207),
(340, 2, '2025-05-28', '16S', 'High', 7, 102, 210),
(340, 3, '2025-06-10', 'CYTB', 'High', 10, 103, 201),
(340, 4, '2025-06-23', '12S', 'High', 13, 104, 204),
(340, 5, '2025-07-06', 'ND2', 'Medium', 16, 105, 207),
(340, 6, '2025-07-19', 'COI', 'High', 19, 106, 210),
(340, 7, '2025-08-01', '16S', 'High', 22, 107, 201),
(340, 8, '2025-08-14', 'CYTB', NULL, 25, 108, 204),
(340, 9, '2025-08-27', '12S', 'Medium', 3, 109, 207),
(341, 1, '2025-06-03', '16S', 'High', 11, 102, 212),
(341, 2, '2025-06-16', 'CYTB', 'High', 14, 103, 203),
(341, 3, '2025-06-29', '12S', 'High', 17, 104, 206),
(341, 4, '2025-07-12', 'ND2', 'Medium', 20, 105, 209),
(342, 1, '2025-06-22', 'CYTB', 'High', 18, 103, 205),
(342, 2, '2025-07-05', '12S', 'High', 21, 104, 208),
(342, 3, '2025-07-18', 'ND2', 'Medium', 24, 105, 211),
(342, 4, '2025-07-31', 'COI', 'High', 27, 106, 202),
(343, 1, '2025-07-11', '12S', 'Low', 25, 104, 210),
(343, 2, '2025-07-24', 'ND2', 'Medium', 3, 105, 201),
(343, 3, '2025-08-06', 'COI', 'High', 6, 106, 204),
(343, 4, '2025-08-19', '16S', 'High', 9, 107, 207),
(344, 1, '2025-07-30', 'ND2', 'Medium', 7, 105, 203),
(344, 2, '2025-08-12', 'COI', 'High', 10, 106, 206),
(344, 3, '2025-08-25', '16S', 'High', 13, 107, 209),
(344, 4, '2025-09-07', 'CYTB', 'High', 16, 108, 212),
(345, 1, '2025-08-18', 'COI', 'High', 14, 106, 208),
(345, 2, '2025-08-31', '16S', 'High', 17, 107, 211),
(345, 3, '2025-09-13', 'CYTB', 'High', 20, 108, 202),
(345, 4, '2025-09-26', '12S', 'Medium', 23, 109, 205),
(346, 1, '2025-09-06', '16S', 'High', 21, 107, 201),
(346, 2, '2025-09-19', 'CYTB', 'Low', 24, 108, 204),
(346, 3, '2025-10-02', '12S', 'Medium', 27, 109, 207),
(346, 4, '2025-10-15', 'ND2', 'High', 5, 110, 210),
(347, 1, '2025-09-25', 'CYTB', 'High', 3, 108, 206),
(347, 2, '2025-10-08', '12S', 'Medium', 6, 109, 209),
(347, 3, '2025-10-21', 'ND2', 'High', 9, 110, 212),
(347, 4, '2025-11-03', 'COI', 'High', 12, 101, 203),
(348, 1, '2025-10-14', '12S', 'Medium', 10, 109, 211),
(348, 2, '2025-10-27', 'ND2', 'High', 13, 110, 202),
(348, 3, '2025-11-09', 'COI', 'High', 16, 101, 205),
(348, 4, '2025-11-22', '16S', 'High', 19, 102, 208),
(349, 1, '2025-11-02', 'ND2', NULL, 17, 110, 204),
(349, 2, '2025-11-15', 'COI', 'High', 20, 101, 207),
(349, 3, '2025-11-28', '16S', 'Low', 23, 102, 210),
(349, 4, '2025-12-11', 'CYTB', 'Medium', 26, 103, 201),
(350, 1, '2025-11-21', 'COI', 'High', 24, 101, 209),
(350, 2, '2025-12-04', '16S', 'High', 27, 102, 212),
(350, 3, '2025-12-17', 'CYTB', 'Medium', 5, 103, 203),
(350, 4, '2025-12-30', '12S', 'High', 8, 104, 206);

COMMIT;

-- Produce 12 authentic trigger-generated audit entries on synthetic species.

UPDATE Fish_Species SET conservation_status = 'Vulnerable' WHERE speciesID = 301;

UPDATE Fish_Species SET conservation_status = 'Endangered' WHERE speciesID = 301;

UPDATE Fish_Species SET conservation_status = 'Endangered' WHERE speciesID = 304;

UPDATE Fish_Species SET conservation_status = 'Critically Endangered' WHERE speciesID = 304;

UPDATE Fish_Species SET conservation_status = 'Vulnerable' WHERE speciesID = 309;

UPDATE Fish_Species SET conservation_status = 'Endangered' WHERE speciesID = 309;

UPDATE Fish_Species SET conservation_status = 'Vulnerable' WHERE speciesID = 305;

UPDATE Fish_Species SET conservation_status = 'Endangered' WHERE speciesID = 305;

UPDATE Fish_Species SET conservation_status = 'Endangered' WHERE speciesID = 313;

UPDATE Fish_Species SET conservation_status = 'Critically Endangered' WHERE speciesID = 313;

UPDATE Fish_Species SET conservation_status = 'Vulnerable' WHERE speciesID = 332;

UPDATE Fish_Species SET conservation_status = 'Endangered' WHERE speciesID = 332;


-- Quick acceptance evidence.
SELECT 'Researchers' AS metric, COUNT(*) AS total FROM Researcher
UNION ALL SELECT 'Species', COUNT(*) FROM Fish_Species
UNION ALL SELECT 'DNA samples', COUNT(*) FROM DNA_Sample
UNION ALL SELECT 'Regions', COUNT(*) FROM Ocean_Sampling_Region
UNION ALL SELECT 'Vessels', COUNT(*) FROM Research_Vehicle
UNION ALL SELECT 'Audit events', COUNT(*) FROM Species_Status_Audit;
