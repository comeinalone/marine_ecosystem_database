# Marine research tools

The Research Tools sidebar section and dashboard shortcuts open five tools using real MySQL queries through `ResearchDao`, JDBC prepared statements and the existing `UiTasks` Swing workers. No new tables, views, indexes, relationships, credentials or migrations are needed. Results are read-only for all roles. Changing the interface role refreshes the current tool; opening a tool or pressing Refresh reloads its database records. Older requests cannot overwrite newer selections. Failed queries show inline errors and can be retried; missing results have specific empty states.

The existing role switcher defaults to Administrator. Administrator can edit every existing CRUD entity; Researcher can edit only DNA samples; Viewer cannot edit any entity. Permissions apply to buttons, direct actions, dialogs and queued writes. The quality checker can open the existing sample editor for Administrator and Researcher, using the full composite sample key. Role changes close editors and invalidate pending editor requests. A write already running may finish.

**Roles are an educational UI simulation, not authentication or authorization at the database. Anyone can select Administrator. MySQL privileges for the configured JDBC account remain the actual database protection.** The dropdown never changes the database account or connection configuration.

| Tool | Behavior | DBMS concepts |
| --- | --- | --- |
| Conservation Status Timeline | Search common/scientific names or species IDs; view previous/new status and original timestamps; chronological or newest-first order with audit-ID tie breaking; total recorded changes | Existing NULL-safe `AFTER UPDATE` trigger, history, joins, temporal ordering |
| DNA Sample Traceability | Search species names or `(speciesID, sampleID)`; separate sample, species, collecting researcher, vessel and location sections; absent optional attributes show “Not recorded” | Composite primary key, foreign keys, five-table joins, referential integrity |
| Biodiversity Explorer | Select/search a recorded sampling region; compare all regions; see sample totals, distinct species, distinct endangered/critically endangered species; filter species by minimum sample count | `LEFT JOIN`, `JOIN`, `COUNT(DISTINCT)`, conditional aggregation, `GROUP BY`, `HAVING` |
| Research Data Quality Checker | Category filters, reasons and separate warning/constraint severity; authorized navigation to sample editor; no automatic repair | `CASE`, `IS NULL`, CTEs, `UNION ALL`, constraints and data integrity |
| Researcher Contribution Dashboard | All researchers including zero collections; sample/species/region totals, first/latest dates, sample-count ranks; sort by samples/species/latest activity; select researcher for samples; Swing bar chart | `LEFT JOIN`, CTE, aggregates, `COUNT(DISTINCT)`, `MIN`/`MAX`, `DENSE_RANK()` |

All tables support literal-text filtering and header sorting, including numeric counts. Selector searches filter database-backed choices, not invented records. Clear a search to restore all choices. The biodiversity minimum count applies only to the species list, never to region totals. “Region” means the existing `locationID` record; distinct visited regions are distinct location IDs, rather than ocean-name strings.

Biodiversity results describe **recorded sampling data**, not actual ocean biodiversity estimates. Endangered counts use the species' **current** stored conservation status, not its status on the collection date. Researcher ranks describe sample counts, not productivity, scientific impact or the quality of work. No tracking events, sample processing stages, custody records or history predating the audit trigger are inferred.

## Quality rules and their scope

The schema supports `DNA_quality IN ('High', 'Medium', 'Low')` and permits NULL. “Poor” is not a supported value. No scientific thresholds, minimum researcher age, expected marker lists or quality scores are introduced.

| Category | SQL rule | Interpretation |
| --- | --- | --- |
| Recorded quality | `DNA_quality = 'Low'` | Warning: review the recorded value; valid under the schema |
| Missing optional metadata | `DNA_quality IS NULL` | Warning: quality is not recorded; NULL is permitted |
| Missing optional metadata | Existing linked species has `conservation_status IS NULL` | Warning about collection context; NULL is permitted |
| Missing optional metadata | Existing linked vessel has `vessel_capacity IS NULL` | Warning about collection context; NULL is permitted |
| Date warnings | `collection_date > CURRENT_DATE` | Possible planned/mistyped collection; compare with MySQL server date, not Java's clock |
| Date warnings | `collection_date < researcher.date_of_birth` | Collection predates the recorded collector's birth; either record may require correction |
| Content warnings | `TRIM(gene_marker) = ''` | Whitespace-only marker is valid under SQL NOT NULL but lacks useful content |
| Constraint violations | NULL required sample date/marker/researcher/vessel/location; date before `1900-01-01`; non-NULL quality outside High/Medium/Low | Stored record violates an existing sample NOT NULL/CHECK rule |
| Constraint violations | Missing species, researcher, vessel or location parent detected by `LEFT JOIN` | Stored sample lacks a required foreign-key parent |

