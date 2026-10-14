# Desktop and integration acceptance checks

Actual result for every case below: **not run against MySQL**. Use an initialized disposable database. Record the server version, test date, screenshots and observed results after running. Do not conduct mutation checks on valuable research data.

| Case | Action | Expected result |
| --- | --- | --- |
| Startup | Launch with initialized database and manager account | Dashboard shows original seed counts 4 / 5 / 6 / 3 |
| Role default | Launch application | Current Role is Administrator; full CRUD controls and Administrator Dashboard |
| Researcher role | Select Researcher with a row selected; visit all modules and researcher tabs | Heading/shortcuts update immediately; only DNA samples allow add/edit/delete; all modules can be viewed |
| Viewer role | Select Viewer and visit all modules | Viewer Dashboard; all CRUD disabled; search, sort, refresh and reports remain available |
| Role restoration | Switch from Viewer back to Administrator | CRUD controls restored, with edit/delete requiring a selected row |
| Role refresh | Change role on a module or while statistics load | Current accessible page retained; fresh MySQL dashboard counts loaded, including after an in-flight request |
| Pending editor | Switch role programmatically while a form/reference lookup or delete confirmation is pending | Stale form/confirmation cannot submit a write; editor closes on role changes |
| Reports by role | View all four reports and conservation-status audit history as each role | Reports and history remain read-only and accessible |
| Switch does not write | Switch roles without submitting edits and compare database records/audit counts | Database records and audit history unchanged; JDBC account unchanged |
| Missing server | Stop MySQL or configure an unreachable port | Clear connection error; UI remains responsive; Refresh retries |
| Species CRUD | Add unused ID, edit its name/status, search, delete before status change | Added/edited rows displayed; unreferenced species can be deleted |
| Conservation audit | Change a fixture status, repeat same status, change to blank | Real transitions create audit rows; unchanged status does not |
| Audit restriction | Delete species with audit history | FK denial explained; history remains intact |
| Researcher CRUD | Add researcher, edit specialization, search | Values saved and refreshed; invalid email/DOB blocked |
| Contacts | Add two contact types, edit a contact tuple, delete it | Three-column key used; other contacts unaffected |
| Subtypes | Add/edit/delete biologist and geneticist for an existing researcher | Base researcher retained; experience/certification validated |
| Parent deletion | Delete researcher while contacts/subtypes/samples exist | FK denial explained; no automatic cascading |
| Vessels | Add/edit/delete fixture; try zero capacity | Valid values saved; zero blocked; referenced vessel deletion denied |
| Locations | Add/edit/delete fixture; try blank ocean region | Valid values saved; required value blocked; referenced location denied |
| Samples | Add same sample ID for two different species | Both composite-key tuples permitted |
| Sample update/delete | Edit/delete only one of those tuples | Other species' sample with same ID remains unchanged |
| Sample references | Open add dialog | Labeled selections show IDs/names for species/researchers/vessels/locations |
| Sample validation | Try missing references, invalid date, blank gene marker | Form rejects input before saving |
| Duplicate key | Repeat existing species ID/email/scientific name/sample tuple | Clear duplicate message; original row retained |
| Search/sort | Search `[` or `.*`; sort sample IDs numerically | Search treats characters literally; IDs sort numerically |
| Reports | Select all four reports and audit history | Read-only results match SQL queries; search works |
| Read-only account | Launch as reader; read reports then attempt mutation | Reads succeed; database denies writes with clear message |
| Background work | Refresh a large table / simulate slow network | Window stays interactive; duplicate actions disabled while loading |
| Resize | Resize window and dialogs; scroll wide tables | Content remains accessible; tables scroll horizontally/vertically |
| Research tools by role | Open every tool as Administrator, Researcher and Viewer | All tools accessible and read-only; only Admin/Researcher can open flagged sample editor |
| Timeline | Search species, change fixture status in CRUD, return to timeline; toggle newest first | Actual audit rows and count refresh; no-change update adds none; empty history explained |
| Traceability | Choose two samples sharing sampleID across different species; use NULL quality | Correct composite identities and linked sections; absent optional values shown as not recorded |
| Biodiversity | Compare regions, including empty region; sample same species twice; set minimum count | Totals count samples, species/endangered totals remain distinct; HAVING filters list only |
| Quality checker | Review Low/NULL quality, future and pre-birth dates, blank marker; filter each category | Independent documented reasons; valid-record warnings distinct from constraint violations; no automatic writes |
| Flagged sample editor | Select issue, open editor as Researcher; revoke role while lookup loads | Correct composite-key sample editor; Viewer and revoked pending requests cannot edit |
| Contributions | Include researcher with zero samples; sort counts/diversity/latest date; select a row | Zero researcher retained; accurate distinct counts/dates/rank/chart and relevant collections |
| Stale results | Change species/order/filter during a slow research query | Earlier completion never overwrites the current selection; errors are inline and Refresh retries |

`mvn clean verify` exercises validation, configuration precedence, parameter binding, composite predicates, resource cleanup, panel construction, the full permission matrix, role transitions, guarded action handlers and asynchronous dashboard refreshes without a database. Display-dependent tests exercise the dropdown and dialog revocation when a graphical session is available. The optional `JdbcIntegrationTest` uses a real database transaction and rolls it back. SQL scripts separately test MySQL constraints, routines, auditing, views and transaction behavior. Manual desktop checks are still needed; automated Swing tests do not verify its visual layout.

Screenshots to capture after execution: dashboard, species editor, researcher tabs, sample reference selectors, four reports, audit rows, SQL integrity-test PASS, privilege rejection and a foreign-key error. Save them under `docs/screenshots/` with descriptive filenames and reference them from the report. Avoid passwords, local configuration contents or connection strings containing credentials in screenshots.

Research tools also have offline JDBC/Swing tests for composite-key binding, NULL rendering, every tool under every role, selector search, empty results, inline failures/retry and stale-query suppression. These test doubles are not evidence of MySQL execution. Both `JdbcIntegrationTest` and `ResearchJdbcIntegrationTest` require `MARINE_RUN_DB_TESTS=true` **and** `MARINE_TEST_DB_DISPOSABLE=true`; configure the existing JDBC settings to an initialized disposable database first. See [research rules, SQL and integration coverage](research-tools.md). Capture each research tool after real database execution.

## Smart Search acceptance

Follow [Smart Search manual steps, fields and SQL/performance notes](smart-search.md). New offline tests cover search plans, typed filters, literal SQL-looking input, duplicate identities, composite selection, all five tools, NULL/large-count results, role changes and stale workers. `SearchWindowTest` exercises real autocomplete focus/selection and dialog revocation when a display is available. `SearchJdbcIntegrationTest` adds rollback-only MySQL semantics coverage and requires both existing disposable-database opt-ins. All three integration classes remain unexecuted by default; no working research data should be used.

An unavailable but populated DISPLAY can prevent Swing initialization; use `env -u DISPLAY mvn clean verify` for headless verification. Display-dependent tests then skip explicitly. This does not replace visual desktop acceptance.
