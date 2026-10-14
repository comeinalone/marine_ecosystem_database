# Marine Ecosystem — Safe Demo Database

This package creates an **independent** MySQL database named `Marine_Ecosystem_Demo`, leaving the original `Marine_Ecosystem_Database` and its records unchanged. It reuses your current repository's authoritative nine-table schema, reporting views, procedures, functions and trigger. Records are **synthetic demonstration data**, not actual field observations or verified conservation assessments.

## Data design

| Records | Count / characteristic |
|---|---|
| Researchers | 30; two distinct researcher IDs both named **Sana Khan**; IDs 28–30 intentionally have zero collections |
| Contacts | 37, including several Work and Mobile entries |
| Marine biologists and geneticists | Valid researcher-linked subtype data |
| Species | 50, with common/scientific names and varied conservation-status values |
| Research vessels | 10, with names, capacities and home ports |
| Sampling locations | 12, across Arabian Sea, Bay of Bengal and Indian Ocean |
| DNA samples | 446, with composite `(speciesID, sampleID)` identifiers; 1/2/3+ sample groupings and several regions |
| Conservation audit events | 12, generated through the project's existing trigger as synthetic status updates |
| Quality-check cases | `Low`, NULL, future-date and whitespace-marker records, alongside many healthy records |

## Setup on Linux, in your local repository

1. Copy `generate_seed.py`, `build_demo_sql.py` and `demo_seed.sql` into your repository in a new folder, e.g. `database/demo/`. (The generated data file is supplied so you do **not** need to regenerate it.)
2. From the repository root, build one install SQL file:

```bash
python3 database/demo/build_demo_sql.py .
```

3. Review the generated `database/demo/marine_demo_full.sql` and make sure it creates **Marine_Ecosystem_Demo**, not your existing database.
4. Run the script with a MySQL user allowed to create databases, tables, views, stored programs and triggers:

```bash
mysql -u root -p < database/demo/marine_demo_full.sql
```

**Do not run against an existing `Marine_Ecosystem_Demo`.** The generated SQL begins with `CREATE DATABASE Marine_Ecosystem_Demo;` (without `IF NOT EXISTS`), so a second run is expected to fail before applying seed records. Never change it to use `DROP DATABASE` unless you deliberately intend to delete that disposable database.

5. Check populated demo data:

```bash
mysql -u root -p < database/demo/demo_checks.sql
```

6. To run the Swing application against the demo database, configure a *separate local* `MARINE_DB_URL` referencing `Marine_Ecosystem_Demo`, and use a MySQL account that has privileges on that database. Your existing `marine_app` grants on the original database **do not automatically apply** to the demo DB. Use an appropriately privileged demo account or create one through your DBA. Restore the original environment after the presentation.

```bash
export MARINE_DB_URL='jdbc:mysql://localhost:3306/Marine_Ecosystem_Demo?connectTimeout=5000&socketTimeout=15000'
java -jar target/marine-ecosystem.jar --check-db
java -jar target/marine-ecosystem.jar
```

## Demo walkthrough (reliable known records)

1. **Duplicate-name search:** search `Sana Khan`. Two separate researchers (IDs **1** and **2**) should appear; choose by email/specialization.
2. **Exact researcher:** search `Aarav Shah` (ID **3**); show linked contacts, subtype if applicable, and sample collections.
3. **Species search:** `Whale Shark` (ID **301**) or `Yellowfin Tuna` (ID **303**). These have numerous linked DNA samples.
4. **DNA traceability:** select `(301, 1)` or search `Whale Shark`. The species/researcher/vessel/location joins will resolve.
5. **Vessel search:** `Ocean Explorer` (ID **101**) and `Coral Voyager` (ID **104**).
6. **Region:** `Arabian Sea Station A` (ID **201**). Several prominent species have multiple samples there. Minimum-sample threshold `1`, then `2`, then `3` yields nonempty results.
7. **Conservation timeline:** `Whale Shark` (301) and `Scalloped Hammerhead` (304) each have two audit changes.
8. **Quality checker:** choose **All** and **Recorded quality**; the latter has multiple `Low` sample warnings. Some NULL-quality and future-date records also appear.
9. **Researcher contribution:** researcher IDs **28, 29, 30** should rank at zero samples while other researchers have varied sample counts.

## Caveats and final checks

- DB install and JDBC were **not executed in the creation environment**. Live compatibility and view/routine syntax depend on your checked-out repo and MySQL setup.
- The `Fish_Species` table, by original project design, also represents some non-fish marine species (e.g. turtles). If asked, describe the table's broad application scope or exclude those rows from the presentation.
- Dates and status transitions are illustrative, not scientific claims.
- Synthetic email addresses use `example.org` and are not intended for contacting people.
- Do not push actual passwords or local `config/database.properties`. The database itself is not transferred by Git; commit these SQL/scripts if you want reproducible demo setup on Windows.
