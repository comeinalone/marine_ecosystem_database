
USE Marine_Ecosystem_Database;

-- Researchers
INSERT INTO Researcher VALUES
(1, 'Aarav', NULL, 'Shah', 'Marine Biology',
 'aarav@example.com', '1988-04-15'),
(2, 'Priya', NULL, 'Nair', 'Genetics',
 'priya@example.com', '1990-09-21'),
(3, 'Rohan', NULL, 'Mehta', 'Marine Ecology',
 'rohan@example.com', '1985-11-08'),
(4, 'Ananya', NULL, 'Rao', 'Molecular Genetics',
 'ananya@example.com', '1993-06-12');

-- Researcher contact details
INSERT INTO Researcher_Contact VALUES
(1, 'Mobile', '9876500001'),
(1, 'Work', '02240000001'),
(2, 'Mobile', '9876500002'),
(3, 'Mobile', '9876500003'),
(4, 'Mobile', '9876500004');

-- Marine biologists
INSERT INTO Marine_Biologist VALUES
(1, 8, 'Yes'),
(3, 12, 'Yes');

-- Geneticists
INSERT INTO Geneticist VALUES
(2, 'Marine Genomics Lab', 'DNA Sequencing'),
(4, 'Ocean Genetics Centre', 'Population Genetics');

-- Research vessels
INSERT INTO Research_Vehicle VALUES
(101, 'Ocean Explorer', 20, 'Mumbai'),
(102, 'Marine Discovery', 15, 'Kochi'),
(103, 'Blue Horizon', 25, 'Goa');

-- Sampling regions
INSERT INTO Ocean_Sampling_Region VALUES
(201, 'Arabian Sea Station A', 'Arabian Sea'),
(202, 'Bay of Bengal Station B', 'Bay of Bengal'),
(203, 'Lakshadweep Reef', 'Indian Ocean');

-- Fish species
INSERT INTO Fish_Species VALUES
(301, 'Whale Shark', 'Rhincodon typus', 'Endangered'),
(302, 'Great White Shark', 'Carcharodon carcharias', 'Vulnerable'),
(303, 'Yellowfin Tuna', 'Thunnus albacares', 'Least Concern'),
(304, 'Scalloped Hammerhead', 'Sphyrna lewini',
 'Critically Endangered'),
(305, 'Manta Ray', 'Mobula birostris', 'Endangered');

-- DNA samples
INSERT INTO DNA_Sample VALUES
(301, 1, '2026-01-12', 'COI', 'High', 1, 101, 201),
(301, 2, '2026-01-18', '16S', 'Medium', 2, 101, 201),
(302, 1, '2026-02-05', 'COI', 'High', 3, 102, 202),
(303, 1, '2026-02-15', 'CYTB', 'Low', 4, 102, 202),
(304, 1, '2026-03-02', 'COI', 'High', 1, 103, 203),
(305, 1, '2026-03-10', '16S', 'Medium', 2, 103, 203);

