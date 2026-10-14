package com.marine.ui;

import com.marine.dao.ResearchDao;
import com.marine.db.ConnectionProvider;
import com.marine.model.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static com.marine.ui.RoleUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchPanelsTest {
    @FunctionalInterface interface Reply { TableData query(String sql) throws Exception; }
    private ConnectionProvider jdbc(Reply reply) {
        return () -> {
            assertFalse(SwingUtilities.isEventDispatchThread(), "Research queries must run off the EDT");
            return proxy(Connection.class, (method, args) -> switch (method) {
                case "close" -> null;
                case "prepareStatement" -> {
                    String sql = (String) args[0];
                    yield proxy(PreparedStatement.class, (name, values) -> switch (name) {
                        case "setObject", "close" -> null;
                        case "executeQuery" -> result(reply.query(sql));
                        default -> throw new AssertionError("Unexpected JDBC operation: " + name);
                    });
                }
                default -> throw new AssertionError("Unexpected connection operation: " + method);
            });
        };
    }
    private ResultSet result(TableData data) {
        AtomicInteger row = new AtomicInteger(-1);
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> row.incrementAndGet() < data.rows().size();
            case "getObject" -> data.rows().get(row.get()).get((Integer) args[0] - 1);
            case "close" -> null;
            case "getMetaData" -> proxy(ResultSetMetaData.class, (name, values) -> switch (name) {
                case "getColumnCount" -> data.columns().size();
                case "getColumnLabel" -> data.columns().get((Integer) values[0] - 1);
                default -> throw new UnsupportedOperationException(name);
            });
            default -> throw new UnsupportedOperationException(method);
        });
    }
    @FunctionalInterface interface Call { Object invoke(String method, Object[] args) throws Exception; }
    private <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> call.invoke(method.getName(), args)));
    }

    @Test void allToolsHandleEmptyDatabaseResultsOffTheEdt() throws Exception {
        ResearchDao dao = new ResearchDao(jdbc(sql -> new TableData(List.of("id"), List.of())));
        List<ResearchPanel> panels = onEdt(() -> List.of(new ConservationTimelinePanel(dao), new SampleTraceabilityPanel(dao),
                new BiodiversityPanel(dao), new DataQualityPanel(dao, new RoleContext(), key -> fail("Empty result opened editor")),
                new ContributionPanel(dao)));
        for (ResearchPanel panel : panels) {
            onEdt(() -> { panel.refresh(); return null; });
            await(() -> !panel.busy && !panel.status.getText().startsWith("Loading"));
            onEdt(() -> {
                assertTrue(panel.refreshButton.isEnabled());
                for (JTable table : components(panel, JTable.class)) assertEquals(0, table.getRowCount());
                return null;
            });
        }
    }
    @Test void queryErrorsHaveInlineStateAndCanBeRetried() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        ResearchDao dao = new ResearchDao(jdbc(sql -> {
            if (reads.getAndIncrement() == 0) throw new SQLException("offline", "08001");
            return new TableData(List.of(), List.of());
        }));
        DataQualityPanel panel = onEdt(() -> new DataQualityPanel(dao, new RoleContext(), key -> { }));
        onEdt(() -> { panel.refresh(); return null; });
        await(() -> panel.status.getText().startsWith("Cannot connect to MySQL"));
        onEdt(() -> { panel.refreshButton.doClick(); return null; });
        await(() -> panel.status.getText().startsWith("No issues found"));
        assertEquals(2, reads.get());
    }
    @Test void qualityEditorRevokesAccessImmediatelyAndGuardsDirectHandlers() throws Exception {
        AtomicInteger opened = new AtomicInteger(); RoleContext roles = new RoleContext();
        onEdt(() -> {
            DataQualityPanel panel = new DataQualityPanel(new ResearchDao(() -> { throw new AssertionError("No query expected"); }), roles,
                    key -> { assertEquals(new SampleKey(301, 2), key); opened.incrementAndGet(); });
            ResearchTable table = components(panel, ResearchTable.class).get(0);
            table.showData(new TableData(List.of("speciesID", "sampleID"), List.of(List.of(301, 2))));
            table.table.setRowSelectionInterval(0, 0);
            JButton edit = button(panel, "Open selected sample editor");
            roles.setRole(UserRole.RESEARCHER); assertTrue(edit.isEnabled()); edit.doClick();
            roles.setRole(UserRole.VIEWER); assertFalse(edit.isEnabled());
            for (var listener : edit.getActionListeners()) listener.actionPerformed(new ActionEvent(edit, 0, "edit"));
            assertEquals(1, opened.get()); roles.setRole(UserRole.ADMIN); assertTrue(edit.isEnabled());
            return null;
        });
    }
    @Test void searchableSelectorRetainsRealKeysAndTreatsMetacharactersLiterally() throws Exception {
        onEdt(() -> {
            ResearchSelector selector = new ResearchSelector("Species");
            selector.setChoices(List.of(new ResearchSelector.Choice(1, "1 — Shark [rare]"), new ResearchSelector.Choice(2, "2 — Tuna")));
            JTextField search = components(selector, JTextField.class).get(0); search.setText("[rare]");
            assertEquals(1, selector.key()); search.setText("TUNA"); assertEquals(2, selector.key());
            search.setText("absent"); assertNull(selector.key()); search.setText(""); assertEquals(1, selector.key());
            return null;
        });
    }
    @Test void supersededTimelineRequestCannotOverwriteNewerSelection() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        ResearchDao dao = new ResearchDao(jdbc(sql -> {
            if (!sql.contains("Species_Status_Audit")) return new TableData(List.of("speciesID", "common_name", "scientific_name"), List.of(List.of(1, "Shark", "Sharkus")));
            if (sql.contains(" ASC")) { started.countDown(); assertTrue(release.await(8, TimeUnit.SECONDS)); }
            return new TableData(List.of("auditID"), List.of(List.of(sql.contains(" DESC") ? 2 : 1)));
        }));
        ConservationTimelinePanel panel = onEdt(() -> new ConservationTimelinePanel(dao));
        onEdt(() -> { panel.refresh(); return null; });
        try {
            assertTrue(started.await(8, TimeUnit.SECONDS));
            onEdt(() -> { components(panel, JCheckBox.class).get(0).doClick(); return null; });
            await(() -> !panel.busy && components(panel, JTable.class).get(0).getRowCount() == 1);
        } finally { release.countDown(); }
        // Queue behind worker completion to observe any stale update, rather than just the latest query.
        await(() -> panel.status.getText().startsWith("1 recorded changes"));
        onEdt(() -> { assertEquals(2, components(panel, JTable.class).get(0).getValueAt(0, 0)); return null; });
    }
    @Test void nullTraceabilityAttributesRenderWithoutFailing() throws Exception {
        ResearchDao dao = new ResearchDao(jdbc(sql -> sql.contains("WHERE d.speciesID")
                ? new TableData(List.of("speciesID", "sampleID", "DNA_quality"), List.of(Arrays.asList(301, 1, null)))
                : new TableData(List.of("speciesID", "sampleID", "common_name", "collection_date"), List.of(List.of(301, 1, "Shark", "2026-01-01")))));
        SampleTraceabilityPanel panel = onEdt(() -> new SampleTraceabilityPanel(dao));
        onEdt(() -> { panel.refresh(); return null; });
        await(() -> panel.status.getText().startsWith("Database collection context"));
        onEdt(() -> {
            JTextArea text = components(panel, JTextArea.class).stream().filter(area -> area.getText().contains("DNA_quality:")).findFirst().orElseThrow();
            assertFalse(text.isEditable()); assertTrue(text.getText().contains("DNA_quality: Not recorded"));
            assertTrue(text.getText().contains("SAMPLING LOCATION")); return null;
        });
    }

    @Test void viewerCannotUseTheSampleEditorEntryPoint() throws Exception {
        onEdt(() -> {
            RoleContext roles = new RoleContext(); roles.setRole(UserRole.VIEWER);
            CrudPanel samples = new CrudPanel(EntityType.SAMPLE, new com.marine.dao.EntityDao(() -> { throw new AssertionError("Viewer reached JDBC editor lookup"); }), roles);
            samples.editSample(new SampleKey(301, 1)); assertTrue(button(samples, "Refresh").isEnabled());
            return null;
        });
    }
}
