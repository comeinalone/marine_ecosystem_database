-- Optional DBA-only script. Run outside any application transaction.
-- No real passwords: new accounts are LOCKED until manually configured.
-- Existing accounts are not modified by CREATE USER IF NOT EXISTS.
-- Roles contain database-scoped permissions only, without GRANT OPTION.
CREATE ROLE IF NOT EXISTS 'marine_readonly', 'marine_manager';
CREATE USER IF NOT EXISTS 'marine_reader'@'localhost' ACCOUNT LOCK;
CREATE USER IF NOT EXISTS 'marine_app'@'localhost' ACCOUNT LOCK;

GRANT SELECT ON Marine_Ecosystem_Database.* TO 'marine_readonly';
GRANT EXECUTE ON PROCEDURE Marine_Ecosystem_Database.GetSamplesByResearcher TO 'marine_readonly';
GRANT EXECUTE ON FUNCTION Marine_Ecosystem_Database.CountSpeciesSamples TO 'marine_readonly';
GRANT EXECUTE ON FUNCTION Marine_Ecosystem_Database.CountResearcherSamples TO 'marine_readonly';
-- REVOKE demonstration: temporarily grant and then remove INSERT from the role.
-- New users are locked; run only in a controlled lab, with no active role users.
GRANT INSERT ON Marine_Ecosystem_Database.DNA_Sample TO 'marine_readonly';
REVOKE INSERT ON Marine_Ecosystem_Database.DNA_Sample FROM 'marine_readonly';

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, CREATE, ALTER, INDEX,
      CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, TRIGGER,
      CREATE TEMPORARY TABLES
ON Marine_Ecosystem_Database.* TO 'marine_manager';
GRANT 'marine_readonly' TO 'marine_reader'@'localhost';
GRANT 'marine_manager' TO 'marine_app'@'localhost';
SET DEFAULT ROLE 'marine_readonly' TO 'marine_reader'@'localhost';
SET DEFAULT ROLE 'marine_manager' TO 'marine_app'@'localhost';
SHOW GRANTS FOR 'marine_readonly';
SHOW GRANTS FOR 'marine_manager';
SHOW GRANTS FOR 'marine_reader'@'localhost' USING 'marine_readonly';
SHOW GRANTS FOR 'marine_app'@'localhost' USING 'marine_manager';

-- Manual setup, entered interactively by the DBA (never save actual passwords):
-- ALTER USER 'marine_app'@'localhost' IDENTIFIED BY '<choose-unique-password>' ACCOUNT UNLOCK;
-- ALTER USER 'marine_reader'@'localhost' IDENTIFIED BY '<different-password>' ACCOUNT UNLOCK;
-- The manager is suitable for this DBMS lab; production applications should use
-- a narrower runtime-only role without schema-management permissions.
