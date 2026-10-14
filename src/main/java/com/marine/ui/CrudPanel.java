package com.marine.ui;

import com.marine.dao.EntityDao;
import com.marine.model.*;
import com.marine.model.RolePermissions.Operation;
import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrudPanel extends JPanel implements Refreshable {
    private final EntityType type;
    private final EntityDao dao;
    private final RoleContext roles;
    private final EntityTableModel model;
    private final JTable table;
    private final JLabel status = new JLabel("Select Refresh to load records.");
    private final JButton add = new JButton("Add"), edit = new JButton("Edit"), delete = new JButton("Delete"), refresh = new JButton("Refresh");
    private boolean busy;
    private SampleKey pendingSample;

    public CrudPanel(EntityType type, EntityDao dao) {
        this(type, dao, new RoleContext());
    }

    public CrudPanel(EntityType type, EntityDao dao, RoleContext roles) {
        super(new BorderLayout(12, 14));
        this.type = type; this.dao = dao; this.roles = roles;
        setBorder(UiStyle.padding()); setBackground(UiStyle.BACKGROUND);
        JPanel top = new JPanel(new BorderLayout(12, 12)); top.setOpaque(false);
        top.add(UiStyle.heading(type.title()), BorderLayout.NORTH);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); tools.setOpaque(false);
        JTextField search = new JTextField(22); search.setToolTipText("Filter all loaded columns");
        tools.add(new JLabel("Search")); tools.add(search);
        for (JButton button : new JButton[]{add, edit, delete, refresh}) tools.add(button);
        top.add(tools, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        model = new EntityTableModel(type); table = new JTable(model); UiStyle.configure(table); UiStyle.sizeColumns(table);
        TableRowSorter<EntityTableModel> sorter = new TableRowSorter<>(model); table.setRowSorter(sorter);
        TableFilter.attach(search, sorter);
        add(new JScrollPane(table), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        add.addActionListener(event -> openEditor(null));
        edit.addActionListener(event -> { EntityRow row = selected(); if (row != null) openEditor(row); });
        delete.addActionListener(event -> deleteSelected());
        refresh.addActionListener(event -> refresh());
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        roles.addRoleListener(event -> { pendingSample = null; updateButtons(); });
        updateButtons();
    }

    private EntityRow selected() {
        int index = table.getSelectedRow();
        return index < 0 ? null : model.row(table.convertRowIndexToModel(index));
    }
    private void updateButtons() {
        add.setEnabled(!busy && roles.allows(type, Operation.CREATE)); refresh.setEnabled(!busy);
        edit.setEnabled(!busy && selected() != null && roles.allows(type, Operation.UPDATE));
        delete.setEnabled(!busy && selected() != null && roles.allows(type, Operation.DELETE));
        for (JButton button : new JButton[]{add, edit, delete})
            button.setToolTipText(roles.allows(type, Operation.CREATE) ? null : "Read-only for the current role.");
    }
    private void busy(boolean value, String message) {
        busy = value; table.setEnabled(!value); status.setText(message); updateButtons();
        if (!value && pendingSample != null) {
            SampleKey key = pendingSample; pendingSample = null;
            long revision = roles.revision();
            SwingUtilities.invokeLater(() -> { if (revision == roles.revision()) editSample(key); });
        }
    }

    /** Research tool entry point: resolve the exact composite key, then reuse the guarded editor. */
    public void editSample(SampleKey key) {
        if (type != EntityType.SAMPLE || !roles.allows(type, Operation.UPDATE)) return;
        if (busy) { pendingSample = key; return; }
        long revision = roles.revision();
        busy(true, "Loading sample " + key + "…");
        UiTasks.run(this, () -> dao.sample(key), result -> {
            if (revision != roles.revision() || !roles.allows(type, Operation.UPDATE)) return;
            if (result.isEmpty()) status.setText("Sample no longer exists. Refresh the records.");
            else openEditor(result.get());
        }, () -> busy(false, "Ready."));
    }
    @Override public void refresh() {
        if (busy) return;
        busy(true, "Loading records…");
        UiTasks.run(this, () -> dao.list(type), rows -> {
            model.setRows(rows);
            status.setText(rows.size() + " records loaded. Search filters the loaded rows.");
        }, () -> busy(false, "Refresh finished. If it failed, the previous records remain displayed."));
    }

    private void openEditor(EntityRow original) {
        Operation operation = original == null ? Operation.CREATE : Operation.UPDATE;
        if (busy || !roles.allows(type, operation)) return;
        long revision = roles.revision();
        busy(true, "Loading reference choices…");
        UiTasks.run(this, () -> {
            Map<String, List<LookupChoice>> lookups = new LinkedHashMap<>();
            for (Field field : type.fields()) {
                if (field.reference() != null && !lookups.containsKey(field.reference()))
                    lookups.put(field.reference(), dao.choices(EntityType.valueOf(field.reference())));
            }
            return lookups;
        }, lookups -> {
            if (revision != roles.revision() || !roles.allows(type, operation)) return;
            EntityDialog dialog = new EntityDialog(SwingUtilities.getWindowAncestor(this), type, original, lookups, roles);
            dialog.setVisible(true);
            dialog.result().ifPresent(row -> save(original, row));
        }, () -> busy(false, "Ready."));
    }

    private void save(EntityRow original, EntityRow row) {
        Operation operation = original == null ? Operation.CREATE : Operation.UPDATE;
        if (busy || !roles.allows(type, operation)) return;
        long revision = roles.revision();
        busy(true, "Saving record…");
        UiTasks.run(this, () -> {
            if (revision != roles.revision() || !roles.allows(type, operation)) return false;
            if (original == null) dao.insert(row); else dao.update(original, row);
            return true;
        }, saved -> { if (saved) refresh(); }, () -> busy(false, "Ready."));
    }
    private void deleteSelected() {
        if (busy || !roles.allows(type, Operation.DELETE)) return;
        long revision = roles.revision();
        EntityRow row = selected(); if (row == null) return;
        int answer = JOptionPane.showConfirmDialog(this, "Delete this record?\n" + row.identity()
                + "\nReferenced records cannot be deleted.", "Confirm deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION || revision != roles.revision()
                || !roles.allows(type, Operation.DELETE)) return;
        busy(true, "Deleting record…");
        UiTasks.run(this, () -> {
            if (revision != roles.revision() || !roles.allows(type, Operation.DELETE)) return false;
            dao.delete(row); return true;
        }, deleted -> { if (deleted) refresh(); },
                () -> busy(false, "Ready."));
    }
}
