package com.marine.ui;

import com.marine.dao.ResearchDao;
import com.marine.model.*;
import com.marine.model.RolePermissions.Operation;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public final class DataQualityPanel extends ResearchPanel {
    private final ResearchDao dao;
    private final RoleContext roles;
    private final Consumer<SampleKey> editSample;
    private final JComboBox<ResearchDao.IssueCategory> category = new JComboBox<>(ResearchDao.IssueCategory.values());
    private final JButton edit = new JButton("Open selected sample editor");
    private final ResearchTable issues = new ResearchTable("Flagged records and reasons (a sample may have multiple issues)");
    public DataQualityPanel(ResearchDao dao, RoleContext roles, Consumer<SampleKey> editSample) {
        super(ResearchTool.QUALITY.toString(), "Review warnings manually. No automatic changes. Rules are documented in docs/research-tools.md.");
        this.dao = dao; this.roles = roles; this.editSample = editSample;
        controls.add(category); controls.add(edit); add(issues, BorderLayout.CENTER);
        category.addActionListener(event -> refresh());
        issues.table.getSelectionModel().addListSelectionListener(event -> stateChanged());
        roles.addRoleListener(event -> stateChanged());
        edit.addActionListener(event -> {
            if (busy || !roles.allows(EntityType.SAMPLE, Operation.UPDATE)) return;
            List<Object> row = issues.selected();
            if (row != null) editSample.accept(new SampleKey(((Number) row.get(0)).intValue(), ((Number) row.get(1)).intValue()));
        });
        stateChanged();
    }
    @Override protected void stateChanged() {
        edit.setEnabled(!busy && issues.selected() != null && roles.allows(EntityType.SAMPLE, Operation.UPDATE));
    }
    @Override public void refresh() {
        ResearchDao.IssueCategory selected = (ResearchDao.IssueCategory) category.getSelectedItem(); issues.clear();
        load(() -> dao.quality(selected), data -> {
            issues.showData(data); stateChanged();
            status.setText(data.rows().isEmpty() ? "No issues found for this category under the documented rules."
                    : data.rows().size() + " issues. Warnings can occur on valid records; constraint violations are separate.");
        });
    }
}
