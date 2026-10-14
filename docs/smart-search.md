# Smart Search Engine

The eight editable entity modules, the read-only conservation audit, and all five research tools share one MySQL search engine. No schema, indexes, ER diagram, credentials, application authentication, audit triggers or baseline research records are changed. Search is available to every role that can read a module; existing CRUD permissions still govern edits.

## Architecture

- `search/SearchDefinition` is the closed registry of field names, SQL expressions, joins, choices, sort columns and primary keys. `SearchField` describes a relevant filter and its display column. Joined researcher names are display/search expressions, never new stored columns.
- `SearchCriteria` carries immutable optional filters, a quick-search term, matching mode, allowlisted sort, page size and offset. `SearchService.plan` validates field-specific types and bounds before executing SQL. Blank inputs are omitted, populated field filters combine with **AND**, and quick search combines relevant text/key fields with **OR** inside that AND expression.
- `SearchService` prepares a count and a bounded row query on one JDBC connection, binds every value (including limits and offsets), and closes connection, statements and result sets with try-with-resources. Dates bind as SQL dates; nullable results remain NULL.
- `SearchResult.Hit` carries all detached display values and the complete primary-key tuple. `EntityDao.find` reloads a selected CRUD record using the **raw selected keys**, without applying text normalization to its identity. CRUD writes still use the original `EntityType` field whitelist, so joined display values never become INSERT/UPDATE columns.
- `AdvancedSearchDialog` hosts the shared, headless-testable `AdvancedSearchPanel`. It has relevant filters only, match operators, enum dropdowns, numeric/date ranges, explicit NULL filters, sorting, 1–500 rows per page (100 default), counts, Previous/Next, Search/Clear/Select/Cancel, double-click and Enter selection. All fields are optional. Its scrollable form/table split adapts to laptop screens; Escape cancels and Tab navigates controls. Enter from a form invokes Search; Enter in the table selects the highlighted result.
- `SearchAutocomplete` debounces focused name input by 300 ms and requests up to 12 suggestions from MySQL. Suggestions show identities and identifying details. Down opens keyboard selection, and an explicit suggestion action resolves its ID. `ResearchSelector` also executes SQL for quick text/name/key input; it retains real keys and requires an explicit choice for multiple matches.
- `SearchRequests` gates worker callbacks by generation, role revision and picker lifecycle. Editing criteria, clearing, selecting another identity, switching roles, or closing a picker invalidates older responses, including older errors. `UiTasks` runs JDBC off the Swing event thread. Existing research workers retain their generation guard.

SQL searches replace CRUD table filtering and the old in-memory research-selector search. The remaining **Filter displayed analysis/report** controls are convenience filters over already computed analytical/report rows. They do not resolve identities or replace Advanced Search. Header sorting rearranges the current page; use the dialog's Sort control to sort the complete SQL result across pages. Each fresh search restores the SQL ordering.

## Searchable fields

All displayed columns are also safe sorting options. Every module additionally supports explicit NULL matching where applicable.

| Module | Search fields |
| --- | --- |
| Fish Species | speciesID, common name, scientific name, conservation status dropdown |
| Researchers | researcherID, normalized full name, first/middle/last name, specialization, email, birth date/range |
| Researcher Contacts | researcherID, researcher name, contact type dropdown, phone number, joined specialization/email |
| Marine Biologists | researcherID, researcher name, field experience/range, diving certification dropdown, joined specialization/email |
| Geneticists | researcherID, researcher name, laboratory, research focus, joined specialization/email |
| DNA Samples | speciesID, sampleID, species common/scientific name, researcherID/name, joined specialization/email, vesselID/name, locationID/region name, collection date/range, gene marker, DNA quality dropdown |
| Research Vessels | vesselID, vessel name, capacity/range, home port |
| Sampling Regions | locationID, location name, ocean region |
| Conservation Audit | auditID, speciesID, species common/scientific name, old/new status dropdowns, change date/range |

Contacts, Marine Biologists and Geneticists display **Researcher Name immediately after researcherID**. Names, specialization and email come from a `LEFT JOIN Researcher`; the original editable columns and keys remain unchanged.

## Exact names, partial names and duplicate names

For an exact normalized name such as `Sana Khan`, open Researchers → Advanced Search, enter it in **Full name**, choose **Exact**, and Search. Full names use:

