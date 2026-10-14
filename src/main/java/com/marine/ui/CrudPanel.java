package com.marine.ui;

import com.marine.dao.EntityDao;
import com.marine.model.*;
import com.marine.search.*;
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
    private boolean busy, searching;
    private final SearchRequests searches = new SearchRequests();
    private final JTextField search = new JTextField(22);
    private final JButton advanced = new JButton("Advanced Search"), previous = new JButton("Previous"), next = new JButton("Next");
    private final Timer searchTimer = new Timer(300, event -> loadSearch(0));
    private int offset;
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
        search.setToolTipText("Search MySQL by name, text or primary key; exact matches appear first.");
        tools.add(new JLabel("Search")); tools.add(search); tools.add(advanced); tools.add(refresh);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); actions.setOpaque(false);
        for (JButton button : new JButton[]{add, edit, delete, previous, next}) actions.add(button);
        JPanel toolRows = new JPanel(new GridLayout(0, 1, 4, 6)); toolRows.setOpaque(false);
        toolRows.add(tools); toolRows.add(actions); top.add(toolRows, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        model = new EntityTableModel(type); table = new JTable(model); UiStyle.configure(table); UiStyle.sizeColumns(table);
        TableRowSorter<EntityTableModel> sorter = new TableRowSorter<>(model); table.setRowSorter(sorter);
        searchTimer.setRepeats(false);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { searches.invalidate(); searching = false; previous.setEnabled(false); next.setEnabled(false); table.clearSelection(); updateButtons(); searchTimer.restart(); }
            public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
        search.addActionListener(event -> { searchTimer.stop(); loadSearch(0); });
        advanced.addActionListener(event -> {
            if (busy) return;
            AdvancedSearchDialog.choose(this, SearchDefinition.entity(type), dao.search(), roles).ifPresent(this::selectSearchResult);
        });
        previous.addActionListener(event -> loadSearch(Math.max(0, offset - 100)));
        next.addActionListener(event -> loadSearch(offset + 100));
        previous.setEnabled(false); next.setEnabled(false);
        add(new JScrollPane(table), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        add.addActionListener(event -> openEditor(null));
        edit.addActionListener(event -> { EntityRow row = selected(); if (row != null) openEditor(row); });
        delete.addActionListener(event -> deleteSelected());
        refresh.addActionListener(event -> refresh());
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        roles.addRoleListener(event -> { pendingSample = null; searchTimer.stop(); searches.invalidate(); searching = false; updateButtons(); });
        updateButtons();
    }

    private EntityRow selected() {
        int index = table.getSelectedRow();
        return index < 0 ? null : model.row(table.convertRowIndexToModel(index));
    }
    private void updateButtons() {
        add.setEnabled(!busy && !searching && roles.allows(type, Operation.CREATE)); refresh.setEnabled(!busy); advanced.setEnabled(!busy);
        edit.setEnabled(!busy && !searching && selected() != null && roles.allows(type, Operation.UPDATE));
        delete.setEnabled(!busy && !searching && selected() != null && roles.allows(type, Operation.DELETE));
        for (JButton button : new JButton[]{add, edit, delete})
            button.setToolTipText(roles.allows(type, Operation.CREATE) ? null : "Read-only for the current role.");
    }
    private void busy(boolean value, String message) {
        if (value) { searches.invalidate(); searchTimer.stop(); searching = false; }
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
    @Override public void refresh() { searchTimer.stop(); loadSearch(0); }

    private void loadSearch(int page) {
        if (busy) return;
        SearchCriteria criteria;
        try { criteria = new SearchCriteria(Map.of(), search.getText(), true, null, false, 100, page); }
        catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); return; }
        searching = true; previous.setEnabled(false); next.setEnabled(false); updateButtons();
        status.setText("Searching MySQL…");
        searches.run(this, roles, () -> dao.search().search(SearchDefinition.entity(type), criteria), result -> {
            searching = false; offset = page; table.getRowSorter().setSortKeys(null);
            model.setRows(result.hits().stream().map(SearchResult.Hit::entityRow).toList());
            previous.setEnabled(page > 0); next.setEnabled(page + result.hits().size() < result.total());
            status.setText(result.total() + " matches; " + result.hits().size() + " shown. Select a specific record."); updateButtons();
        }, error -> { searching = false; status.setText(Errors.message(error)); updateButtons(); });
    }

    /** Resolve by the complete primary key, independent of result order or duplicate names. */
    public void selectSearchResult(SearchResult.Hit hit) {
        if (hit.definition().entity() != type || busy) return;
        searchTimer.stop();
        hit.identity();
        searching = true; updateButtons();
        searches.run(this, roles, () -> dao.find(hit.entityRow()), result -> {
            searching = false; model.setRows(result.map(List::of).orElseGet(List::of));
            if (result.isPresent()) { table.setRowSelectionInterval(0, 0); table.scrollRectToVisible(table.getCellRect(0, 0, true)); }
            previous.setEnabled(false); next.setEnabled(false);
            status.setText(result.isEmpty() ? "The selected record no longer exists." : "Selected " + result.get().identity());
            updateButtons();
        }, error -> { searching = false; status.setText(Errors.message(error)); updateButtons(); });
    }

    private void openEditor(EntityRow original) {
        Operation operation = original == null ? Operation.CREATE : Operation.UPDATE;
        if (busy || searching || !roles.allows(type, operation)) return;
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
            EntityDialog dialog = new EntityDialog(SwingUtilities.getWindowAncestor(this), type, original, lookups, roles, dao.search());
            dialog.setVisible(true);
            dialog.result().ifPresent(row -> save(original, row));
        }, () -> busy(false, "Ready."));
    }

    private void save(EntityRow original, EntityRow row) {
        Operation operation = original == null ? Operation.CREATE : Operation.UPDATE;
        if (busy || searching || !roles.allows(type, operation)) return;
        long revision = roles.revision();
        busy(true, "Saving record…");
        UiTasks.run(this, () -> {
            if (revision != roles.revision() || !roles.allows(type, operation)) return false;
            if (original == null) dao.insert(row); else dao.update(original, row);
            return true;
        }, saved -> { if (saved) refresh(); }, () -> busy(false, "Ready."));
    }
    private void deleteSelected() {
        if (busy || searching || !roles.allows(type, Operation.DELETE)) return;
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
