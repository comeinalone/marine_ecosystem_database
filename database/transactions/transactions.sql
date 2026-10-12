USE Marine_Ecosystem_Database;
-- Run separately from initialization; DDL statements can implicitly commit.
-- COMMIT demonstrations use session-local tables, leaving baseline data intact.
CREATE TEMPORARY TABLE Transaction_Demo (
    id INT PRIMARY KEY, note VARCHAR(60) NOT NULL
) ENGINE=InnoDB;

START TRANSACTION;
INSERT INTO Transaction_Demo VALUES (1, 'Committed observation');
COMMIT;
SELECT COUNT(*) = 1 AS commit_pass FROM Transaction_Demo;

START TRANSACTION;
INSERT INTO Transaction_Demo VALUES (2, 'Rolled back observation');
ROLLBACK;
SELECT COUNT(*) = 1 AS rollback_pass FROM Transaction_Demo;

START TRANSACTION;
INSERT INTO Transaction_Demo VALUES (3, 'Retained before savepoint');
SAVEPOINT observation_saved;
INSERT INTO Transaction_Demo VALUES (4, 'Discarded after savepoint');
ROLLBACK TO SAVEPOINT observation_saved;
RELEASE SAVEPOINT observation_saved;
COMMIT;
SELECT COUNT(*) = 2 AS savepoint_pass FROM Transaction_Demo;
DROP TEMPORARY TABLE Transaction_Demo;

-- Atomic multi-table operation: researcher + contact + geneticist.
-- Reserved demonstration ID: 900010. Use a disposable database if it is occupied.
-- A handler rolls back all steps on error. The successful demonstration also
-- rolls back deliberately so no demonstration records persist.
DELIMITER $$
DROP PROCEDURE IF EXISTS DemoAtomicResearcher$$
CREATE PROCEDURE DemoAtomicResearcher()
SQL SECURITY INVOKER
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;
    INSERT INTO Researcher (
        researcherID, first_name, last_name, researcher_specialization,
        email_address, date_of_birth
    ) VALUES (900010, 'Demo', 'Scientist', 'Genetics',
              'transaction-demo@example.invalid', '2000-01-01');
    INSERT INTO Researcher_Contact VALUES (900010, 'Work', '0000000000');
    INSERT INTO Geneticist VALUES (900010, 'Demonstration Lab', 'Population Genetics');
    SELECT (SELECT COUNT(*) FROM Researcher WHERE researcherID = 900010) = 1
       AND (SELECT COUNT(*) FROM Researcher_Contact WHERE researcherID = 900010) = 1
       AND (SELECT COUNT(*) FROM Geneticist WHERE researcherID = 900010) = 1 AS atomic_insert_pass;
    ROLLBACK;
    SELECT (SELECT COUNT(*) FROM Researcher WHERE researcherID = 900010) = 0
       AND (SELECT COUNT(*) FROM Researcher_Contact WHERE researcherID = 900010) = 0
       AND (SELECT COUNT(*) FROM Geneticist WHERE researcherID = 900010) = 0 AS atomic_rollback_pass;
END$$
DELIMITER ;
CALL DemoAtomicResearcher();
DROP PROCEDURE DemoAtomicResearcher;
