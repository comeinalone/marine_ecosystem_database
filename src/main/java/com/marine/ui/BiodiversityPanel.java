package com.marine.ui;

import com.marine.dao.ResearchDao;
import com.marine.model.ResearchTool;
import javax.swing.*;
import java.awt.*;

public final class BiodiversityPanel extends ResearchPanel {
    private final ResearchDao dao;
    private final ResearchSelector regions = new ResearchSelector("Sampling region");
    private final JSpinner minimum = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final ResearchTable comparison = new ResearchTable("Compare all recorded regions: sample and distinct-species totals");
    private final ResearchTable species = new ResearchTable("Species represented in the selected region");
    public BiodiversityPanel(ResearchDao dao) {
        super(ResearchTool.BIODIVERSITY.toString(), "Recorded sampling data; these counts are not estimates of actual ocean biodiversity.");
        this.dao = dao; controls.add(regions);
        JPanel threshold = new JPanel(new FlowLayout(FlowLayout.LEFT)); threshold.setOpaque(false);
        threshold.add(new JLabel("Minimum samples per species (HAVING)")); threshold.add(minimum); controls.add(threshold);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, comparison, species); split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER); regions.onChange(this::loadSpecies); minimum.addChangeListener(event -> loadSpecies());
    }
    @Override public void refresh() {
        comparison.clear(); species.clear();
        load(dao::regions, data -> {
            comparison.showData(data);
            regions.setChoices(data.rows().stream().map(row -> new ResearchSelector.Choice(((Number) row.get(0)).intValue(),
                    row.get(0) + " — " + row.get(1) + " / " + row.get(2))).toList());
        });
    }
    private void loadSpecies() {
        Integer id = (Integer) regions.key(); int min = ((Number) minimum.getValue()).intValue(); species.clear();
        load(() -> id == null ? null : dao.regionSpecies(id, min), data -> {
            if (data == null) { status.setText("No sampling region matches. Clear the search or refresh."); return; }
            species.showData(data);
            status.setText(data.rows().isEmpty() ? "No recorded species meet this filter in the selected region."
                    : data.rows().size() + " represented species meet the filter. Region totals above include all samples.");
        });
    }
}
