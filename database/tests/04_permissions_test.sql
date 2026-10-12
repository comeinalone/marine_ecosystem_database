-- Manual privilege tests after security/dcl.sql and manual password setup.
-- Actual: unverified. Use separate connections; never run as root for this test.
USE Marine_Ecosystem_Database;
SELECT CURRENT_USER(), CURRENT_ROLE();
SHOW GRANTS;
-- Both reader and manager: expected success.
SELECT * FROM EndangeredSpeciesView;
SELECT CountSpeciesSamples(301);
CALL GetSamplesByResearcher(1);

-- Run the following lines individually in an interactive session.
-- Reader: expected ERROR 1142 (UPDATE denied).
-- Manager: expected success; ROLLBACK restores data and any trigger audit row.
-- START TRANSACTION;
-- UPDATE Fish_Species SET conservation_status = 'Vulnerable' WHERE speciesID = 301;
-- ROLLBACK;

-- Reader: expected ERROR 1142 (INSERT denied after REVOKE).
-- Manager: expected success, even though the query deliberately inserts no rows.
-- INSERT INTO DNA_Sample SELECT * FROM DNA_Sample WHERE 1 = 0;

-- Both roles lack global CREATE USER and GRANT OPTION. Verify via SHOW GRANTS
-- as DBA (do not create accounts just to test that denial).
