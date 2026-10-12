package com.marine.ui;

import com.marine.dao.ReportDao;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class DashboardPanel extends JPanel implements Refreshable {
    private final ReportDao dao;
    private final Map<String, JLabel> values = new LinkedHashMap<>();
    private final JLabel status = new JLabel("Connect to MySQL to load statistics.");
    private final JButton refresh = new JButton("Refresh statistics");
    private boolean busy;

    public DashboardPanel(ReportDao dao, Consumer<String> navigate) {
        super(new BorderLayout(16, 24)); this.dao = dao;
        setBorder(UiStyle.padding()); setBackground(UiStyle.BACKGROUND);
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        header.add(UiStyle.heading("Marine Research Overview"), BorderLayout.NORTH);
        JLabel subtitle = new JLabel("Species, field collections and the people behind marine research.");
        subtitle.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        JPanel body = new JPanel(); body.setOpaque(false); body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JPanel cards = new JPanel(new GridLayout(2, 2, 16, 16)); cards.setOpaque(false);
        String[][] metrics = {{"researchers", "Researchers"}, {"species", "Fish species"},
                {"samples", "DNA samples"}, {"endangered", "Endangered / critically endangered"}};
        for (String[] metric : metrics) {
            JPanel card = new JPanel(new BorderLayout(8, 12)); card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(218, 227, 233)), UiStyle.padding()));
            card.add(new JLabel(metric[1]), BorderLayout.NORTH);
            JLabel value = new JLabel("—"); value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38)); value.setForeground(UiStyle.TEAL);
            values.put(metric[0], value); card.add(value, BorderLayout.CENTER); cards.add(card);
        }
        body.add(cards); body.add(Box.createVerticalStrut(24));
        JPanel shortcuts = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); shortcuts.setOpaque(false);
        for (String page : new String[]{"Fish Species", "DNA Samples", "Reports"}) {
            JButton button = new JButton("Open " + page); button.addActionListener(event -> navigate.accept(page)); shortcuts.add(button);
        }
        shortcuts.add(refresh); refresh.addActionListener(event -> refresh());
        body.add(shortcuts); body.add(Box.createVerticalGlue()); add(body, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
    }

    @Override public void refresh() {
        if (busy) return;
        busy = true; refresh.setEnabled(false); status.setText("Loading statistics…");
        UiTasks.run(this, dao::dashboard, counts -> {
            counts.forEach((key, value) -> values.get(key).setText(String.valueOf(value)));
            status.setText("Statistics loaded from the database.");
        }, () -> {
            busy = false; refresh.setEnabled(true);
            if (status.getText().equals("Loading statistics…")) status.setText("Statistics unavailable. Check database setup and refresh to retry.");
        });
    }
}
