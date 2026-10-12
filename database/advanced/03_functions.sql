-- Install after the schema. These functions read current data, so they are
-- NOT DETERMINISTIC. Missing IDs (including NULL) return zero.
-- With binary logging, the DBA may need routine-creation privileges or a
-- deliberate log_bin_trust_function_creators configuration; see README.
USE Marine_Ecosystem_Database;
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
