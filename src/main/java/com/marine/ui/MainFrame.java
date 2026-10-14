package com.marine.ui;

import com.marine.dao.*;
import com.marine.db.ConnectionProvider;
import com.marine.model.EntityType;
import com.marine.model.UserRole;
import com.marine.model.ResearchTool;
import com.marine.model.SampleKey;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, JPanel> panels = new LinkedHashMap<>();
    private final Map<String, JButton> navigation = new LinkedHashMap<>();
    private final RoleContext roles = new RoleContext();
    private final JComboBox<UserRole> roleSelector = new JComboBox<>(UserRole.values());
    private final DashboardPanel dashboard;
    private String currentPage = "Dashboard";

    public MainFrame(ConnectionProvider database) {
        super("Marine Ecosystem Management System");
        EntityDao entities = new EntityDao(database); ReportDao reports = new ReportDao(database);
        dashboard = new DashboardPanel(reports, this::navigate, roles);
        panels.put("Dashboard", dashboard);
        panels.put("Fish Species", new CrudPanel(EntityType.SPECIES, entities, roles));
        panels.put("Researchers", new ResearchersPanel(entities, roles));
        panels.put("DNA Samples", new CrudPanel(EntityType.SAMPLE, entities, roles));
        panels.put("Research Vessels", new CrudPanel(EntityType.VESSEL, entities, roles));
        panels.put("Sampling Locations", new CrudPanel(EntityType.LOCATION, entities, roles));
        panels.put("Reports", new ReportsPanel(reports));
        ResearchDao research = new ResearchDao(database);
        panels.put(ResearchTool.TIMELINE.toString(), new ConservationTimelinePanel(research));
        panels.put(ResearchTool.TRACEABILITY.toString(), new SampleTraceabilityPanel(research));
        panels.put(ResearchTool.BIODIVERSITY.toString(), new BiodiversityPanel(research));
        panels.put(ResearchTool.QUALITY.toString(), new DataQualityPanel(research, roles, this::editSample));
        panels.put(ResearchTool.CONTRIBUTIONS.toString(), new ContributionPanel(research));
        JPanel sidebar = new JPanel(); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(UiStyle.NAVY); sidebar.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
        sidebar.setPreferredSize(new Dimension(290, 780));
        JLabel brand = new JLabel("MARINE / RESEARCH"); brand.setForeground(Color.WHITE); brand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        sidebar.add(brand); sidebar.add(Box.createVerticalStrut(32));
        panels.forEach((name, panel) -> {
            if (name.equals(ResearchTool.TIMELINE.toString())) {
                JLabel section = new JLabel("Research Tools"); section.setForeground(Color.WHITE);
                section.setBorder(BorderFactory.createEmptyBorder(12, 0, 10, 0)); sidebar.add(section);
            }
            pages.add(panel, name);
            JButton button = new JButton(name); button.setHorizontalAlignment(SwingConstants.LEFT);
            if (name.length() > 26) button.setFont(button.getFont().deriveFont(12f));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            button.addActionListener(event -> navigate(name)); navigation.put(name, button);
            sidebar.add(button); sidebar.add(Box.createVerticalStrut(8));
        });
        sidebar.add(Box.createVerticalGlue());
        JLabel footer = new JLabel("DBMS mini-project • JDBC"); footer.setForeground(new Color(190, 210, 220)); sidebar.add(footer);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        JLabel roleLabel = new JLabel("Current Role"); roleLabel.setLabelFor(roleSelector);
        toolbar.add(roleLabel); toolbar.add(roleSelector);
        roleSelector.addActionListener(event -> roles.setRole((UserRole) roleSelector.getSelectedItem()));
        roles.addRoleListener(event -> {
            if (!roles.canAccessPage(currentPage)) navigate("Dashboard");
            else {
                updateNavigation();
                if (!currentPage.equals("Dashboard")) ((Refreshable) panels.get(currentPage)).refresh();
            }
            dashboard.refresh();
        });
        setLayout(new BorderLayout()); add(toolbar, BorderLayout.NORTH);
        JScrollPane sidebarScroll = new JScrollPane(sidebar, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder()); sidebarScroll.setPreferredSize(new Dimension(300, 0));
        add(sidebarScroll, BorderLayout.WEST); add(pages, BorderLayout.CENTER);
        updateNavigation();
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1260, 800); setMinimumSize(new Dimension(1040, 640));
        setLocationRelativeTo(null);
    }

    private void editSample(SampleKey key) {
        if (!roles.allows(EntityType.SAMPLE, com.marine.model.RolePermissions.Operation.UPDATE)) return;
        showPage("DNA Samples", false);
        ((CrudPanel) panels.get("DNA Samples")).editSample(key);
    }

    public void navigate(String page) {
        showPage(page, true);
    }

    private void showPage(String page, boolean refresh) {
        JPanel panel = panels.get(page); if (panel == null || !roles.canAccessPage(page)) return;
        currentPage = page;
        cards.show(pages, page);
        updateNavigation();
        if (refresh) ((Refreshable) panel).refresh();
    }

    private void updateNavigation() {
        navigation.forEach((name, button) -> {
            boolean accessible = roles.canAccessPage(name);
            button.setVisible(accessible);
            button.setEnabled(accessible && !name.equals(currentPage));
        });
    }
}
