package com.marine.ui;

import com.marine.dao.ResearchDao;
import com.marine.search.*;
import com.marine.model.ResearchTool;
import javax.swing.*;
import java.awt.*;

public final class ConservationTimelinePanel extends ResearchPanel {
    private final ResearchDao dao;
    private final ResearchSelector species = new ResearchSelector("Species");
    private final JCheckBox newest = new JCheckBox("Newest first");
    private Integer pendingAudit;
    private final ResearchTable history = new ResearchTable("Recorded status changes");
    public ConservationTimelinePanel(ResearchDao dao) { this(dao, new RoleContext()); }
    public ConservationTimelinePanel(ResearchDao dao, RoleContext roles) {
        super(ResearchTool.TIMELINE.toString(), "History recorded by the existing AFTER UPDATE trigger; no earlier history is inferred.");
        this.dao = dao;
        species.configure(dao.search(), SearchDefinition.entity(com.marine.model.EntityType.SPECIES), roles);
        advanced(dao.search(), SearchDefinition.audit(), roles, this::selectSearchResult);
        JButton findSpecies = new JButton("Find species…"); controls.add(findSpecies);
        findSpecies.addActionListener(event -> AdvancedSearchDialog.choose(this, SearchDefinition.entity(com.marine.model.EntityType.SPECIES), dao.search(), roles).ifPresent(species::select));
        controls.add(species); controls.add(newest); newest.setOpaque(false);
        add(history, BorderLayout.CENTER); species.onChange(this::loadHistory);
        newest.addActionListener(event -> loadHistory());
    }
    void selectSearchResult(SearchResult.Hit hit) {
        pendingAudit = ((Number) hit.get("auditID")).intValue();
        species.select(new ResearchSelector.Choice(((Number) hit.get("speciesID")).intValue(), hit.label()));
    }
    @Override public void refresh() {
        history.clear();
        load(dao::species, data -> species.setChoices(data.rows().stream().map(row ->
                new ResearchSelector.Choice(((Number) row.get(0)).intValue(), row.get(0) + " — " + row.get(1) + " / " + row.get(2))).toList()));
    }
    private void loadHistory() {
        Integer id = (Integer) species.key(); boolean descending = newest.isSelected(); history.clear();
        load(() -> id == null ? null : dao.timeline(id, descending), data -> {
            if (data == null) { status.setText("No species match. Clear the search or add species through CRUD."); return; }
            history.showData(data);
            if (pendingAudit != null) { history.highlight(java.util.Map.of("auditID", pendingAudit)); pendingAudit = null; }
            status.setText(data.rows().isEmpty() ? "0 recorded changes. This species has no recorded status history."
                    : data.rows().size() + " recorded changes; timestamps come from MySQL audit records.");
        });
    }
}
