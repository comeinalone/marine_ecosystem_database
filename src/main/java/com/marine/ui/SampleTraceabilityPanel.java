package com.marine.ui;

import com.marine.dao.ResearchDao;
import com.marine.model.*;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SampleTraceabilityPanel extends ResearchPanel {
    private final ResearchDao dao;
    private final ResearchSelector samples = new ResearchSelector("Sample (speciesID, sampleID)");
    private final JTextArea details = new JTextArea();
    public SampleTraceabilityPanel(ResearchDao dao) {
        super(ResearchTool.TRACEABILITY.toString(), "Stored collection context linked by foreign keys. Select both components of the sample identity.");
        this.dao = dao; controls.add(samples);
        details.setEditable(false); details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        details.setMargin(new Insets(16, 16, 16, 16)); add(new JScrollPane(details), BorderLayout.CENTER);
        samples.onChange(this::loadSample);
    }
    @Override public void refresh() {
        details.setText("");
        load(dao::samples, data -> samples.setChoices(data.rows().stream().map(row -> {
            SampleKey key = new SampleKey(((Number) row.get(0)).intValue(), ((Number) row.get(1)).intValue());
            return new ResearchSelector.Choice(key, key + " — " + row.get(2) + " / " + row.get(3));
        }).toList()));
    }
    private void loadSample() {
        SampleKey key = (SampleKey) samples.key(); details.setText("");
        load(() -> key == null ? null : dao.traceability(key), data -> {
            if (data == null || data.rows().isEmpty()) { status.setText("No matching sample. Clear the search or refresh recorded samples."); return; }
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < data.columns().size(); i++) values.put(data.columns().get(i), data.rows().get(0).get(i));
            StringBuilder text = new StringBuilder();
            section(text, values, "DNA SAMPLE", "speciesID", "sampleID", "collection_date", "gene_marker", "DNA_quality");
            section(text, values, "SPECIES", "speciesID", "common_name", "scientific_name", "conservation_status");
            section(text, values, "COLLECTING RESEARCHER", "researcherID", "first_name", "middle_name", "last_name", "researcher_specialization", "email_address", "date_of_birth");
            section(text, values, "RESEARCH VESSEL", "vesselID", "vessel_name", "vessel_capacity", "home_port");
            section(text, values, "SAMPLING LOCATION", "locationID", "location_name", "ocean_region");
            details.setText(text.toString()); details.setCaretPosition(0); status.setText("Database collection context for sample " + key + ". Read-only for all roles.");
        });
    }
    private static void section(StringBuilder text, Map<String, Object> values, String title, String... columns) {
        text.append(title).append('\n');
        for (String column : columns) text.append("  ").append(column).append(": ")
                .append(values.get(column) == null ? "Not recorded" : values.get(column)).append('\n');
        text.append('\n');
    }
}
