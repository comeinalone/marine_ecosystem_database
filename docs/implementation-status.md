# Implementation status

Status recorded during development on 2026-10-12. **Implemented** means source exists and has been reviewed; it does not imply successful execution. **Build-verified** means Java compiled or an offline test ran. **Database-tested** requires a real MySQL connection; no component currently has that status.

| Component | Implementation | Verification |
| --- | --- | --- |
| Nine-table schema and seed data | Existing work preserved | Live execution unverified |
| 1NF/2NF/3NF document | Existing analysis; sample-ID scope confirmed | Documentation review |
| Four reporting views | Existing work preserved | Seed expectations and empty-parent tests supplied; unverified |
| Three stored procedures | Implemented | Manual and automated SQL test calls supplied; unverified |
| Two count functions | Implemented | SQL assertions supplied; unverified |
| Status audit trigger | Implemented, NULL-safe | Change/no-change/rollback assertions supplied; unverified |
| Transaction demonstrations | Implemented | COMMIT/ROLLBACK/SAVEPOINT/atomic checks supplied; unverified |
| DCL accounts and roles | Implemented; initially locked | Manual privilege checks supplied; unverified |
| Four query scripts | Implemented | Expected seed results documented; unverified |
| SQL integrity tests | Implemented | Not executed without MySQL |
| Maven / Java 17 target | Implemented | `mvn clean compile` succeeded on OpenJDK 21 |
| Executable JAR / Connector/J | Implemented | `mvn clean verify` succeeded; driver, entry point and manifest verified |
| Configuration / DAO / validation | Implemented | Offline tests passed; live JDBC unverified |
| Dashboard / all CRUD modules / reports | Implemented | Panel construction passed offline; connected workflows unverified |
| Optional JDBC integration test | Implemented | Disabled unless `MARINE_RUN_DB_TESTS=true`; not run |
| README / testing guide / report / ER diagram | Implemented | Documentation review; live screenshots pending |

Verified commands and outcomes:

- `mvn clean compile`: success, Java release target 17 on OpenJDK 21.
- `mvn -B clean verify`: success; 13 tests discovered, 12 passed, 1 live JDBC test skipped, zero failures/errors. Executable JAR produced at `target/marine-ecosystem.jar`.
- `java -jar target/marine-ecosystem.jar --check-config`: configuration loaded and MySQL JDBC driver available; no connection attempted.
- Packaged JAR inspection: application entry point, Connector/J driver class and Main-Class manifest present.
- `git diff --check`: passed; local documentation links resolve. Build output, environment files and configured local credentials are ignored.

The first build attempt encountered a read-only Maven cache; dependency downloads succeeded after permission was granted. The remaining build checks ran successfully. Maven reported overlapping dependency manifests during shading; the configured manifest transformer supplied the verified executable entry point. Test-double JDBC checks verify SQL construction, value binding and resource cleanup, not MySQL semantics.

No schema/data migration, commit or push was performed. All live behavior remains **unverified**, including MySQL-specific syntax, role activation, routines, constraints, trigger execution and transaction outcomes. Follow README initialization and `database/tests/README.md` before updating any status to database-tested.
