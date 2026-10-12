package com.marine.ui;

import com.marine.dao.*;
import com.marine.db.ConnectionProvider;
import com.marine.model.EntityType;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, JPanel> panels = new LinkedHashMap<>();
    private final Map<String, JButton> navigation = new LinkedHashMap<>();

    public MainFrame(ConnectionProvider database) {
        super("Marine Ecosystem Management System");
        EntityDao entities = new EntityDao(database); ReportDao reports = new ReportDao(database);
        panels.put("Dashboard", new DashboardPanel(reports, this::navigate));
        panels.put("Fish Species", new CrudPanel(EntityType.SPECIES, entities));
        panels.put("Researchers", new ResearchersPanel(entities));
        panels.put("DNA Samples", new CrudPanel(EntityType.SAMPLE, entities));
        panels.put("Research Vessels", new CrudPanel(EntityType.VESSEL, entities));
        panels.put("Sampling Locations", new CrudPanel(EntityType.LOCATION, entities));
        panels.put("Reports", new ReportsPanel(reports));
        JPanel sidebar = new JPanel(); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(UiStyle.NAVY); sidebar.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
        sidebar.setPreferredSize(new Dimension(220, 0));
        JLabel brand = new JLabel("MARINE / RESEARCH"); brand.setForeground(Color.WHITE); brand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        sidebar.add(brand); sidebar.add(Box.createVerticalStrut(32));
        panels.forEach((name, panel) -> {
            pages.add(panel, name);
            JButton button = new JButton(name); button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            button.addActionListener(event -> navigate(name)); navigation.put(name, button);
            sidebar.add(button); sidebar.add(Box.createVerticalStrut(8));
        });
        sidebar.add(Box.createVerticalGlue());
        JLabel footer = new JLabel("DBMS mini-project • JDBC"); footer.setForeground(new Color(190, 210, 220)); sidebar.add(footer);
        setLayout(new BorderLayout()); add(sidebar, BorderLayout.WEST); add(pages, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1260, 800); setMinimumSize(new Dimension(1040, 640));
        setLocationRelativeTo(null);
    }

    public void navigate(String page) {
        JPanel panel = panels.get(page); if (panel == null) return;
        cards.show(pages, page);
        navigation.forEach((name, button) -> button.setEnabled(!name.equals(page)));
        ((Refreshable) panel).refresh();
    }
}
