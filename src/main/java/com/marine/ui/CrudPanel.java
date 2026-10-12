package com.marine.ui;

import com.marine.dao.EntityDao;
import com.marine.model.*;
import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrudPanel extends JPanel implements Refreshable {
    private final EntityType type;
    private final EntityDao dao;
    private final EntityTableModel model;
    private final JTable table;
    private final JLabel status = new JLabel("Select Refresh to load records.");
    private final JButton add = new JButton("Add"), edit = new JButton("Edit"), delete = new JButton("Delete"), refresh = new JButton("Refresh");
    private boolean busy;

    public CrudPanel(EntityType type, EntityDao dao) {
        super(new BorderLayout(12, 14));
        this.type = type; this.dao = dao;
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
        updateButtons();
    }

    private EntityRow selected() {
        int index = table.getSelectedRow();
        return index < 0 ? null : model.row(table.convertRowIndexToModel(index));
    }
    private void updateButtons() {
        add.setEnabled(!busy); refresh.setEnabled(!busy);
        edit.setEnabled(!busy && selected() != null); delete.setEnabled(!busy && selected() != null);
    }
    private void busy(boolean value, String message) {
        busy = value; table.setEnabled(!value); status.setText(message); updateButtons();
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
        busy(true, "Loading reference choices…");
        UiTasks.run(this, () -> {
            Map<String, List<LookupChoice>> lookups = new LinkedHashMap<>();
            for (Field field : type.fields()) {
                if (field.reference() != null && !lookups.containsKey(field.reference()))
                    lookups.put(field.reference(), dao.choices(EntityType.valueOf(field.reference())));
            }
            return lookups;
        }, lookups -> {
            EntityDialog dialog = new EntityDialog(SwingUtilities.getWindowAncestor(this), type, original, lookups);
            dialog.setVisible(true);
            dialog.result().ifPresent(row -> save(original, row));
        }, () -> busy(false, "Ready."));
    }

    private void save(EntityRow original, EntityRow row) {
        busy(true, "Saving record…");
        UiTasks.run(this, () -> {
            if (original == null) dao.insert(row); else dao.update(original, row);
            return null;
        }, ignored -> refresh(), () -> busy(false, "Ready."));
    }
    private void deleteSelected() {
        EntityRow row = selected(); if (row == null) return;
        int answer = JOptionPane.showConfirmDialog(this, "Delete this record?\n" + row.identity()
                + "\nReferenced records cannot be deleted.", "Confirm deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        busy(true, "Deleting record…");
        UiTasks.run(this, () -> { dao.delete(row); return null; }, ignored -> refresh(),
                () -> busy(false, "Ready."));
    }
}