```sql
TRIM(REGEXP_REPLACE(
    CONCAT_WS(' ', r.first_name, r.middle_name, r.last_name),
    '[[:space:]]+', ' '
))
```

`CONCAT_WS` handles a NULL middle name; the regular expression collapses multiple spaces, tabs and other whitespace, including a blank middle name. Input whitespace is similarly collapsed. SQL uses LOWER and an explicit UTF-8 binary collation for case-insensitive, accent-sensitive comparison; accents remain significant. Ordinary text comparisons also trim stored outer spaces.

**Exact** uses `= ?`. **Contains** uses `LIKE ? ESCAPE '!'`. The engine escapes literal `!`, `%` and `_` before adding its own surrounding `%`, so user-entered wildcards never become pattern operators. A surname can be entered alone in Last name, Full name with Contains, or the quick-search box. Blank text is an omitted filter; choose Is NULL to search missing values.

The quick-search **Include partial matches** checkbox affects its quick-search term only. Per-field Exact/Contains operators remain independent. With partial search enabled, exact normalized full-name matches appear before partial-name matches, followed by exact text priority and the chosen sort/key tie breakers. With it disabled, quick search uses exact comparisons. Numeric ID fields always use exact comparisons unless an explicit numeric range is selected.

There is no DISTINCT-by-name or assumption of name uniqueness. Every researcher with that full name is returned and displays researcherID, name, specialization and email. The dialog selects neither one result nor multiple results automatically. Select a particular row, double-click it, or press Enter; the selected database key drives navigation and editing. Research tool dropdowns may retain a previous explicit identity or select a sole initial choice; they never choose the first of several results.

## Foreign keys and composite keys

Name autocomplete on sample filters resolves species/researcher/vessel/region records through the corresponding entity definition. An explicit suggestion can populate an empty ID filter. Existing entered IDs and unrelated form fields are kept; clear an ID before changing it if needed. Names never become foreign keys, and a name shared by several researchers remains several identified choices.

Reference fields in CRUD editors provide both name suggestions and Advanced Search. The initial dropdown contains at most 100 choices; an existing reference outside that page is retained by its ID. The picker can search the entire database. Choosing a referenced record changes that reference only, and never silently overwrites other user-entered form values.

A sample's identity is always `SampleKey(speciesID, sampleID)`. Search results show both columns. Traceability passes both values directly to its parameterized joined lookup; no manual composite-key entry is needed. CRUD reload, UPDATE and DELETE use both columns. Contact records retain `(researcherID, contact_type, phone_number)`, including the original raw key values when the contact tuple is edited.

## Research tools

| Tool | Advanced Search behavior and retained analysis |
| --- | --- |
| Conservation Status Timeline | Search audit by species/status/date; selecting a change resolves its species, loads the existing timeline and highlights auditID. Find species also searches species without any audit history. Newest-first ordering and original trigger history remain intact. |
| DNA Sample Traceability | All sample filters, including any known combination of species/researcher/vessel/region/date/marker/quality. Explicit result selection passes SampleKey to the original linked context query. |
| Biodiversity Explorer | Search region names/IDs/ocean region; selecting loads the complete region comparison, highlights that region and loads its species list. **Minimum samples per species (HAVING)** remains a separate SQL aggregation threshold. Region totals are unaffected by it. |
| Research Data Quality Checker | Search any DNA sample, then run the existing issue/category rules restricted in SQL by both key components. Multiple reasons remain multiple rows. A valid sample can have zero issues. All samples clears this restriction; permitted roles can open the selected flagged sample's existing editor. |
| Researcher Contribution Dashboard | Search researcher names/IDs/specialization/email; selecting reloads the complete ranked summary, highlights the specific researcher and retrieves their collections by researcherID. The global aggregation, zero-sample participants, ranks, sorting and chart are preserved. |

The existing Reports → Conservation status history entry exposes the same read-only audit picker. Other report views keep their existing behavior.

## SQL examples and JDBC safeguards

A combined sample query follows this structure (projection abbreviated here; production uses an explicit alias for every column):