These checks concern DNA samples and selected linked context. Optional middle names, absent subtype records and absent extra contact numbers are not treated as defects. Multiple reasons for one sample are retained as separate rows. The issue count is a count of reasons, not distinct samples. A zero count means no issue under these rules, not certification of scientific validity or a full database integrity audit. Enforced constraints normally prevent constraint-violation rows; the checks are useful for imported or legacy data where enforcement was bypassed. SQL constraint tests remain available separately.

## SQL examples

These examples show the queries/expressions in [ResearchDao](../src/main/java/com/marine/dao/ResearchDao.java). `?` denotes a value bound through a prepared statement. Ordering choices come exclusively from booleans/enums; user text never forms SQL identifiers or ORDER BY clauses.

Timeline (the newest-first choice replaces both `ASC` tokens with `DESC`):

```sql
SELECT a.auditID, a.speciesID, f.common_name,
       a.old_status, a.new_status, a.changed_at
FROM Species_Status_Audit a
JOIN Fish_Species f ON f.speciesID = a.speciesID
WHERE a.speciesID = ?
ORDER BY a.changed_at ASC, a.auditID ASC;
```

The existing trigger writes history only when the stored status actually changes, including transitions to/from NULL. The UI never inserts audit records. After changing a species status in CRUD, opening the timeline reloads actual audit rows. External changes require opening the page again or Refresh.

Traceability (selected columns abbreviated here; the DAO includes all displayed sections):

```sql
SELECT d.speciesID, d.sampleID, d.collection_date, d.gene_marker, d.DNA_quality,
       f.common_name, f.scientific_name, f.conservation_status,
       d.researcherID, r.first_name, r.last_name,
       d.vesselID, v.vessel_name, d.locationID, l.location_name, l.ocean_region
FROM DNA_Sample d
LEFT JOIN Fish_Species f ON f.speciesID = d.speciesID
LEFT JOIN Researcher r ON r.researcherID = d.researcherID
LEFT JOIN Research_Vehicle v ON v.vesselID = d.vesselID
LEFT JOIN Ocean_Sampling_Region l ON l.locationID = d.locationID
WHERE d.speciesID = ? AND d.sampleID = ?;
```

Region comparison includes empty regions and counts each endangered species once even if several samples represent it:

```sql
SELECT l.locationID, l.location_name, l.ocean_region,
       COUNT(d.sampleID) AS total_samples,
       COUNT(DISTINCT d.speciesID) AS distinct_species,
       COUNT(DISTINCT CASE
           WHEN f.conservation_status IN ('Endangered', 'Critically Endangered')
           THEN d.speciesID END) AS endangered_species
FROM Ocean_Sampling_Region l
LEFT JOIN DNA_Sample d ON d.locationID = l.locationID
LEFT JOIN Fish_Species f ON f.speciesID = d.speciesID
GROUP BY l.locationID, l.location_name, l.ocean_region
ORDER BY l.location_name, l.locationID;

SELECT f.speciesID, f.common_name, f.scientific_name, f.conservation_status,
       COUNT(*) AS sample_count
FROM DNA_Sample d JOIN Fish_Species f ON f.speciesID = d.speciesID
WHERE d.locationID = ?
GROUP BY f.speciesID, f.common_name, f.scientific_name, f.conservation_status
HAVING COUNT(*) >= ?
ORDER BY sample_count DESC, f.speciesID;
```

Quality expressions excerpted from the `checked` CTE (the complete query independently unions each non-NULL reason and binds the category filter):

```sql
CASE WHEN d.DNA_quality = 'Low'
     THEN 'Recorded DNA quality is Low' END AS quality_issue,
CASE WHEN d.DNA_quality IS NULL
     THEN 'DNA quality is not recorded' END AS metadata_issue,
CASE WHEN d.collection_date > CURRENT_DATE
     THEN 'Collection date is in the future (server date)' END AS future_issue,
CASE WHEN d.collection_date < r.date_of_birth
     THEN 'Collection predates collecting researcher birth' END AS birth_issue
-- Final CTE result:
-- SELECT * FROM issues WHERE (? = 'ALL' OR category = ?)
-- ORDER BY speciesID, sampleID, category, reason
```

Researcher contribution query (the other sort options order by species diversity or most recent date; rank always uses sample count):

