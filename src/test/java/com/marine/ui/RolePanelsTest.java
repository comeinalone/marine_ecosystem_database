package com.marine.ui;

import com.marine.dao.EntityDao;
import com.marine.dao.ReportDao;
import com.marine.model.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static com.marine.ui.RoleUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RolePanelsTest {
    @Test void existingSelectionsAndAllResearcherTabsRespectImmediateRoleChanges() throws Exception {
        onEdt(() -> {
            RoleContext roles = new RoleContext();
            EntityDao dao = new EntityDao(() -> { throw new AssertionError("Role changes must not open JDBC connections"); });
            for (EntityType type : EntityType.values()) {
                CrudPanel panel = new CrudPanel(type, dao, roles);
                JTable table = components(panel, JTable.class).get(0);
                ((EntityTableModel) table.getModel()).setRows(List.of(new EntityRow(type, Map.of())));
                table.setRowSelectionInterval(0, 0);
                for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.RESEARCHER, UserRole.VIEWER, UserRole.ADMIN}) {
                    roles.setRole(role);
                    boolean editable = role == UserRole.ADMIN || (role == UserRole.RESEARCHER && type == EntityType.SAMPLE);
                    for (String action : new String[]{"Add", "Edit", "Delete"})
                        assertEquals(editable, button(panel, action).isEnabled(), role + " / " + type + " / " + action);
                    assertTrue(button(panel, "Refresh").isEnabled());
                    assertEquals(1, table.getSelectedRowCount());
                    assertFalse(table.isCellEditable(0, 0));
                }
            }
            ResearchersPanel researchers = new ResearchersPanel(dao, roles);
            assertEquals(4, components(researchers, CrudPanel.class).size());
            roles.setRole(UserRole.RESEARCHER);
            for (CrudPanel panel : components(researchers, CrudPanel.class)) assertFalse(button(panel, "Add").isEnabled());
            roles.setRole(UserRole.VIEWER);
            for (CrudPanel panel : components(researchers, CrudPanel.class)) assertFalse(button(panel, "Add").isEnabled());
            roles.setRole(UserRole.ADMIN);
            for (CrudPanel panel : components(researchers, CrudPanel.class)) assertTrue(button(panel, "Add").isEnabled());
            return null;
        });
    }

    @Test void invokingDisabledActionHandlersOrSaveDirectlyCannotReachTheDao() throws Exception {
        onEdt(() -> {
            RoleContext roles = new RoleContext();
            EntityDao dao = new EntityDao(() -> { throw new AssertionError("Unauthorized action reached JDBC"); });
            for (UserRole role : new UserRole[]{UserRole.RESEARCHER, UserRole.VIEWER}) {
                roles.setRole(role);
                for (EntityType type : EntityType.values()) {
                    if (role == UserRole.RESEARCHER && type == EntityType.SAMPLE) continue;
                    CrudPanel panel = new CrudPanel(type, dao, roles);
                    EntityRow row = new EntityRow(type, Map.of());
                    JTable table = components(panel, JTable.class).get(0);
                    ((EntityTableModel) table.getModel()).setRows(List.of(row));
                    table.setRowSelectionInterval(0, 0);
                    for (String action : new String[]{"Add", "Edit", "Delete"}) {
                        JButton button = button(panel, action);
                        for (var listener : button.getActionListeners())
                            listener.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, action));
                    }
                    invokeSave(panel, null, row);
                    invokeSave(panel, row, row);
                    assertTrue(button(panel, "Refresh").isEnabled(), "Denied actions must not start workers");
                }
            }
            return null;
        });
    }

    @Test void researcherCanSaveSamplesThroughTheExistingDao() throws Exception {
        JdbcStub jdbc = new JdbcStub();
        CrudPanel samples = onEdt(() -> {
            RoleContext roles = new RoleContext(); roles.setRole(UserRole.RESEARCHER);
            CrudPanel panel = new CrudPanel(EntityType.SAMPLE, new EntityDao(jdbc), roles);
            EntityRow sample = new EntityRow(EntityType.SAMPLE, Map.of("speciesID", 91, "sampleID", 7,
                    "gene_marker", "COI", "researcherID", 1, "vesselID", 2, "locationID", 3));
            invokeSave(panel, null, sample);
            return panel;
        });
        await(() -> jdbc.writes.get() == 1 && button(samples, "Refresh").isEnabled());
        assertEquals(1, jdbc.writes.get());
        assertFalse(jdbc.queries.isEmpty(), "Successful save should refresh records");
    }

    @Test void changingRolesDuringReferenceLoadingInvalidatesThePendingEditor() throws Exception {
        JdbcStub jdbc = new JdbcStub();
        jdbc.releaseFirstEntity = new CountDownLatch(1);
        RoleContext roles = new RoleContext();
        CrudPanel samples = onEdt(() -> {
            CrudPanel panel = new CrudPanel(EntityType.SAMPLE, new EntityDao(jdbc), roles);
            button(panel, "Add").doClick();
            return panel;
        });
        try {
            assertTrue(jdbc.firstEntityStarted.await(8, TimeUnit.SECONDS));
            onEdt(() -> {
                roles.setRole(UserRole.VIEWER);
                assertFalse(button(samples, "Add").isEnabled());
                // Even restoring permission must not resurrect a request from an earlier role.
                roles.setRole(UserRole.RESEARCHER);
                return null;
            });
        } finally { jdbc.releaseFirstEntity.countDown(); }
        await(() -> button(samples, "Refresh").isEnabled());
        onEdt(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof EntityDialog) {
                    boolean opened = window.isDisplayable();
                    window.dispose();
                    assertFalse(opened, "A stale reference request opened an editor");
                }
            }
            assertTrue(button(samples, "Add").isEnabled());
            return null;
        });
        assertEquals(0, jdbc.writes.get());
    }

    @Test void roleSpecificDashboardsUseDaoCountsAndRefreshAfterAnInFlightRoleChange() throws Exception {
        JdbcStub jdbc = new JdbcStub();
        jdbc.releaseFirstDashboard = new CountDownLatch(1);
        RoleContext roles = new RoleContext();
        DashboardPanel panel = onEdt(() -> {
            DashboardPanel dashboard = new DashboardPanel(new ReportDao(jdbc), page -> { }, roles);
            assertTrue(hasLabel(dashboard, "Administrator Dashboard"));
            assertTrue(button(dashboard, "Manage Fish Species").isEnabled());
            dashboard.refresh();
            return dashboard;
        });
        try {
            assertTrue(jdbc.firstDashboardStarted.await(8, TimeUnit.SECONDS));
            onEdt(() -> {
                roles.setRole(UserRole.RESEARCHER); panel.refresh();
                assertTrue(hasLabel(panel, "Researcher Dashboard"));
                assertTrue(button(panel, "Manage DNA Samples").isEnabled());
                assertTrue(button(panel, "View Fish Species").isEnabled());
                return null;
            });
        } finally { jdbc.releaseFirstDashboard.countDown(); }
        await(() -> hasLabel(panel, "Statistics loaded from the database.") && hasLabel(panel, "66"));
        assertEquals(2, jdbc.dashboardReads.get());
        onEdt(() -> {
            assertTrue(hasLabel(panel, "22")); assertTrue(hasLabel(panel, "44")); assertTrue(hasLabel(panel, "88"));
            roles.setRole(UserRole.VIEWER); panel.refresh();
            assertTrue(hasLabel(panel, "Viewer Dashboard"));
            assertTrue(button(panel, "View DNA Samples").isEnabled());
            assertTrue(button(panel, "Research Reports").isEnabled());
            assertFalse(components(panel, JButton.class).stream().anyMatch(button -> button.getText().startsWith("Manage ")));
            return null;
        });
        await(() -> hasLabel(panel, "Statistics loaded from the database.") && hasLabel(panel, "99"));
        assertEquals(0, jdbc.writes.get());
    }

    private static void invokeSave(CrudPanel panel, EntityRow original, EntityRow replacement) throws Exception {
        Method save = CrudPanel.class.getDeclaredMethod("save", EntityRow.class, EntityRow.class);
        save.setAccessible(true); save.invoke(panel, original, replacement);
    }
}
