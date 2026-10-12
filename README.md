# Marine Ecosystem Management System

A second-year DBMS mini-project for managing marine research with MySQL, Java JDBC and a Swing desktop application. The existing nine-table relational design connects researchers, contacts, biologists, geneticists, vessels, sampling regions, fish species, DNA samples and conservation-status history.

**Verification:** Java compilation, offline tests and packaging are verified. MySQL Server is not installed in the development environment; SQL execution, live JDBC behavior and connected desktop workflows remain unverified. See [implementation status](docs/implementation-status.md).

## Features

- Sidebar dashboard: researcher, species, sample and endangered-species counts.
- Add, edit, delete, sort and search fish species, researchers, vessels and sampling locations.
- Researcher tabs for contact records, marine biologists and geneticists.
- DNA sample CRUD with labeled species/researcher/vessel/location selectors and the composite `(speciesID, sampleID)` key.
- Four view-based reports plus read-only conservation-status audit history.
- Required-field, length, integer, date, email, status and quality validation; deletion confirmation and database-error explanations.
- Background `SwingWorker` database operations, JDBC resource cleanup and parameterized SQL.
- Stored procedures, reusable counting functions, automatic audit trigger, transaction demonstrations, DCL roles and SQL tests.

## Requirements and technologies

- Java JDK 17+ (build verified with OpenJDK 21, targeting Java 17).
- Maven 3.9+ and network access for the first dependency download.
- Oracle MySQL Server and client 8.0.16+; InnoDB and strict SQL mode. MySQL 8.4 is a suitable server version. MariaDB compatibility is not claimed.
- MySQL Connector/J 8.4.0, supplied by Maven.
- A graphical desktop session for Swing. No third-party UI framework is required.
- JUnit 5 for offline tests and the opt-in integration test.

## Database installation