```sql
WITH participation AS (
    SELECT r.researcherID,
           CONCAT_WS(' ', r.first_name, r.middle_name, r.last_name) AS researcher,
           r.researcher_specialization,
           COUNT(d.sampleID) AS sample_count,
           COUNT(DISTINCT d.speciesID) AS distinct_species,
           COUNT(DISTINCT d.locationID) AS distinct_regions,
           MIN(d.collection_date) AS first_collection,
           MAX(d.collection_date) AS most_recent_collection
    FROM Researcher r LEFT JOIN DNA_Sample d ON d.researcherID = r.researcherID
    GROUP BY r.researcherID, r.first_name, r.middle_name,
             r.last_name, r.researcher_specialization
)
SELECT *, DENSE_RANK() OVER (ORDER BY sample_count DESC) AS sample_count_rank
FROM participation ORDER BY sample_count DESC, researcherID;
```

## Launch, privileges and verification

Use the existing environment/local properties configuration described in [README](../README.md). MySQL 8.0.16+ supports the existing CHECK constraints and the new CTE/window queries. The tools need SELECT on `DNA_Sample`, `Fish_Species`, `Researcher`, `Research_Vehicle`, `Ocean_Sampling_Region` and `Species_Status_Audit`. Existing reports additionally need SELECT on reporting views. No new EXECUTE, CREATE VIEW, INDEX or other DDL privilege is needed by the tools. Authorized CRUD still requires the corresponding MySQL INSERT/UPDATE/DELETE grants; the existing audit trigger must be installed by the DBA for new status changes to be recorded.

```bash
mvn clean verify
java -jar target/marine-ecosystem.jar --check-config
java -jar target/marine-ecosystem.jar --check-db
java -jar target/marine-ecosystem.jar
```

Offline tests exercise permission decisions, every research page across all roles, asynchronous empty/error/retry states, stale-request suppression, selector search, NULL display, composite-key binding and JDBC cleanup. Test doubles exist only in tests and do not validate MySQL semantics.

For live tests, configure `MARINE_DB_URL`, `MARINE_DB_USER`, `MARINE_DB_PASSWORD` to an initialized **disposable** database on a separate test server/container. Both explicit opt-ins are required. Never point these tests at working research data. The existing integration test requires original seed references; the research integration test creates its own fixture parents. IDs `920001`, `930001`, `930002` and associated fixture names/emails must be unused. All fixture DML is inside a transaction rolled back in `finally`; no test DDL is executed. Audit sequence gaps after rollback are possible.

```bash
MARINE_RUN_DB_TESTS=true MARINE_TEST_DB_DISPOSABLE=true \
  mvn test -Dtest=JdbcIntegrationTest,ResearchJdbcIntegrationTest
```

Live research assertions cover actual trigger rows, status/no-change/NULL transitions, composite lookup, duplicate species counts, empty regions, NULL/Low/Medium/High quality, defensible date/content warnings, HAVING filters, zero-sample researchers, window ranks and rollback. They do not disable constraints or insert fabricated audit history. Server SQL behavior and connected desktop appearance remain unverified without a running MySQL instance. The app still loads result lists without pagination; SQL computes research totals, but large catalogs and issue tables can use substantial memory. Separate refresh statements can see changes from concurrent writers at different instants.

## Implementation files

Created for this upgrade:

- `src/main/java/com/marine/model/ResearchTool.java`, `SampleKey.java`.
- `src/main/java/com/marine/dao/ResearchDao.java`.
- `src/main/java/com/marine/ui/ResearchPanel.java`, `ResearchTable.java`, `ResearchSelector.java`, `ConservationTimelinePanel.java`, `SampleTraceabilityPanel.java`, `BiodiversityPanel.java`, `DataQualityPanel.java`, `ContributionPanel.java`.
- `src/test/java/com/marine/dao/ResearchDaoTest.java`, `ResearchJdbcIntegrationTest.java`; `src/test/java/com/marine/ui/ResearchPanelsTest.java`.
- `docs/research-tools.md`.

Extended: `EntityDao`, `RolePermissions`, `CrudPanel`, `DashboardPanel`, `MainFrame`, `UiTasks`, `JdbcIntegrationTest`, `RolePermissionsTest`, `RoleWindowTest`, `SwingPanelsTest`, `README.md`, `docs/testing.md`, `docs/implementation-status.md`, `database/tests/README.md` and the report draft. The pre-existing staged role implementation and tests were retained. Database SQL definitions/data, entity metadata, keys, relationships, the ER diagram and connection configuration were preserved.
