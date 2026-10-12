# SQL and JDBC test guide

Live MySQL results have **not been recorded**. Expected results below are specifications, not execution evidence. Use MySQL 8.0.16+ with InnoDB and strict SQL mode on a disposable database initialized from this repository. Tests assume no concurrent writers. Never load test scripts as production initialization.

Run from the repository root. Stop after any unexpected error and inspect the transaction/session before continuing. Do not use `--force`.

```bash
mysql -u root -p < database/tests/01_views_test.sql
mysql -u root -p < database/tests/02_procedures_test.sql
mysql -u root -p < database/tests/03_integrity_test.sql
mysql -u root -p < database/transactions/transactions.sql
```

| Test | Expected | Actual |
| --- | --- | --- |
| Four seed-data views (`01`) | Every boolean assertion is 1 | Not run |
| Procedure success cases (`02`, T1–T5) | Correct rows; boolean assertions are 1; baseline restored | Not run |
| Procedure rejection cases (`02`, T6–T17) | SQLSTATE 45000; error messages specified beside calls | Not run |
| Composite PK (`03`) | Same sample ID in different species succeeds; duplicate tuple raises 1062 | Not run |
| Foreign key and deletion restriction (`03`) | Missing parent raises 1452; referenced deletion raises 1451 | Not run |
| UNIQUE (`03`) | Duplicate scientific name raises 1062 | Not run |
| NOT NULL (`03`) | NULL first name raises 1048 | Not run |
| CHECK constraints (`03`) | Invalid status, quality, date, capacity, contact type, experience and certification raise 3819 | Not run |
| Functions (`03`) | Missing/NULL/empty IDs count 0; inserted samples count 1 and 2 | Not run |
| Empty-parent views (`03`) | Species/researcher/location retained with zero samples | Not run |
| Audit trigger (`03`) | Two actual NULL/value transitions; identical updates add no audit rows | Not run |
| SAVEPOINT / ROLLBACK (`03`) | Third sample removed; all fixture rows/audit entries rolled back | Not run |
| COMMIT demonstration (`transactions.sql`) | Committed session-local row survives rollback of a later transaction | Not run |
| Atomic researcher insertion (`transactions.sql`) | All three records visible together, then all rolled back | Not run |
| DCL (`04`) | Reader can read/execute reports but cannot write; manager can write | Not run |
| JDBC opt-in test | CRUD, routines, view and trigger work through Connector/J; fixture rollback | Not run |

`03_integrity_test.sql` raises an error on any failed assertion and prints a PASS row only after all checks succeed. It creates helper test procedures in the database, then drops them on success. If it aborts, the handler rolls back DML; rerunning replaces the test procedures. Audit AUTO_INCREMENT sequence gaps may remain after rollback, which is normal.

The negative calls in `02` are commented intentionally: run each individually in an interactive session after the successful tests. A batch client must not stop on an expected error before reaching rollback. The automated `03` script also exercises routine rejections and database constraints with error handlers.

For permissions, load `database/security/dcl.sql` as DBA, choose separate passwords interactively, then:

```bash
mysql -h localhost -u marine_reader -p
```

```sql
SOURCE database/tests/04_permissions_test.sql;
```

Run its commented denial cases one by one, then repeat with `marine_app`. Always issue `ROLLBACK` after the update case, even following the reader's expected denial. `SHOW GRANTS` is evidence of configured roles; successful and rejected operations verify effective privileges. Existing accounts may already have other grants, so the role script assumes new lab accounts; inspect existing grants before reusing an account.

Enable the JDBC test only after initialization and local configuration:

```bash
MARINE_RUN_DB_TESTS=true mvn test -Dtest=JdbcIntegrationTest
```

It uses fixture ID `920001`, calls real JDBC APIs and rolls back data in `finally`. It requires seed references `1`, `101`, `201`, the existing `(301,1)` sample, and an unused fixture ID/scientific name. It does not initialize or erase a database. Leave the variable unset for offline builds.

Record the MySQL version, SQL mode, command, timestamp, output and screenshots when executing. Update the Actual column only after observing results.
