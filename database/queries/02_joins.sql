USE Marine_Ecosystem_Database;
-- INNER JOIN: six samples with researcher and species descriptions.
SELECT d.speciesID, d.sampleID, f.common_name, r.first_name, r.last_name
FROM DNA_Sample d
INNER JOIN Fish_Species f ON f.speciesID = d.speciesID
INNER JOIN Researcher r ON r.researcherID = d.researcherID
ORDER BY d.speciesID, d.sampleID;

-- LEFT JOIN retains species without samples. Expected counts: 2,1,1,1,1.
SELECT f.speciesID, f.common_name, COUNT(d.sampleID) AS samples
FROM Fish_Species f LEFT JOIN DNA_Sample d ON d.speciesID = f.speciesID
GROUP BY f.speciesID, f.common_name ORDER BY f.speciesID;

-- RIGHT JOIN retains all vessels. Expected counts: 2,2,2.
SELECT v.vesselID, v.vessel_name, COUNT(d.sampleID) AS samples
FROM DNA_Sample d RIGHT JOIN Research_Vehicle v ON v.vesselID = d.vesselID
GROUP BY v.vesselID, v.vessel_name ORDER BY v.vesselID;

-- SELF JOIN pairs different samples collected by the same researcher.
-- Lexicographic composite-key comparison prevents self-pairs and duplicates.
-- Expected two pairs: (301,1)-(304,1), (301,2)-(305,1).
SELECT a.researcherID, a.speciesID AS species_a, a.sampleID AS sample_a,
       b.speciesID AS species_b, b.sampleID AS sample_b
FROM DNA_Sample a JOIN DNA_Sample b ON a.researcherID = b.researcherID
AND (a.speciesID < b.speciesID OR (a.speciesID = b.speciesID AND a.sampleID < b.sampleID))
ORDER BY a.researcherID, a.speciesID, a.sampleID;
