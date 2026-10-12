-- mysql client entry point, executed from the repository root.
-- FRESH DATABASE ONLY: does not drop, overwrite or migrate an existing database.
-- Use mysql --abort-source-on-error -u root -p < database/initialize.sql
SOURCE database/schema/01_create_tables.sql;
SOURCE database/data/02_insert_data.sql;
SOURCE database/advanced/01_views.sql;
SOURCE database/advanced/02_procedures.sql;
SOURCE database/advanced/03_functions.sql;
SOURCE database/advanced/04_triggers.sql;
-- Security, demonstrations and tests are deliberately excluded.
