package com.marine.ui;

import com.marine.dao.ReportDao;
import com.marine.model.EntityType;
import com.marine.model.ResearchTool;
import com.marine.model.RolePermissions.Operation;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class DashboardPanel extends JPanel implements Refreshable {
    private final ReportDao dao;
    private final RoleContext roles;
    private final Consumer<String> navigate;
    private final Map<String, JLabel> values = new LinkedHashMap<>();
    private final Map<String, JPanel> metricCards = new LinkedHashMap<>();
    private final JLabel heading = UiStyle.heading("");
    private final JLabel subtitle = new JLabel();
    private final JPanel cards = new JPanel(new GridLayout(2, 2, 16, 16));
    private final JPanel shortcuts = new JPanel(new GridLayout(0, 3, 10, 10));
    private final JLabel status = new JLabel("Connect to MySQL to load statistics.");
    private final JButton refresh = new JButton("Refresh statistics");
    private boolean busy;
    private boolean refreshRequested;

    public DashboardPanel(ReportDao dao, Consumer<String> navigate) {
        this(dao, navigate, new RoleContext());
    }

    public DashboardPanel(ReportDao dao, Consumer<String> navigate, RoleContext roles) {
        super(new BorderLayout(16, 24)); this.dao = dao; this.navigate = navigate; this.roles = roles;
        setBorder(UiStyle.padding()); setBackground(UiStyle.BACKGROUND);
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        header.add(heading, BorderLayout.NORTH);
        subtitle.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        JPanel body = new JPanel(); body.setOpaque(false); body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        cards.setOpaque(false);
        String[][] metrics = {{"researchers", "Researchers"}, {"species", "Fish species"},
                {"samples", "DNA samples"}, {"endangered", "Endangered / critically endangered"}};
        for (String[] metric : metrics) {
            JPanel card = new JPanel(new BorderLayout(8, 12)); card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(218, 227, 233)), UiStyle.padding()));
            card.add(new JLabel(metric[1]), BorderLayout.NORTH);
            JLabel value = new JLabel("—"); value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38)); value.setForeground(UiStyle.TEAL);
            values.put(metric[0], value); card.add(value, BorderLayout.CENTER); metricCards.put(metric[0], card);
        }
        body.add(cards); body.add(Box.createVerticalStrut(24));
        shortcuts.setOpaque(false);
        refresh.addActionListener(event -> refresh());
        JPanel refreshTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10)); refreshTools.setOpaque(false);
        refreshTools.add(refresh);
        body.add(shortcuts); body.add(refreshTools); body.add(Box.createVerticalGlue());
        add(body, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        roles.addRoleListener(event -> updateRole());
        updateRole();
    }

    private void updateRole() {
        heading.setText(roles.role() + " Dashboard");
        subtitle.setText(switch (roles.role()) {
            case ADMIN -> "Manage marine research records, collections and conservation reporting.";
            case RESEARCHER -> "Manage DNA samples and explore species, field resources and research reports.";
            case VIEWER -> "Browse marine research records and conservation reports with read-only access.";
        });
        String[] metrics = switch (roles.role()) {
            case ADMIN -> new String[]{"researchers", "species", "samples", "endangered"};
            case RESEARCHER -> new String[]{"samples", "species", "researchers", "endangered"};
            case VIEWER -> new String[]{"species", "endangered", "researchers", "samples"};
        };
        cards.removeAll();
        for (String metric : metrics) cards.add(metricCards.get(metric));
        shortcuts.removeAll();
        EntityType[] entities = {EntityType.SAMPLE, EntityType.SPECIES, EntityType.RESEARCHER, EntityType.VESSEL, EntityType.LOCATION};
        for (EntityType entity : entities) {
            String page = entity.title();
            String verb = roles.allows(entity, Operation.CREATE) ? "Manage " : "View ";
            addShortcut(verb + page, page);
        }
        addShortcut("Research Reports", "Reports");
        for (ResearchTool tool : ResearchTool.values()) addShortcut(tool.toString(), tool.toString());
        revalidate(); repaint();
    }

    private void addShortcut(String label, String page) {
        if (!roles.canAccessPage(page)) return;
        JButton button = new JButton(label);
        button.addActionListener(event -> { if (roles.canAccessPage(page)) navigate.accept(page); });
        shortcuts.add(button);
    }

    @Override public void refresh() {
        if (busy) { refreshRequested = true; return; }
        long revision = roles.revision();
        busy = true; refresh.setEnabled(false); status.setText("Loading statistics…");
        UiTasks.run(this, dao::dashboard, counts -> {
            if (revision != roles.revision()) return;
            counts.forEach((key, value) -> values.get(key).setText(String.valueOf(value)));
            status.setText("Statistics loaded from the database.");
        }, () -> {
            busy = false; refresh.setEnabled(true);
            if (status.getText().equals("Loading statistics…")) status.setText("Statistics unavailable. Check database setup and refresh to retry.");
            if (refreshRequested) {
                refreshRequested = false;
                SwingUtilities.invokeLater(this::refresh);
            }
        });
    }
}
