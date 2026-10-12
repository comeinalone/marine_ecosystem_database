# Marine Ecosystem Management System — report draft

## Abstract

This mini-project models marine research using a nine-table MySQL relational database and a Java Swing desktop interface. It connects researchers and their specializations with fish species, DNA samples, vessels and ocean sampling locations. The implementation demonstrates normalization, integrity constraints, joins, subqueries, aggregates, views, stored routines, auditing, transactions and access privileges. Java compilation and offline tests are verified; live MySQL evaluation and screenshots remain pending.

## Objectives and scope

Maintain structured research records, prevent orphaned samples, report research activity and track conservation-status changes. The application supports CRUD for all eight operational entities and read-only status history. The project uses Java 17, Maven, JDBC/Connector-J and standard Swing components; it does not include a web service or external UI framework.

## Database design

See the [ER diagram](../diagrams/erd.md) and [schema](../../database/schema/01_create_tables.sql). `Researcher` has contact rows and optional `Marine_Biologist`/`Geneticist` subtype rows. Each `DNA_Sample` references one researcher, vessel, location and species. `(speciesID, sampleID)` is the sample key because sample numbering is local to a species. `Species_Status_Audit` stores status transitions with an AUTO_INCREMENT key and timestamp.

Candidate keys include researcher email and species scientific name. Required attributes use NOT NULL, enumerated values and ranges use CHECK, and all related IDs use foreign keys. Explicit sample FK indexes support joins. No cascading deletion is defined; referenced parent records and species with audit history cannot be deleted accidentally.

## Normalization

The [normalization analysis](../../database/normalization/normalization.md) documents candidate keys and intended functional dependencies. Attributes are scalar (1NF); sample attributes depend on the entire species-local composite key (2NF); entity descriptions are stored with their own determinants (3NF). Multiple phone numbers are separate contact rows. The 3NF claim remains conditional on the documented domain dependencies, rather than being inferred only from example data.

## SQL implementation

| Concept | Implementation and purpose |
| --- | --- |
| DDL/DML/DQL | Schema definitions; isolated temporary-table insert/update/delete; entity retrieval |
| Joins | INNER descriptive sample report; LEFT species counts; RIGHT vessel counts; SELF researcher sample pairs |
| Subqueries | Nested endangered-species researcher lookup; correlated counts and NOT EXISTS |
| Aggregation | COUNT, MIN, MAX, SUM, AVG, GROUP BY, HAVING; ranked researcher activity |
| Views | EndangeredSpeciesView, SpeciesSampleSummary, ResearcherSampleReport, SamplingLocationReport |
| Procedures | Validated sample insertion, status update and researcher sample retrieval |
| Functions | Species and researcher sample counts; unknown/NULL IDs return zero |
| Trigger | AFTER UPDATE audit using `NOT (OLD.status <=> NEW.status)` on the actual conservation_status column |
| Transactions | COMMIT, ROLLBACK, SAVEPOINT and atomic researcher/contact/geneticist insertion |
| DCL | Locked lab accounts, read-only and manager roles, GRANT, REVOKE, SHOW GRANTS |

Production objects are separate from demonstrations and tests. Procedures do not commit caller transactions. The audit trigger is the single automatic status-history writer, including changes made directly through JDBC. Rollback undoes species changes and audit rows together.

Example expected seed results (not observed execution results): species 301 has two samples, researchers 1 and 2 each have two samples, and each location has two samples from two species. EndangeredSpeciesView contains species 301, 304 and 305. Query scripts include their expected results beside the SQL.

## Java architecture and interface

`model` holds explicit schema metadata and detached entity/result models. `db` reads environment variables or an ignored local properties file and opens connections. `dao` builds SQL from whitelisted metadata, binds user values and closes JDBC resources. `ui` provides a sidebar frame, dashboard, reusable CRUD forms, researcher tabs and reports. `Main` supports desktop launch and configuration/connection diagnostics.

SwingWorker keeps JDBC work off the event-dispatch thread. JTable/TableRowSorter provide sorting and literal search filtering. Forms use JTextField and JComboBox for required values and references. Entity keys remain fixed during updates, except contacts whose tuple can be edited using the original three-column predicate. Sample updates/deletes always include both key columns. Deletion requires confirmation; SQL failures are translated into clear messages without removing dependent data automatically.

The shared form/DAO design makes schema mappings visible in one place and avoids repetitive classes. Application writes use parameterized CRUD; stored routines remain independently callable and are exercised by SQL and opt-in JDBC tests.

## Testing and evidence

See [implementation status](../implementation-status.md), [SQL test matrix](../../database/tests/README.md) and [desktop acceptance checks](../testing.md). Offline Java tests cover validation, configuration precedence, composite-key operations, parameter binding, resource cleanup and panel construction. MySQL tests cover constraints, views, routines, NULL-safe auditing, rollback, savepoints and permissions.

**Current actual evidence:** `mvn clean compile` and `mvn -B clean verify` succeeded on OpenJDK 21, targeting Java 17. Twelve offline tests passed; one opt-in live JDBC test was skipped. The executable JAR's configuration/driver diagnostic succeeded without attempting a connection. Detailed verification is recorded in the implementation-status document. No MySQL execution or connected desktop success is claimed.

**Evidence still required:** server/version and SQL-mode output, actual SQL-test output, opt-in JDBC result, reader write-denial evidence, dashboard and editor/report screenshots. Add these only after running the application. Screenshot targets are listed in `docs/testing.md`; there are no fabricated images or results in this draft.

## Limitations and future work

Live integration remains pending. The application uses manual IDs, loads tables into memory, has no pagination or optimistic locking, and relies on database privileges rather than a separate login interface. Failed saves require reopening the form. Future improvements could add pagination, edit-conflict detection, narrower runtime roles and report export after completing the documented integration checks.

## Conclusion

The repository contains the SQL implementation, Java desktop application, Maven build and reproducible test instructions. Offline build verification demonstrates compile readiness. The final submission must attach measured MySQL results and screenshots before claiming end-to-end correctness.
