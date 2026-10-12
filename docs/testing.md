# Desktop and integration acceptance checks

Actual result for every case below: **not run against MySQL**. Use an initialized disposable database. Record the server version, test date, screenshots and observed results after running. Do not conduct mutation checks on valuable research data.

| Case | Action | Expected result |
| --- | --- | --- |
| Startup | Launch with initialized database and manager account | Dashboard shows original seed counts 4 / 5 / 6 / 3 |
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

`mvn verify` exercises validation, configuration precedence, parameter binding, composite predicates, resource cleanup and panel construction without a database. The optional `JdbcIntegrationTest` uses a real database transaction and rolls it back. SQL scripts separately test MySQL constraints, routines, auditing, views and transaction behavior. Manual desktop checks are still needed; compiling Swing does not verify its visual layout.

Screenshots to capture after execution: dashboard, species editor, researcher tabs, sample reference selectors, four reports, audit rows, SQL integrity-test PASS, privilege rejection and a foreign-key error. Save them under `docs/screenshots/` with descriptive filenames and reference them from the report. Avoid passwords, local configuration contents or connection strings containing credentials in screenshots.
