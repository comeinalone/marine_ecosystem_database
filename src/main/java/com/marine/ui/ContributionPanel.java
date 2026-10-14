package com.marine.ui;

import com.marine.dao.ResearchDao;
import com.marine.model.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class ContributionPanel extends ResearchPanel {
    private final ResearchDao dao;
    private final JComboBox<ResearchDao.ContributionSort> sort = new JComboBox<>(ResearchDao.ContributionSort.values());
    private final ResearchTable researchers = new ResearchTable("Researchers, including those with zero recorded samples");
    private final ResearchTable samples = new ResearchTable("Selected researcher: recorded sample collections");
    private final SampleBars bars = new SampleBars();
    private boolean populating;
    public ContributionPanel(ResearchDao dao) {
        super(ResearchTool.CONTRIBUTIONS.toString(), "Participation in recorded collections. Sample count ranks do not measure productivity or scientific impact.");
        this.dao = dao; controls.add(new JLabel("Sort by samples / species diversity / latest activity")); controls.add(sort);
        JPanel summary = new JPanel(new BorderLayout(8, 8)); summary.setOpaque(false);
        summary.add(researchers, BorderLayout.CENTER); summary.add(new JScrollPane(bars), BorderLayout.EAST);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, summary, samples); split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER); sort.addActionListener(event -> refresh());
        researchers.table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !populating) loadSamples();
        });
    }
    @Override public void refresh() {
        ResearchDao.ContributionSort selected = (ResearchDao.ContributionSort) sort.getSelectedItem();
        populating = true; researchers.clear(); samples.clear(); bars.setData(new TableData(List.of(), List.of())); populating = false;
        load(() -> dao.contributions(selected), data -> {
            populating = true; researchers.showData(data); bars.setData(data); populating = false;
            status.setText(data.rows().isEmpty() ? "No researchers recorded." : data.rows().size() + " researchers. Select a row to view collections.");
        });
    }
    private void loadSamples() {
        List<Object> row = researchers.selected(); samples.clear();
        Integer id = row == null ? null : ((Number) row.get(0)).intValue();
        load(() -> id == null ? null : dao.researcherSamples(id), data -> {
            if (data == null) { status.setText("Select a researcher to view recorded collections."); return; }
            samples.showData(data);
            status.setText(data.rows().isEmpty() ? "This researcher has zero recorded DNA samples." : data.rows().size() + " recorded samples for researcher " + id + ".");
        });
    }

    /** Standard Swing drawing, no chart dependency; every value comes from the SQL summary. */
    private static final class SampleBars extends JPanel {
        private TableData data = new TableData(List.of(), List.of());
        void setData(TableData data) {
            this.data = data; setPreferredSize(new Dimension(240, Math.max(180, 40 + data.rows().size() * 48)));
            revalidate(); repaint();
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); graphics.setColor(UiStyle.NAVY); graphics.drawString("Recorded samples by researcher ID", 8, 20);
            long max = data.rows().stream().mapToLong(row -> ((Number) row.get(3)).longValue()).max().orElse(0);
            int y = 42;
            for (List<Object> row : data.rows()) {
                long count = ((Number) row.get(3)).longValue();
                graphics.setColor(UiStyle.NAVY); graphics.drawString("ID " + row.get(0) + ": " + count, 8, y);
                graphics.setColor(UiStyle.TEAL);
                graphics.fillRect(8, y + 6, max == 0 ? 0 : (int) ((getWidth() - 25) * ((double) count / max)), 16); y += 48;
            }
        }
    }
}
