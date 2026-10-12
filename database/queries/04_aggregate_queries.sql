USE Marine_Ecosystem_Database;
-- Expected total=6, distinct species=5, earliest=2026-01-12, latest=2026-03-10.
SELECT COUNT(*) AS total_samples, COUNT(DISTINCT speciesID) AS sampled_species,
       MIN(collection_date) AS earliest, MAX(collection_date) AS latest
FROM DNA_Sample;
-- Expected sum=60, average=20, minimum=15, maximum=25.
SELECT SUM(vessel_capacity) AS total_capacity, AVG(vessel_capacity) AS average_capacity,
       MIN(vessel_capacity) AS minimum_capacity, MAX(vessel_capacity) AS maximum_capacity
FROM Research_Vehicle;
-- GROUP BY + HAVING: expected researchers 1 and 2, count 2 each.
SELECT researcherID, COUNT(*) AS samples FROM DNA_Sample
GROUP BY researcherID HAVING COUNT(*) >= 2 ORDER BY researcherID;
-- Expected High=3, Medium=2, Low=1.
SELECT DNA_quality, COUNT(*) AS samples FROM DNA_Sample
GROUP BY DNA_quality ORDER BY samples DESC;
-- Advanced reporting over a view: species 301 exceeds average samples (1.2).
SELECT speciesID, common_name, total_samples FROM SpeciesSampleSummary
WHERE total_samples > (SELECT AVG(total_samples) FROM SpeciesSampleSummary);
-- MySQL 8 window function: equal sample totals share a rank.
SELECT researcherID, first_name, total_samples_collected,
       DENSE_RANK() OVER (ORDER BY total_samples_collected DESC) AS activity_rank
FROM ResearcherSampleReport ORDER BY activity_rank, researcherID;
