# Implementation status

Status updated on 2026-10-14 after the Smart Search rebuild. **Implemented** means source exists and has been reviewed; it does not imply successful execution. **Build-verified** means Java compiled or an offline test ran. **Database-tested** requires a real MySQL connection; no component currently has that status.

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
| Role switcher and centralized UI permissions | Existing staged implementation preserved and extended for research navigation | Permission matrix, handler guards, dialog revocation and display-dependent dropdown tests passed in final verification |
| Conservation Status Timeline | Implemented, searchable and chronologically ordered | Offline binding, empty-state and stale-query tests passed; actual trigger history unverified |
| DNA Sample Traceability | Implemented, joins use both sample-key columns | Offline composite bindings and NULL display passed; live joins unverified |
| Biodiversity Explorer | Implemented, SQL totals and region comparison | Offline DAO/panel checks passed; distinct aggregation and HAVING semantics await MySQL |
| Research Data Quality Checker | Implemented, documented rules and guarded editor link | Offline category bindings, role revocation, inline errors/retry passed; SQL rule results await MySQL |
| Researcher Contribution Dashboard | Implemented, CTE/window ranking, sample drilldown and Swing chart | Offline construction/empty/navigation passed; zero-parent aggregates and ranks await MySQL |
| Optional JDBC integration tests | CRUD, research and Smart Search semantic tests | Require both `MARINE_RUN_DB_TESTS=true` and `MARINE_TEST_DB_DISPOSABLE=true`; all three skipped |
| README / testing guide / report / ER diagram | Implemented | Documentation review; live screenshots pending |

Verified commands and outcomes (prior connection/packaging diagnostics retained where noted):

- `mvn clean compile`: success, Java release target 17 on OpenJDK 21.
- `mvn clean verify`: success; 166 tests discovered, 163 passed, 3 live JDBC tests skipped, zero failures/errors. Display-dependent frame/dropdown/dialog and ambiguous-autocomplete tests passed with display access outside the sandbox. The initial sandboxed run could not connect to X11; headless checks also passed before the final full verification. Executable JAR produced at `target/marine-ecosystem.jar`.
- `java -jar target/marine-ecosystem.jar --check-config`: configuration loaded and MySQL JDBC driver available; no connection attempted.
- Prior baseline `java -jar target/marine-ecosystem.jar --check-db`: failed with a MySQL connection-unavailable message, including a repeat outside the socket-restricted sandbox. No local `mysql` or `mysqld` executable was found; live tests were not enabled.
- Packaged JAR inspection: application entry point, Connector/J driver class and Main-Class manifest present.
- `git diff --check`: passed; local documentation links resolve. Build output, environment files and configured local credentials are ignored.

The final Smart Search verification used the existing approved Maven command outside the sandbox so Swing could access the configured display. No dependencies were added. Earlier baseline dependency-cache permission work is unchanged. Maven reported overlapping dependency manifests during shading; the configured manifest transformer supplied the verified executable entry point. Test-double JDBC checks verify SQL construction, value binding and resource cleanup, not MySQL semantics.

No schema/data migration, commit or push was performed. All live behavior remains **unverified**, including MySQL-specific syntax, role activation, routines, constraints, trigger execution and transaction outcomes. Follow README initialization and `database/tests/README.md` before updating any status to database-tested.

The five new tools use read-only Java-integrated queries without schema additions. Existing role behavior was preserved; no automatic staging, commit or push was performed. [Research tools](research-tools.md) lists new/extended files, SQL examples, quality rules, privileges and disposable integration instructions. No research data, entity metadata, keys, relationships, ER-diagram content, dependencies or JDBC configuration were changed. Connected UI appearance and live MySQL results still need acceptance testing.

## Smart Search rebuild

Implemented shared SQL definitions/criteria/results/service and the common advanced dialog, SQL autocomplete, bounded CRUD pages, joined researcher names, raw complete-key CRUD selection, all five tool selection paths, audit search and generation/role guards. No schema/index/ER/credential changes or application data writes were performed. Final `mvn clean verify`: **166 tests, 163 passed, 3 opt-in live tests skipped, zero failures/errors**. Offline tests cover query construction and Swing behavior, including display-dependent autocomplete and role revocation; live MySQL semantics, EXPLAIN/index introspection and desktop appearance remain pending behind the two disposable-database opt-ins. See [architecture, fields, test coverage, limits and complete file inventory](smart-search.md).