Install Oracle MySQL Server and the `mysql` client before integration testing. On CachyOS/Arch, check that the selected package supplies Oracle MySQL. Follow the [official Linux installation guide](https://dev.mysql.com/doc/refman/8.4/en/linux-installation.html); the [generic binary installation guide](https://dev.mysql.com/doc/refman/8.4/en/binary-installation.html) documents an upstream option when a suitable distribution package is unavailable. Initialize a **new** data directory, start the server using the installation's service instructions, and secure the administrator account. Do not initialize an existing server data directory.

Confirm the running server (client version alone is insufficient):

```bash
mysql --version
mysql -u root -p -e "SELECT VERSION(), @@default_storage_engine, @@sql_mode;"
```

Use your administrator account instead of `root` where appropriate. CHECK constraints require MySQL 8.0.16 or later; InnoDB is required for transactional and foreign-key behavior. Strict mode prevents silently truncated invalid input.

## Initialization and SQL execution order

Run commands from the repository root. `01_create_tables.sql` is the schema source of truth. It creates `Marine_Ecosystem_Database` and does **not** drop an existing database. Neither the schema nor seed script is a migration or safe to rerun on existing data.

For a fresh database only:

```bash
mysql --abort-source-on-error -u root -p < database/initialize.sql
```

The entry script uses mysql-client `SOURCE` commands in this order:

| Order | Script | Effect |
| --- | --- | --- |
| 1 | `database/schema/01_create_tables.sql` | Creates database and nine tables |
| 2 | `database/data/02_insert_data.sql` | Inserts baseline records once |
| 3 | `database/advanced/01_views.sql` | Creates/replaces four reporting views |
| 4 | `database/advanced/02_procedures.sql` | Creates/replaces three procedures |
| 5 | `database/advanced/03_functions.sql` | Creates/replaces two counting functions |
| 6 | `database/advanced/04_triggers.sql` | Creates/replaces status audit trigger |

For an **already initialized database**, review its schema and load only the needed advanced-object scripts individually. These replace their named objects without rewriting table data. Run object DDL outside transactions: MySQL DDL may implicitly commit.

```bash
mysql -u root -p < database/advanced/03_functions.sql
mysql -u root -p < database/advanced/04_triggers.sql
```

Function creation on a server with binary logging may require additional DBA privileges or a deliberate `log_bin_trust_function_creators` setting. The count functions correctly declare `NOT DETERMINISTIC` and `READS SQL DATA`; do not mislabel them to bypass a configuration error. See [MySQL stored-program binary logging](https://dev.mysql.com/doc/refman/8.4/en/stored-programs-logging.html).

The remaining scripts are optional and separate from initialization:

- `database/queries/01_basic_queries.sql` through `04_aggregate_queries.sql`: commented DDL/DML/DQL, joins, subqueries, aggregates, views and window functions. DDL/DML demonstrations use a temporary table.
- `database/transactions/transactions.sql`: COMMIT, ROLLBACK, SAVEPOINT and an atomic three-table researcher insertion. Committed examples use session-local tables; persistent-table fixtures roll back.
- `database/security/dcl.sql`: DBA-only role and locked-account setup, followed by GRANT, REVOKE and SHOW GRANTS.
- `database/tests/`: independent test scripts and [expected-result guide](database/tests/README.md).

## Database accounts

After initialization, run the optional DCL setup as administrator:

```bash
mysql -u root -p < database/security/dcl.sql
mysql -u root -p
```

In the interactive session, choose unique passwords and unlock the accounts. The following strings are placeholders; replace them locally and never save actual values in repository files:

```sql
ALTER USER 'marine_app'@'localhost' IDENTIFIED BY '<choose-unique-password>' ACCOUNT UNLOCK;
ALTER USER 'marine_reader'@'localhost' IDENTIFIED BY '<different-password>' ACCOUNT UNLOCK;
SHOW GRANTS FOR 'marine_app'@'localhost' USING 'marine_manager';
```

`marine_readonly` can read tables/views and execute the reporting procedure/count functions. `marine_manager` can manage records and database objects within this database; it cannot create users or grant privileges to others. The desktop is intended to run as `marine_app`. A read-only user can open reports, but attempted writes will be rejected by MySQL. There is no separate application login or role-based button hiding.

Accounts target a locally installed server. For remote/container deployments, a DBA must provision accounts with an appropriate client host and configure transport security. Existing account grants are not reset by the example script.

## Connection configuration

Use environment variables or copy the ignored configuration file:

```bash
mkdir -p config
cp config/database.properties.example config/database.properties
chmod 600 config/database.properties
```

Edit the copy locally. Relative paths resolve from the directory where Java starts, so launch from the repository root when using this file.

| Environment variable | Local property | Default |
| --- | --- | --- |
| `MARINE_DB_URL` | `db.url` | `jdbc:mysql://localhost:3306/Marine_Ecosystem_Database?connectTimeout=5000&socketTimeout=15000` |
| `MARINE_DB_USER` | `db.user` | `marine_app` |
| `MARINE_DB_PASSWORD` | `db.password` | Empty; set before connecting |

Environment values override file values. Passwords are passed separately to JDBC, not embedded in the URL. For a temporary shell session:

```bash
export MARINE_DB_USER=marine_app
read -rsp 'Database password: ' MARINE_DB_PASSWORD
export MARINE_DB_PASSWORD
```

The program does not automatically load `.env`. The default URL uses finite connection/read timeouts; retain suitable timeouts if overriding it. No insecure authentication or TLS overrides are enabled by the application.

## Build and launch

Compilation and offline tests do not need MySQL:

```bash
mvn clean compile
mvn verify
java -jar target/marine-ecosystem.jar --check-config
```

`mvn verify` runs offline tests, skips the opt-in live test by default, and builds an executable JAR containing Connector/J. `--check-config` validates configuration and driver availability without connecting.

After MySQL is initialized and credentials are configured:

```bash
java -jar target/marine-ecosystem.jar --check-db
java -jar target/marine-ecosystem.jar
```

`--check-db` opens a real connection and prints the database server version on success. The normal launch needs a desktop display. A missing server produces a connection message; the application does not create a database or populate fake results.

Create parent records before dependent records. For a researcher, first add the base researcher, then contacts/biologist/geneticist records in the related tabs. For a sample, create its species, researcher, vessel and location first. IDs are entered manually because the existing keys are not AUTO_INCREMENT. Editing preserves entity keys; edit contact records by changing their full three-column tuple. Search filters all loaded columns, and clicking a column header sorts the table.

## Testing and integration checklist

1. Initialize a fresh disposable MySQL database and load all six scripts in order.
2. Run `database/tests/01_views_test.sql`, `02_procedures_test.sql` and `03_integrity_test.sql` as described in the [test guide](database/tests/README.md).
3. Run the transaction demonstration separately; expected assertions are all 1.
4. Configure roles/passwords and verify reader/manager privileges in separate sessions.
5. Configure JDBC, then run `java -jar target/marine-ecosystem.jar --check-db`.
6. Run `MARINE_RUN_DB_TESTS=true mvn test -Dtest=JdbcIntegrationTest` on the disposable database. It exercises real DAO CRUD, routines, reports, audit and rollback.
7. Follow the [desktop acceptance checklist](docs/testing.md), record actual results, and capture screenshots for the [report](docs/report/mini-project-report.md).

Original seed expectations: 4 researchers, 5 species, 6 DNA samples and 3 endangered/critically endangered species. These are expected values derived from seed SQL, not observed MySQL results.

## Repository structure

```text
pom.xml                         Java 17 build, Connector/J, JUnit and executable JAR
config/database.properties.example
database/
  initialize.sql                Fresh-database entry script
  schema/                       Original nine-table schema
  data/                         Original baseline records
  normalization/                1NF, 2NF, 3NF analysis
  advanced/                     Views, procedures, functions, trigger
  queries/                      DDL/DML/DQL and reporting examples
  transactions/                 Isolated transaction demonstrations
  security/                     Optional DBA account/role demonstrations
  tests/                        SQL tests and expected outcomes
src/main/java/com/marine/
  Main.java                     Desktop entry and command-line diagnostics
  model/                        Entity/field definitions and detached row models
  db/                           Configuration and connection ownership
  dao/                          PreparedStatement CRUD, reports and statistics
  ui/                           Sidebar, dashboard, editors, reports and workers
src/test/java/com/marine/        Offline tests and opt-in JDBC integration test
docs/                           Status, acceptance checks, ER diagram and report
```

## Implemented DBMS concepts and design decisions

- Primary, composite, foreign and candidate keys; UNIQUE, NOT NULL and CHECK constraints; FK indexes.
- Researcher specialization through subtype tables and multi-valued contacts through a separate relation.
- Normalization to 3NF under documented functional dependencies; species-local sample IDs are a confirmed requirement.
- Four reporting views with LEFT JOINs retaining entities with no samples.
- `AddDNASample`, `UpdateSpeciesStatus`, `GetSamplesByResearcher`; input validation and `SIGNAL` errors.
- `CountSpeciesSamples` and `CountResearcherSamples`; missing IDs return zero.
- NULL-safe AFTER UPDATE trigger recording old/new statuses and timestamp in the same transaction.
- ACID demonstrations, savepoints and error-handler rollback; database-scoped access roles.
- Prepared statements, JDBC resource ownership, background Swing work and schema-driven forms.

`EntityType` explicitly describes each existing entity; `EntityRow` represents its values. One small shared DAO/editor avoids eight near-identical CRUD implementations. Only whitelisted enum metadata forms SQL identifiers; all user values are parameters. The desktop writes through direct CRUD statements, so database constraints and the audit trigger enforce integrity for every writer. The standalone stored routines are demonstrated and tested separately, including through the opt-in JDBC test.

## Known limitations

- All live SQL/JDBC/UI acceptance tests remain pending until MySQL is available. Panel construction and validation are tested offline; desktop appearance has not been manually reviewed.
- The mini-project loads each module's rows into memory and filters locally; it has no pagination or optimistic concurrency control. Refresh after another user changes data.
- Foreign keys intentionally block deletion of referenced parents. Audit history also blocks species deletion; the interface does not automatically erase history or cascade records.
- Optional blank inputs become NULL. Email validation and rejection of future birth dates are additional UI rules; SQL remains the final authority.
- Rollback restores audit rows but may leave AUTO_INCREMENT gaps. DCL/DDL demonstrations run outside application transactions.
- A failed database save closes the input dialog; reopen it to correct values. There is no offline editing, automatic database migration, connection pool or bundled MySQL installer.
- Screenshots and measured live results must be added after actual execution; the report draft labels these gaps explicitly.
