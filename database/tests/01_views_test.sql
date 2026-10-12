-- Seed-data checks: run only against the unchanged original seed dataset.
-- Expected: every result is 1. Actual: unverified until run against MySQL.
USE Marine_Ecosystem_Database;
SELECT COUNT(*) = 3 AND SUM(speciesID IN (301,304,305)) = 3 AS endangered_pass
FROM EndangeredSpeciesView;
SELECT COUNT(*) = 5 AND SUM(total_samples) = 6
       AND SUM(speciesID = 301 AND total_samples = 2) = 1 AS species_summary_pass
FROM SpeciesSampleSummary;
SELECT COUNT(*) = 4 AND SUM(total_samples_collected) = 6
       AND SUM(researcherID IN (1,2) AND total_samples_collected = 2) = 2 AS researcher_report_pass
FROM ResearcherSampleReport;
SELECT COUNT(*) = 3 AND MIN(total_samples_collected) = 2 AND MAX(total_samples_collected) = 2
       AND MIN(species_sampled) = 2 AND MAX(species_sampled) = 2 AS location_report_pass
FROM SamplingLocationReport;
