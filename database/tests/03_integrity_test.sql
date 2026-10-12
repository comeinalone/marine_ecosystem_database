-- Automated assertions, not part of initialization. MySQL 8.0.16+ required.
-- Run as the test DBA on a disposable initialized database, without other writers.
-- Reserved fixture IDs 910001/910002 must be unused. All fixture DML rolls back.
-- Definitions are created BEFORE the transaction because routine DDL commits.
USE Marine_Ecosystem_Database;
DELIMITER $$
DROP PROCEDURE IF EXISTS TestAssert$$
CREATE PROCEDURE TestAssert(IN passed BOOLEAN, IN description VARCHAR(128))
BEGIN
    IF NOT COALESCE(passed, FALSE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = description;
    END IF;
END$$
DROP PROCEDURE IF EXISTS TestExpectedError$$
CREATE PROCEDURE TestExpectedError(IN statement_text TEXT, IN expected_errno INT)
BEGIN
    DECLARE actual_errno INT DEFAULT 0;
    SET @test_statement = statement_text;
    PREPARE test_stmt FROM @test_statement;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
            GET DIAGNOSTICS CONDITION 1 actual_errno = MYSQL_ERRNO;
        EXECUTE test_stmt;
    END;
    DEALLOCATE PREPARE test_stmt;
    CALL TestAssert(actual_errno = expected_errno, 'Expected database error was not raised');
END$$
DROP PROCEDURE IF EXISTS RunIntegrityTests$$
CREATE PROCEDURE RunIntegrityTests()
BEGIN
    DECLARE original_audit_count BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    SELECT COUNT(*) INTO original_audit_count FROM Species_Status_Audit;
    START TRANSACTION;
    INSERT INTO Fish_Species VALUES (910001, 'Test Species', 'Testus primus', NULL),
                                   (910002, 'Other Species', 'Testus secundus', 'Least Concern');
    INSERT INTO Researcher VALUES (910001, 'Test', NULL, 'Scientist', 'Genetics',
                                  'integrity-test@example.invalid', '2000-01-01');
    INSERT INTO Research_Vehicle VALUES (910001, 'Test Vessel', 2, 'Test Port');
    INSERT INTO Ocean_Sampling_Region VALUES (910001, 'Test Station', 'Test Ocean');

    CALL TestAssert(CountSpeciesSamples(910001) = 0, 'Empty species function');
    CALL TestAssert(CountResearcherSamples(910001) = 0, 'Empty researcher function');
    CALL TestAssert(CountSpeciesSamples(NULL) = 0, 'NULL species function');
    CALL TestAssert(CountResearcherSamples(-2147483648) = 0, 'Missing researcher function');
    CALL TestAssert((SELECT total_samples FROM SpeciesSampleSummary WHERE speciesID = 910001) = 0,
                    'LEFT JOIN must retain species without samples');
    CALL TestAssert((SELECT total_samples_collected FROM ResearcherSampleReport WHERE researcherID = 910001) = 0,
                    'LEFT JOIN must retain researchers without samples');
    CALL TestAssert((SELECT total_samples_collected FROM SamplingLocationReport WHERE locationID = 910001) = 0,
                    'LEFT JOIN must retain empty locations');

    CALL AddDNASample(910001, 1, '2026-01-01', 'COI', 'High', 910001, 910001, 910001);
    CALL AddDNASample(910002, 1, '2026-01-02', '16S', NULL, 910001, 910001, 910001);
    CALL TestAssert(CountSpeciesSamples(910001) = 1, 'Species function after insert');
    CALL TestAssert(CountResearcherSamples(910001) = 2, 'Researcher function after insert');
    CALL GetSamplesByResearcher(910001); -- Expected two rows, species 910001 and 910002.

    -- PRIMARY KEY, FOREIGN KEY, UNIQUE, NOT NULL and CHECK (MySQL error numbers).
    CALL TestExpectedError('INSERT INTO DNA_Sample VALUES (910001,1,\'2026-01-01\',\'COI\',NULL,910001,910001,910001)', 1062);
    CALL TestExpectedError('INSERT INTO DNA_Sample VALUES (-2147483648,2,\'2026-01-01\',\'COI\',NULL,910001,910001,910001)', 1452);
    CALL TestExpectedError('INSERT INTO Fish_Species VALUES (910003,\'Duplicate name\',\'Testus primus\',NULL)', 1062);
    CALL TestExpectedError('UPDATE Researcher SET first_name=NULL WHERE researcherID=910001', 1048);
    CALL TestExpectedError('UPDATE Fish_Species SET conservation_status=\'Invalid\' WHERE speciesID=910001', 3819);
    CALL TestExpectedError('UPDATE Research_Vehicle SET vessel_capacity=0 WHERE vesselID=910001', 3819);
    CALL TestExpectedError('UPDATE DNA_Sample SET DNA_quality=\'Invalid\' WHERE speciesID=910001 AND sampleID=1', 3819);
    CALL TestExpectedError('UPDATE DNA_Sample SET collection_date=\'1899-01-01\' WHERE speciesID=910001 AND sampleID=1', 3819);
    CALL TestExpectedError('UPDATE Researcher SET date_of_birth=\'1899-01-01\' WHERE researcherID=910001', 3819);
    CALL TestExpectedError('INSERT INTO Researcher_Contact VALUES (910001,\'Invalid\',\'123\')', 3819);
    CALL TestExpectedError('INSERT INTO Marine_Biologist VALUES (910001,-1,\'Yes\')', 3819);
    CALL TestExpectedError('INSERT INTO Marine_Biologist VALUES (910001,1,\'Maybe\')', 3819);
    CALL TestExpectedError('DELETE FROM Fish_Species WHERE speciesID=910001', 1451);

    CALL UpdateSpeciesStatus(910001, 'Endangered'); -- NULL -> value
    CALL UpdateSpeciesStatus(910001, 'Endangered'); -- identical: no audit
    CALL UpdateSpeciesStatus(910001, NULL);         -- value -> NULL
    CALL UpdateSpeciesStatus(910001, NULL);         -- NULL -> NULL: no audit
    CALL TestAssert((SELECT COUNT(*) FROM Species_Status_Audit WHERE speciesID = 910001) = 2,
                    'Audit must record exactly two actual changes');
    CALL TestAssert((SELECT COUNT(*) FROM Species_Status_Audit WHERE speciesID = 910001
                    AND old_status IS NULL AND new_status = 'Endangered' AND changed_at IS NOT NULL) = 1,
                    'Audit must store old/new values and timestamp');
    CALL TestAssert((SELECT COUNT(*) FROM Species_Status_Audit WHERE speciesID = 910001
                    AND old_status = 'Endangered' AND new_status IS NULL) = 1,
                    'Audit must detect transition to NULL');
    CALL TestExpectedError('CALL AddDNASample(910001,1,\'2026-01-01\',\'COI\',NULL,910001,910001,910001)', 1644);
    CALL TestExpectedError('CALL UpdateSpeciesStatus(910001,\'Invalid\')', 1644);
    CALL TestExpectedError('CALL GetSamplesByResearcher(NULL)', 1644);

    SAVEPOINT before_third_sample;
    CALL AddDNASample(910001, 3, '2026-01-03', 'COI', 'Low', 910001, 910001, 910001);
    ROLLBACK TO SAVEPOINT before_third_sample;
    CALL TestAssert(CountSpeciesSamples(910001) = 1, 'Savepoint must undo third sample');
    ROLLBACK;
    CALL TestAssert((SELECT COUNT(*) FROM Researcher WHERE researcherID = 910001) = 0, 'Researcher rollback');
    CALL TestAssert((SELECT COUNT(*) FROM DNA_Sample WHERE speciesID IN (910001,910002)) = 0, 'Sample rollback');
    CALL TestAssert((SELECT COUNT(*) FROM Species_Status_Audit) = original_audit_count, 'Audit rollback');
    SELECT 'PASS: integrity, routines, views, audit and rollback assertions' AS result;
END$$
DELIMITER ;
CALL RunIntegrityTests();
DROP PROCEDURE RunIntegrityTests;
DROP PROCEDURE TestExpectedError;
DROP PROCEDURE TestAssert;
