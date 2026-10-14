package com.marine.ui;

import com.marine.dao.ReportDao;
import com.marine.model.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.util.Map;
import static com.marine.ui.RoleUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class RoleWindowTest {
    @Test void dropdownRetainsAccessiblePageRefreshesStatisticsAndNeverWrites() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Top-level Swing windows require a display");
        JdbcStub jdbc = new JdbcStub();
        MainFrame frame = onEdt(() -> new MainFrame(jdbc));
        try {
            DashboardPanel dashboard = onEdt(() -> components(frame, DashboardPanel.class).get(0));
            JComboBox<?> selector = onEdt(() -> components(frame, JComboBox.class).stream()
                    .filter(combo -> combo.getSelectedItem() instanceof UserRole).findFirst().orElseThrow());
            assertEquals(0, jdbc.dashboardReads.get(), "Construction should not access MySQL");
            onEdt(() -> {
                assertEquals(UserRole.ADMIN, selector.getSelectedItem());
                assertTrue(hasLabel(frame, "Current Role"));
                frame.navigate("DNA Samples");
                return null;
            });
            for (UserRole role : new UserRole[]{UserRole.RESEARCHER, UserRole.VIEWER, UserRole.ADMIN}) {
                int previousReads = jdbc.dashboardReads.get();
                onEdt(() -> {
                    selector.setSelectedItem(role);
                    assertTrue(hasLabel(dashboard, role + " Dashboard"));
                    assertFalse(dashboard.isVisible(), "Accessible DNA Samples page should remain selected");
                    assertFalse(button(frame, "DNA Samples").isEnabled());
                    assertTrue(button(frame, "Reports").isEnabled());
                    return null;
                });
                await(() -> jdbc.dashboardReads.get() > previousReads
                        && hasLabel(dashboard, "Statistics loaded from the database."));
            }
            onEdt(() -> { frame.navigate("Reports"); return null; });
            ReportsPanel reports = onEdt(() -> components(frame, ReportsPanel.class).get(0));
            await(() -> button(reports, "Refresh").isEnabled());
            onEdt(() -> {
                JComboBox<?> reportSelector = components(reports, JComboBox.class).get(0);
                assertEquals(5, reportSelector.getItemCount());
                reportSelector.setSelectedItem(ReportDao.Report.AUDIT);
                return null;
            });
            await(() -> button(reports, "Refresh").isEnabled());
            for (UserRole role : new UserRole[]{UserRole.RESEARCHER, UserRole.VIEWER}) {
                int previousReads = jdbc.dashboardReads.get();
                onEdt(() -> {
                    selector.setSelectedItem(role);
                    assertTrue(reports.isVisible(), "Reports remain accessible under each role");
                    JTable table = components(reports, JTable.class).get(0);
                    assertFalse(table.getModel().isCellEditable(0, 0));
                    return null;
                });
                await(() -> button(reports, "Refresh").isEnabled() && jdbc.dashboardReads.get() > previousReads
                        && hasLabel(dashboard, "Statistics loaded from the database."));
            }
            assertTrue(jdbc.queries.stream().anyMatch(sql -> sql.contains("Species_Status_Audit")));
            assertEquals(3, jdbc.queries.stream().filter(sql -> sql.contains("Species_Status_Audit")).count());
            assertEquals(0, jdbc.writes.get());
            assertTrue(jdbc.queries.stream().allMatch(sql -> sql.startsWith("SELECT ")));
        } finally { onEdt(() -> { frame.dispose(); return null; }); }
    }

    @Test void defaultSaveAndDirectDialogActionsRespectRoleRevocation() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Dialogs require a display");
        onEdt(() -> {
            RoleContext roles = new RoleContext();
            EntityRow species = new EntityRow(EntityType.SPECIES, Map.of("speciesID", 91,
                    "common_name", "Test species", "scientific_name", "Testus species",
                    "conservation_status", "Least Concern"));
            EntityDialog dialog = new EntityDialog(null, EntityType.SPECIES, species, Map.of(), roles);
            try {
                JButton save = dialog.getRootPane().getDefaultButton();
                assertTrue(save.isEnabled());
                save.doClick();
                assertTrue(dialog.result().isPresent());
                roles.setRole(UserRole.VIEWER);
                assertTrue(dialog.result().isEmpty(), "A result saved under an earlier role cannot be used");
                for (var listener : save.getActionListeners())
                    listener.actionPerformed(new ActionEvent(save, ActionEvent.ACTION_PERFORMED, "Save"));
                assertTrue(dialog.result().isEmpty());
            } finally { dialog.dispose(); }

            roles.setRole(UserRole.ADMIN);
            EntityDialog pending = new EntityDialog(null, EntityType.SPECIES, species, Map.of(), roles);
            pending.pack();
            assertTrue(pending.isDisplayable());
            roles.setRole(UserRole.RESEARCHER);
            assertFalse(pending.isDisplayable(), "Role change closes a pending editor");
            assertTrue(pending.result().isEmpty());
            EntityDialog denied = new EntityDialog(null, EntityType.SPECIES, species, Map.of(), roles);
            try {
                assertFalse(denied.getRootPane().getDefaultButton().isEnabled());
                assertTrue(denied.result().isEmpty());
            } finally { denied.dispose(); }
            return null;
        });
    }

    @Test void researchNavigationAndRefreshRemainAvailableForEveryRole() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Top-level Swing windows require a display");
        JdbcStub jdbc = new JdbcStub(); MainFrame frame = onEdt(() -> new MainFrame(jdbc));
        try {
            JComboBox<?> selector = onEdt(() -> components(frame, JComboBox.class).stream()
                    .filter(combo -> combo.getSelectedItem() instanceof UserRole).findFirst().orElseThrow());
            for (UserRole role : UserRole.values()) for (ResearchTool tool : ResearchTool.values()) {
                ResearchPanel panel = onEdt(() -> {
                    selector.setSelectedItem(role); frame.navigate(tool.toString());
                    assertTrue(hasLabel(frame, "Research Tools"));
                    assertFalse(button(frame, tool.toString()).isEnabled());
                    return components(frame, ResearchPanel.class).stream().filter(java.awt.Component::isVisible).findFirst().orElseThrow();
                });
                await(() -> !panel.busy && !panel.status.getText().startsWith("Loading"));
                onEdt(() -> {
                    for (JTable table : components(panel, JTable.class)) assertFalse(table.getModel().isCellEditable(0, 0));
                    if (role == UserRole.VIEWER && tool == ResearchTool.QUALITY)
                        assertFalse(button(panel, "Open selected sample editor").isEnabled());
                    return null;
                });
            }
            assertEquals(0, jdbc.writes.get());
        } finally { onEdt(() -> { frame.dispose(); return null; }); }
    }
}
