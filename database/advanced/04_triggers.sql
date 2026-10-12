USE Marine_Ecosystem_Database;
-- AFTER UPDATE ensures rejected updates do not create audit entries.
-- The audit insert is part of the same transaction as the species update.
DELIMITER $$
DROP TRIGGER IF EXISTS AuditSpeciesStatusChange$$
CREATE TRIGGER AuditSpeciesStatusChange
AFTER UPDATE ON Fish_Species
FOR EACH ROW
BEGIN
    IF NOT (OLD.conservation_status <=> NEW.conservation_status) THEN
        INSERT INTO Species_Status_Audit (speciesID, old_status, new_status, changed_at)
        VALUES (NEW.speciesID, OLD.conservation_status, NEW.conservation_status, CURRENT_TIMESTAMP);
    END IF;
END$$
DELIMITER ;
