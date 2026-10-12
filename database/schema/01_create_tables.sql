-- Marine Ecosystem Management System
-- Complete schema for MySQL 8.0.16+
-- Creates a fresh database; does not drop an existing database.

CREATE DATABASE Marine_Ecosystem_Database;
USE Marine_Ecosystem_Database;

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