```sql
SELECT e.speciesID AS speciesID, e.sampleID AS sampleID,
       f.common_name AS species_name, v.vessel_name AS vessel_name
FROM DNA_Sample e
LEFT JOIN Researcher r ON r.researcherID = e.researcherID
LEFT JOIN Fish_Species f ON f.speciesID = e.speciesID
LEFT JOIN Research_Vehicle v ON v.vesselID = e.vesselID
LEFT JOIN Ocean_Sampling_Region l ON l.locationID = e.locationID
WHERE LOWER(CONVERT(f.common_name USING utf8mb4)) COLLATE utf8mb4_bin
      LIKE ? ESCAPE '!'
  AND e.vesselID = ?
  AND e.collection_date >= ? AND e.collection_date <= ?
ORDER BY e.speciesID ASC, e.sampleID ASC
LIMIT ? OFFSET ?;
```

Production text expressions additionally normalize full names and trim ordinary text; CASE expressions ahead of the sort columns implement exact-match priority. A separate `COUNT(*) AS match_count` uses the same joins and predicates without the limit. Only registry expressions and ASC/DESC choices form SQL; field names and sort columns from callers are checked against that registry. User text, numeric/date bounds, choices and identity values are bound parameters, never concatenated SQL. Apostrophes, brackets and SQL-looking text are literal values.

Search joins point from a record to a single primary-key parent; they never join contacts or samples onto a parent search, avoiding fan-out duplicates. LEFT JOINs retain rows without related values when no related-field predicate is requested. A filter on a joined name intentionally restricts to records matching that parent. Complex joins never use SELECT *. Conservation audit is never passed into an editable EntityRow.

Date ranges include both boundary dates. TIMESTAMP audit filters use `>= start` and `< end + one day`, including the entire final day rather than only midnight. Audit day boundaries follow the connection/server session's date interpretation.

Roles are a UI simulation. Search requires SELECT privileges on the relevant joined tables. Existing UI restrictions and guarded handlers still limit writes; actual database security remains MySQL grants for the configured JDBC account.

## Index inspection and performance

The existing [schema](../database/schema/01_create_tables.sql) was inspected; no index or schema change was made:

- Every base table has its original primary key, including the sample and contact composite keys.
- Researcher email and species scientific name have UNIQUE indexes.
- Samples have `idx_dna_researcher`, `idx_dna_vessel`, and `idx_dna_location`, besides `(speciesID, sampleID)`.
- Foreign-key supporting indexes apply to subtype/contact researcher IDs and audit speciesID. MySQL creates a supporting child index when the DDL does not already provide one.

Exact key lookup uses primary-key predicates without normalization. Composite sample lookup is selective when both keys are supplied; sampleID alone is not the leading column of its primary-key index. Parent joins use indexed IDs. Case folding, TRIM, computed full names and `%contains%` generally prevent ordinary B-tree text indexes from supplying a selective lookup. Collection/change date and capacity/experience filters have no dedicated range indexes in the checked schema. Exact-match CASE ordering may require a filesort. COUNT may scan all matching records even when only one page is returned, and large offsets become more expensive. Debouncing, 100-row CRUD pages, 1–500-row advanced pages and 12-suggestion autocomplete bound displayed/search result memory, not database scan cost.

Live SHOW INDEX / EXPLAIN / timing measurements have **not** been performed. Any further index change requires measured workload evidence and explicit schema approval. Research analytical summaries, issue lists and report views retain their complete existing result sets, so very large analytical datasets can still use substantial memory. Count and page are separate SELECTs on the same connection, not a pinned transaction snapshot; concurrent writers can change totals/pages between statements. Refresh and search again after external changes. No optimistic locking or offline search is introduced.

## Validation and manual acceptance

Offline tests verify query construction/bindings/closure, optional filters, exact/partial modes, normalization, surname matching, duplicate identities, joins, literal wildcards and injection-looking input, date/numeric ranges, enums, NULL/zero/one/multiple/large-count results, pagination, sorted-table identity selection, contact/subtype displays, raw composite-key CRUD resolution, all five research tool selection paths, role revocation and stale responses. JDBC doubles exist only under src/test; they are not evidence that MySQL semantics passed. Display-dependent tests verify autocomplete selection and dialog revocation when a display is available.

`SearchJdbcIntegrationTest` exercises actual MySQL normalized duplicate names, exact-before-partial ordering, optional middle names, surname searches, combined filters, NULLs, dates/numeric ranges, literal `%`/`_`, SQL-looking input, paging, contact/subtype joins, multiple contacts without sample fan-out, composite sample lookup/editing, audit dates/triggers and all research query paths. It performs fixture DML only inside a rollback transaction and never creates/alters schema. Both explicit opt-ins and an initialized disposable database are required; fixture IDs `940001`–`940007` must be unused.

