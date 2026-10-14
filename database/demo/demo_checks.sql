USE Marine_Ecosystem_Demo;
SELECT 'Researchers' metric, COUNT(*) total FROM Researcher
UNION ALL SELECT 'Species', COUNT(*) FROM Fish_Species
UNION ALL SELECT 'DNA samples', COUNT(*) FROM DNA_Sample
UNION ALL SELECT 'Conservation audit', COUNT(*) FROM Species_Status_Audit;

-- Exact name, including duplicates.
SELECT researcherID, CONCAT_WS(' ',first_name,middle_name,last_name) full_name,
       researcher_specialization,email_address
FROM Researcher WHERE LOWER(CONCAT_WS(' ',first_name,middle_name,last_name))='sana khan';

-- Traceability for a known composite key.
SELECT d.speciesID,d.sampleID,f.common_name,
       CONCAT_WS(' ',r.first_name,r.last_name) researcher,
       v.vessel_name,l.location_name,d.DNA_quality
FROM DNA_Sample d JOIN Fish_Species f ON f.speciesID=d.speciesID
JOIN Researcher r ON r.researcherID=d.researcherID
JOIN Research_Vehicle v ON v.vesselID=d.vesselID
JOIN Ocean_Sampling_Region l ON l.locationID=d.locationID
WHERE d.speciesID=301 AND d.sampleID=1;

-- HAVING demonstration on region 201; should have several results.
SELECT f.common_name,COUNT(*) sample_count FROM DNA_Sample d
JOIN Fish_Species f ON d.speciesID=f.speciesID
WHERE d.locationID=201 GROUP BY f.speciesID,f.common_name
HAVING COUNT(*) >= 3 ORDER BY sample_count DESC;

-- Sample warnings.
SELECT DNA_quality,COUNT(*) total FROM DNA_Sample GROUP BY DNA_quality;
SELECT COUNT(*) AS future_dated_samples FROM DNA_Sample WHERE collection_date>CURRENT_DATE();

-- Trigger-generated changes.
SELECT a.speciesID,f.common_name,a.old_status,a.new_status,a.changed_at
FROM Species_Status_Audit a JOIN Fish_Species f ON f.speciesID=a.speciesID
ORDER BY a.auditID;
