package com.marine.ui;

import com.marine.dao.*;
import com.marine.model.*;
import com.marine.search.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.marine.ui.RoleUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SmartSearchUiTest {
    private SearchResult.Hit hit(EntityType entity, Map<String, Object> row) { return new SearchResult.Hit(SearchDefinition.entity(entity), row); }
    private static void idleAfterWorkers() throws Exception {
        CountDownLatch tick = new CountDownLatch(1);
        onEdt(() -> { javax.swing.Timer timer = new javax.swing.Timer(150, event -> tick.countDown()); timer.setRepeats(false); timer.start(); return null; });
        assertTrue(tick.await(3, TimeUnit.SECONDS));
    }
    @Test void allCrudModulesAndFiveResearchToolsExposeAdvancedSearchForAllRoles() throws Exception {
        onEdt(() -> {
            SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> { fail("Construction/roles must not query"); return null; });
            RoleContext roles = new RoleContext(); EntityDao dao = new EntityDao(jdbc); ResearchDao research = new ResearchDao(jdbc);
            List<JPanel> panels = new ArrayList<>();
            for (EntityType type : EntityType.values()) panels.add(new CrudPanel(type, dao, roles));
            panels.addAll(List.of(new ConservationTimelinePanel(research, roles), new SampleTraceabilityPanel(research, roles),
                    new BiodiversityPanel(research, roles), new DataQualityPanel(research, roles, key -> fail("Unexpected edit")), new ContributionPanel(research, roles)));
            for (UserRole role : UserRole.values()) {
                roles.setRole(role);
                for (JPanel panel : panels) assertTrue(button(panel, "Advanced Search").isEnabled());
            }
            return null;
        });
    }
    @Test void advancedResultsNeverAutoSelectDuplicateNamesAndSelectUsesSortedModelIdentity() throws Exception {
        SearchDefinition def = SearchDefinition.entity(EntityType.RESEARCHER);
        List<Map<String, Object>> rows = List.of(Map.of("researcherID", 9, "full_name", "Sana Khan", "email_address", "nine@example.invalid"),
                Map.of("researcherID", 2, "full_name", "Sana Khan", "email_address", "two@example.invalid"));
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            return sql.contains("COUNT(*)") ? SearchJdbcStub.count(2) : SearchJdbcStub.rows(def, rows);
        });
        AtomicReference<SearchResult.Hit> selected = new AtomicReference<>();
        AdvancedSearchPanel panel = onEdt(() -> new AdvancedSearchPanel(def, new SearchService(jdbc), new RoleContext(), selected::set, () -> { }));
        try {
            onEdt(() -> { panel.search(); return null; });
            await(() -> components(panel, JTable.class).get(0).getRowCount() == 2);
            onEdt(() -> {
                JTable table = components(panel, JTable.class).get(0);
                assertEquals(-1, table.getSelectedRow()); assertFalse(button(panel, "Select").isEnabled()); assertNull(selected.get());
                table.getRowSorter().toggleSortOrder(0); table.setRowSelectionInterval(0, 0);
                button(panel, "Select").doClick(); assertEquals(Map.of("researcherID", 2), selected.get().identity());
                button(panel, "Clear").doClick(); assertEquals(0, table.getRowCount()); assertFalse(button(panel, "Select").isEnabled());
                return null;
            });
        } finally { onEdt(() -> { panel.close(); return null; }); }
    }
    @Test void zeroAndOneResultAreClearAndOneStillRequiresSelection() throws Exception {
        SearchDefinition def = SearchDefinition.entity(EntityType.SPECIES); AtomicInteger reads = new AtomicInteger();
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(reads.get())
                : SearchJdbcStub.rows(def, reads.getAndIncrement() == 0 ? List.of() : List.of(Map.of("speciesID", 1))));
        AdvancedSearchPanel panel = onEdt(() -> new AdvancedSearchPanel(def, new SearchService(jdbc), new RoleContext(), h -> { }, () -> { }));
        try {
            onEdt(() -> { panel.search(); return null; }); await(() -> hasLabel(panel, "0 matches; 0 shown. No matching records."));
            onEdt(() -> { panel.search(); return null; }); await(() -> components(panel, JTable.class).get(0).getRowCount() == 1);
            onEdt(() -> { assertFalse(button(panel, "Select").isEnabled()); assertNull(panel.selected()); return null; });
        } finally { onEdt(() -> { panel.close(); return null; }); }
    }
    private void filter(AdvancedSearchPanel panel, String label, String value, String mode, String upper) {
        JLabel name = components(panel, JLabel.class).stream().filter(item -> label.equals(item.getText())).findFirst().orElseThrow();
        java.awt.Component[] row = name.getParent().getComponents();
        int index = java.util.Arrays.asList(row).indexOf(name);
        if (name.getLabelFor() instanceof JTextField text) text.setText(value);
        else ((JComboBox<?>) name.getLabelFor()).setSelectedItem(value);
        ((JComboBox<?>) row[index + 2]).setSelectedItem(mode); ((JTextField) row[index + 3]).setText(upper);
    }
    @Test void dialogCombinesOptionalInputsDateRangesAndEnumsAndRejectsReversedRangesBeforeJdbc() throws Exception {
        SearchDefinition def = SearchDefinition.entity(EntityType.SAMPLE);
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(0) : SearchJdbcStub.rows(def, List.of()));
        AdvancedSearchPanel panel = onEdt(() -> new AdvancedSearchPanel(def, new SearchService(jdbc), new RoleContext(), h -> { }, () -> { }));
        try {
            onEdt(() -> {
                filter(panel, "Sample ID (within species)", "7", "Exact", "");
                filter(panel, "Collection date (YYYY-MM-DD)", "2026-01-01", "Range", "2026-12-31");
                filter(panel, "DNA quality", "High", "Exact", ""); filter(panel, "Researcher Name", "Sana Khan", "Exact", "");
                panel.search(); return null;
            });
            await(() -> hasLabel(panel, "0 matches; 0 shown. No matching records."));
            assertEquals(List.of(7, java.sql.Date.valueOf("2026-01-01"), java.sql.Date.valueOf("2026-12-31"), "high", "sana khan"), jdbc.calls.get(0).parameters());
            int calls = jdbc.calls.size();
            onEdt(() -> { filter(panel, "Collection date (YYYY-MM-DD)", "2026-12-31", "Range", "2026-01-01"); panel.search(); return null; });
            assertEquals(calls, jdbc.calls.size());
            assertTrue(onEdt(() -> components(panel, JLabel.class).stream().anyMatch(label -> label.getText().contains("range start exceeds end"))));
        } finally { onEdt(() -> { panel.close(); return null; }); }
    }
    @Test void newerAdvancedFiltersCannotBeOverwrittenByAnOlderSlowSearch() throws Exception {
        SearchDefinition def = SearchDefinition.entity(EntityType.RESEARCHER);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> {
            int id = (Integer) values.get(0);
            if (sql.contains("COUNT(*)")) {
                if (id == 1) { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
                return SearchJdbcStub.count(1);
            }
            if (id == 1) completed.countDown();
            return SearchJdbcStub.rows(def, List.of(Map.of("researcherID", id)));
        });
        AdvancedSearchPanel panel = onEdt(() -> new AdvancedSearchPanel(def, new SearchService(jdbc), new RoleContext(), h -> { }, () -> { }));
        try {
            onEdt(() -> { filter(panel, "Researcher ID", "1", "Exact", ""); panel.search(); return null; }); assertTrue(started.await(3, TimeUnit.SECONDS));
            onEdt(() -> { filter(panel, "Researcher ID", "2", "Exact", ""); panel.search(); return null; });
            await(() -> components(panel, JTable.class).get(0).getRowCount() == 1);
            release.countDown(); assertTrue(completed.await(3, TimeUnit.SECONDS)); idleAfterWorkers();
            onEdt(() -> { assertEquals(2, components(panel, JTable.class).get(0).getValueAt(0, 0)); return null; });
        } finally { release.countDown(); onEdt(() -> { panel.close(); return null; }); }
    }
    @Test void requestGateSuppressesStaleSuccessFailureRevokedRolesAndClosedPickers() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);
        SearchRequests gate = new SearchRequests(); RoleContext roles = new RoleContext(); AtomicReference<String> shown = new AtomicReference<>();
        onEdt(() -> {
            gate.run(new JPanel(), roles, () -> { started.countDown(); release.await(5, TimeUnit.SECONDS); completed.countDown(); return "old"; }, shown::set, error -> fail(error));
            return null;
        });
        assertTrue(started.await(3, TimeUnit.SECONDS));
        onEdt(() -> { gate.run(new JPanel(), roles, () -> "new", shown::set, error -> fail(error)); return null; });
        await(() -> "new".equals(shown.get())); release.countDown(); assertTrue(completed.await(3, TimeUnit.SECONDS)); idleAfterWorkers();
        assertEquals("new", shown.get());
        CountDownLatch revokedRelease = new CountDownLatch(1), revokedComplete = new CountDownLatch(1);
        onEdt(() -> {
            gate.run(new JPanel(), roles, () -> { revokedRelease.await(5, TimeUnit.SECONDS); revokedComplete.countDown(); throw new IllegalStateException("old error"); },
                    v -> fail("Revoked request completed"), error -> fail("Revoked error displayed"));
            roles.setRole(UserRole.VIEWER); roles.setRole(UserRole.ADMIN); gate.close(); return null;
        });
        revokedRelease.countDown(); assertTrue(revokedComplete.await(3, TimeUnit.SECONDS)); idleAfterWorkers(); assertEquals("new", shown.get());
    }
    @Test void roleChangeDuringAdvancedSearchClearsResultsAndDiscardsCompletion() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);
        SearchDefinition def = SearchDefinition.entity(EntityType.RESEARCHER); RoleContext roles = new RoleContext();
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> {
            if (sql.contains("COUNT(*)")) { started.countDown(); release.await(5, TimeUnit.SECONDS); return SearchJdbcStub.count(1); }
            completed.countDown(); return SearchJdbcStub.rows(def, List.of(Map.of("researcherID", 1)));
        });
        AdvancedSearchPanel panel = onEdt(() -> new AdvancedSearchPanel(def, new SearchService(jdbc), roles, h -> fail("Unexpected select"), () -> { }));
        try {
            onEdt(() -> { panel.search(); return null; }); assertTrue(started.await(3, TimeUnit.SECONDS));
            onEdt(() -> { roles.setRole(UserRole.VIEWER); return null; }); release.countDown();
            assertTrue(completed.await(3, TimeUnit.SECONDS)); idleAfterWorkers();
            onEdt(() -> { assertEquals(0, components(panel, JTable.class).get(0).getRowCount()); assertFalse(button(panel, "Select").isEnabled()); return null; });
        } finally { release.countDown(); onEdt(() -> { panel.close(); return null; }); }
    }
    @Test void crudSearchSelectionRequeriesCompleteSampleAndContactKeysAndKeepsRoleRestrictions() throws Exception {
        for (EntityType type : List.of(EntityType.SAMPLE, EntityType.CONTACT, EntityType.BIOLOGIST, EntityType.GENETICIST, EntityType.RESEARCHER)) {
            SearchDefinition def = SearchDefinition.entity(type);
            Map<String, Object> values = type == EntityType.SAMPLE ? Map.of("speciesID", 31, "sampleID", 7)
                    : type == EntityType.CONTACT ? Map.of("researcherID", 2, "contact_type", "Work", "phone_number", "123") : Map.of("researcherID", 2);
            SearchJdbcStub jdbc = new SearchJdbcStub((sql, params) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(1) : SearchJdbcStub.rows(def, List.of(values)));
            RoleContext roles = new RoleContext(); roles.setRole(UserRole.VIEWER);
            CrudPanel panel = onEdt(() -> new CrudPanel(type, new EntityDao(jdbc), roles));
            onEdt(() -> { panel.selectSearchResult(new SearchResult.Hit(def, values)); return null; });
            await(() -> components(panel, JTable.class).get(0).getSelectedRow() >= 0);
            assertEquals(new ArrayList<>(new SearchResult.Hit(def, values).identity().values()), jdbc.calls.get(0).parameters());
            onEdt(() -> {
                assertFalse(button(panel, "Edit").isEnabled()); roles.setRole(UserRole.ADMIN); assertTrue(button(panel, "Edit").isEnabled());
                EntityTableModel model = (EntityTableModel) components(panel, JTable.class).get(0).getModel();
                assertEquals(values, new SearchResult.Hit(def, model.row(0).values()).identity()); return null;
            });
        }
    }
    @Test void researcherSelectorSearchesSqlAndAmbiguousAutocompleteDoesNotSelectOneName() throws Exception {
        SearchDefinition def = SearchDefinition.entity(EntityType.RESEARCHER);
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, values) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(2)
                : SearchJdbcStub.rows(def, List.of(Map.of("researcherID", 1, "full_name", "Sana Khan"), Map.of("researcherID", 2, "full_name", "Sana Khan"))));
        ResearchSelector selector = onEdt(() -> {
            ResearchSelector panel = new ResearchSelector("Researcher"); panel.configure(new SearchService(jdbc), def, new RoleContext());
            JTextField search = components(panel, JTextField.class).get(0); search.setText("SANA%_!"); search.postActionEvent(); return panel;
        });
        await(() -> components(selector, JComboBox.class).get(0).getItemCount() == 2);
        assertTrue(jdbc.calls.get(0).parameters().contains("%sana!%!_!!%"));
        onEdt(() -> { assertNull(selector.key()); components(selector, JComboBox.class).get(0).setSelectedIndex(1); assertEquals(2, selector.key()); return null; });
    }
    @Test void advancedSelectionDrivesEveryAnalyticalToolWithExactIdentities() throws Exception {
        RoleContext roles = new RoleContext();
        SearchJdbcStub traceJdbc = new SearchJdbcStub((sql, parameters) -> {
            assertEquals(List.of(32, 7), parameters);
            return new TableData(List.of("speciesID", "sampleID", "DNA_quality"), List.of(Arrays.asList(32, 7, null)));
        });
        SampleTraceabilityPanel trace = onEdt(() -> new SampleTraceabilityPanel(new ResearchDao(traceJdbc), roles));
        onEdt(() -> { trace.selectSearchResult(hit(EntityType.SAMPLE, Map.of("speciesID", 32, "sampleID", 7))); return null; });
        await(() -> trace.status.getText().startsWith("Database collection context for sample (32, 7)"));

        SearchJdbcStub timelineJdbc = new SearchJdbcStub((sql, parameters) -> {
            assertEquals(List.of(32), parameters); return new TableData(List.of("auditID", "speciesID"), List.of(List.of(9, 32), List.of(10, 32)));
        });
        ConservationTimelinePanel timeline = onEdt(() -> new ConservationTimelinePanel(new ResearchDao(timelineJdbc), roles));
        onEdt(() -> { timeline.selectSearchResult(new SearchResult.Hit(SearchDefinition.audit(), Map.of("auditID", 10, "speciesID", 32))); return null; });
        await(() -> !timeline.busy && components(timeline, JTable.class).get(0).getSelectedRow() == 1);

        SearchJdbcStub biodiversityJdbc = new SearchJdbcStub((sql, parameters) -> {
            if (sql.contains("HAVING")) { assertEquals(List.of(22, 3), parameters); return new TableData(List.of("speciesID", "sample_count"), List.of(List.of(32, 4))); }
            return new TableData(List.of("locationID", "location_name", "ocean_region"), List.of(List.of(22, "Station", "Ocean")));
        });
        BiodiversityPanel biodiversity = onEdt(() -> new BiodiversityPanel(new ResearchDao(biodiversityJdbc), roles));
        onEdt(() -> { components(biodiversity, JSpinner.class).get(0).setValue(3); biodiversity.selectSearchResult(hit(EntityType.LOCATION, Map.of("locationID", 22))); return null; });
        await(() -> !biodiversity.busy && biodiversity.status.getText().startsWith("1 represented species"));
        assertTrue(biodiversityJdbc.calls.stream().anyMatch(c -> c.sql().contains("HAVING COUNT(*) >= ?")));

        SearchJdbcStub qualityJdbc = new SearchJdbcStub((sql, parameters) -> {
            assertEquals(List.of("ALL", "ALL", 32, 7), parameters); assertTrue(sql.contains("AND speciesID = ? AND sampleID = ?"));
            return new TableData(List.of("speciesID", "sampleID", "reason"), List.of(List.of(32, 7, "Low"), List.of(32, 7, "Date")));
        });
        DataQualityPanel quality = onEdt(() -> new DataQualityPanel(new ResearchDao(qualityJdbc), roles, key -> assertEquals(new SampleKey(32, 7), key)));
        onEdt(() -> { quality.selectSearchResult(hit(EntityType.SAMPLE, Map.of("speciesID", 32, "sampleID", 7))); return null; });
        await(() -> !quality.busy && components(quality, JTable.class).get(0).getRowCount() == 2);
        onEdt(() -> { components(quality, JTable.class).get(0).setRowSelectionInterval(0, 0); roles.setRole(UserRole.VIEWER); assertFalse(button(quality, "Open selected sample editor").isEnabled()); return null; });

        SearchJdbcStub contributionJdbc = new SearchJdbcStub((sql, parameters) -> {
            if (sql.contains("DENSE_RANK")) {
                assertTrue(parameters.isEmpty());
                return new TableData(List.of("researcherID", "researcher", "specialization", "sample_count", "sample_count_rank"),
                        List.of(List.of(1, "Sana Khan", "Genetics", 5, 1), List.of(2, "Sana Khan", "Biology", 0, 2)));
            }
            assertEquals(List.of(2), parameters); return new TableData(List.of("speciesID", "sampleID"), List.of());
        });
        ContributionPanel contribution = onEdt(() -> new ContributionPanel(new ResearchDao(contributionJdbc), roles));
        onEdt(() -> { contribution.selectSearchResult(hit(EntityType.RESEARCHER, Map.of("researcherID", 2))); return null; });
        await(() -> !contribution.busy && contribution.status.getText().startsWith("This researcher has zero"));
        onEdt(() -> { JTable table = components(contribution, JTable.class).get(0); assertEquals(2, table.getRowCount()); assertEquals(2, table.getValueAt(table.getSelectedRow(), 0)); return null; });
    }
}