```bash
mvn clean verify
# If the shell has an unavailable DISPLAY, run the same suite headlessly:
env -u DISPLAY mvn clean verify

# Only after configuring JDBC to an initialized disposable database:
MARINE_RUN_DB_TESTS=true MARINE_TEST_DB_DISPOSABLE=true \
  mvn test -Dtest=JdbcIntegrationTest,ResearchJdbcIntegrationTest,SearchJdbcIntegrationTest
```

Final `mvn clean verify` succeeded: **166 tests discovered, 163 passed, 3 opt-in live tests skipped, zero failures/errors**. The full verification ran with display access outside the sandbox and included the window/autocomplete tests. Live MySQL tests and connected visual desktop review remain pending. The existing schema/credentials are left unchanged and no application data was modified during implementation.

Manual steps on a disposable database:

1. Open every CRUD tab/tool under each role. Search/Advanced Search stay available; only permitted CRUD controls can write. Search without filters, Clear and Cancel; exercise empty and populated pages.
2. Add two disposable researchers named Sana Khan with different IDs/emails/specializations. Give one a NULL/blank middle name and store extra whitespace/case variations. Full name Exact returns both normalized exact records; Contains/quick partial includes longer matching names afterward. Surname-only search works. Neither ambiguous names nor single dialog results automatically select a row.
3. Select the second duplicate after changing table order, and verify the intended researcherID and editor record. Select it from a sample/contact/subtype reference picker; confirm unrelated entered values remain intact.
4. Verify Researcher Name follows researcherID in Contacts, Marine Biologists and Geneticists. Combine name, enum and specialization-specific filters; edit a contact's full tuple and verify its other contacts are unaffected.
5. Create disposable samples with the same sampleID under different species. Combine species/researcher/vessel/region names with date/marker/quality filters. Select each in CRUD and Traceability and verify both key components and linked context.
6. Search inclusive collection and audit date ranges, numeric capacity/experience ranges, open bounds and Is NULL. Reversed/malformed ranges must display validation feedback without a query.
7. Enter apostrophes, SQL-looking strings, `%`, `_`, `!`, `[` and `.*`; results treat them literally. Check total counts, bounded pages and server sort; select using double-click and keyboard.
8. Search audit old/new statuses and dates, select a change and verify timeline highlight/newest ordering. Find a species with no history. Audit remains read-only.
9. Search a biodiversity region and adjust Minimum samples (HAVING); verify the species list changes while full region totals remain. Search a quality sample; verify independent issue reasons and no automatic edits, then All samples. Search a zero-sample researcher in Contributions; verify their row, zero collections and global rankings/chart remain present.
10. Start a slow search, edit criteria/Search again, Clear/Cancel, or switch roles. Older results/errors must not reappear; stale selections cannot open an editor. Resize on a typical laptop and verify scroll/Tab/Enter/Down/Escape behavior.

## Change inventory

New production files:

- `src/main/java/com/marine/search/SearchCriteria.java`, `SearchDefinition.java`, `SearchField.java`, `SearchResult.java`, `SearchService.java`.
- `src/main/java/com/marine/ui/AdvancedSearchDialog.java`, `AdvancedSearchPanel.java`, `SearchAutocomplete.java`, `SearchRequests.java`.

Modified production files:

- `dao/EntityDao.java`, `ResearchDao.java`, `ReportDao.java` (under src/main/java/com/marine).
- `ui/CrudPanel.java`, `EntityDialog.java`, `EntityTableModel.java`, `ResearchSelector.java`, `ResearchPanel.java`, `ResearchTable.java`, `ReportsPanel.java`, `MainFrame.java`.
- `ui/ConservationTimelinePanel.java`, `SampleTraceabilityPanel.java`, `BiodiversityPanel.java`, `DataQualityPanel.java`, `ContributionPanel.java`.

New tests: `search/SearchServiceTest.java`, `SearchJdbcStub.java`, `SearchJdbcIntegrationTest.java`; `ui/SmartSearchUiTest.java`, `SearchWindowTest.java` (under src/test/java/com/marine). Updated test: `ui/ResearchPanelsTest.java`.

Documentation: new `docs/smart-search.md`; updated `README.md`, `docs/testing.md`, `docs/research-tools.md`, `docs/implementation-status.md`.
