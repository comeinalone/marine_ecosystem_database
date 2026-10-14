package com.marine.ui;

import com.marine.search.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Shared, headless-testable dialog contents. Every search and suggestion uses MySQL. */
public final class AdvancedSearchPanel extends JPanel implements AutoCloseable {
    private record Input(SearchField field, JComponent value, JTextField upper, JComboBox<String> mode) { }
    private final SearchDefinition definition;
    private final SearchService service;
    private final RoleContext roles;
    private final SearchRequests requests = new SearchRequests();
    private final Map<String, Input> inputs = new LinkedHashMap<>();
    private final List<SearchAutocomplete> autocomplete = new ArrayList<>();
    private final JTextField term = new JTextField(20);
    private final JCheckBox partial = new JCheckBox("Include partial matches", true), descending = new JCheckBox("Descending");
    private final JComboBox<String> sort = new JComboBox<>();
    private final JSpinner limit = new JSpinner(new SpinnerNumberModel(100, 1, 500, 25));
    private final JLabel status = new JLabel("Enter any subset of filters, then Search. Select a specific result.");
    private final JButton searchButton = new JButton("Search");
    private final JButton select = new JButton("Select"), previous = new JButton("Previous"), next = new JButton("Next");
    private List<SearchResult.Hit> hits = List.of();
    private SearchCriteria active;
    private long total;
    private final AbstractTableModel model = new AbstractTableModel() {
        public int getRowCount() { return hits.size(); }
        public int getColumnCount() { return definition.fields().size(); }
        public String getColumnName(int column) { return definition.fields().get(column).label(); }
        public Object getValueAt(int row, int column) { return hits.get(row).get(definition.fields().get(column).name()); }
        public Class<?> getColumnClass(int column) {
            return switch (definition.fields().get(column).kind()) {
                case INTEGER -> Integer.class;
                case DATE -> java.time.LocalDate.class;
                case TIMESTAMP -> java.sql.Timestamp.class;
                default -> String.class;
            };
        }
    };
    private final JTable table;
    private final PropertyChangeListener roleListener;
    public AdvancedSearchPanel(SearchDefinition definition, SearchService service, RoleContext roles,
                               Consumer<SearchResult.Hit> accept, Runnable cancel) {
        super(new BorderLayout(8, 8)); this.definition = definition; this.service = service; this.roles = roles;
        table = new JTable(model);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel form = new JPanel(new GridBagLayout()); int row = 0;
        for (SearchField field : definition.fields()) {
            JComboBox<String> mode = new JComboBox<>(field.text()
                    ? (field.kind() == SearchField.Kind.CHOICE ? new String[]{"Exact", "Is NULL"} : new String[]{"Contains", "Exact", "Is NULL"})
                    : new String[]{"Exact", "Range", "Is NULL"});
            JComponent value;
            if (field.kind() == SearchField.Kind.CHOICE) {
                JComboBox<String> combo = new JComboBox<>(); combo.addItem(""); field.options().forEach(combo::addItem); value = combo;
            } else value = new JTextField(18);
            JTextField upper = new JTextField(12); upper.setEnabled(false);
            upper.setToolTipText("Inclusive range end; dates use YYYY-MM-DD");
            Input input = new Input(field, value, upper, mode); inputs.put(field.name(), input);
            GridBagConstraints c = new GridBagConstraints(); c.gridy = row++; c.insets = new Insets(3, 4, 3, 4); c.anchor = GridBagConstraints.WEST;
            JLabel label = new JLabel(field.label()); label.setLabelFor(value); c.gridx = 0; form.add(label, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(value, c);
            c.gridx = 2; c.weightx = 0; form.add(mode, c); c.gridx = 3; form.add(upper, c);
            mode.addActionListener(event -> {
                value.setEnabled(!"Is NULL".equals(mode.getSelectedItem())); upper.setEnabled("Range".equals(mode.getSelectedItem())); invalidateResults();
            });
            if (value instanceof JTextField text) {
                listen(text); listen(upper);
                if (field.reference() != null) autocomplete.add(new SearchAutocomplete(text, service,
                        SearchDefinition.entity(com.marine.model.EntityType.valueOf(field.reference())), roles, hit -> {
                    String nameColumn = switch (field.reference()) {
                        case "RESEARCHER" -> "full_name"; case "SPECIES" -> "common_name";
                        case "VESSEL" -> "vessel_name"; default -> "location_name";
                    };
                    text.setText(Objects.toString(hit.get(nameColumn), ""));
                    Input key = inputs.get(field.referenceKey());
                    if (key != null && key.value() instanceof JTextField id && id.getText().isBlank()) {
                        id.setText(hit.get(field.referenceKey()).toString()); key.mode().setSelectedItem("Exact");
                    }
                }));
            } else ((JComboBox<?>) value).addActionListener(event -> invalidateResults());
        }
        JScrollPane formScroll = new JScrollPane(form); formScroll.setPreferredSize(new Dimension(800, 225));
        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel quickLabel = new JLabel("Any name / text / primary key"); quickLabel.setLabelFor(term);
        quick.add(quickLabel); quick.add(term); quick.add(partial);
        top.add(quick, BorderLayout.NORTH); top.add(formScroll, BorderLayout.CENTER);
        JPanel ordering = new JPanel(new FlowLayout(FlowLayout.LEFT)); ordering.add(new JLabel("Sort"));
        for (SearchField field : definition.fields()) sort.addItem(field.label());
        ordering.add(sort); ordering.add(descending); ordering.add(new JLabel("Rows per page")); ordering.add(limit); top.add(ordering, BorderLayout.SOUTH);
        listen(term); partial.addActionListener(event -> invalidateResults());
        sort.addActionListener(event -> invalidateResults()); descending.addActionListener(event -> invalidateResults()); limit.addChangeListener(event -> invalidateResults());
        autocomplete.add(new SearchAutocomplete(term, service, definition, roles, hit -> {
            for (String key : definition.keys()) {
                Input input = inputs.get(key);
                if (input.value() instanceof JTextField text && text.getText().isBlank()) {
                    text.setText(hit.get(key).toString()); input.mode().setSelectedItem("Exact");
                } else if (input.value() instanceof JComboBox<?> combo && Objects.toString(combo.getSelectedItem(), "").isBlank()) {
                    combo.setSelectedItem(hit.get(key)); input.mode().setSelectedItem("Exact");
                }
            }
        }));
        UiStyle.configure(table); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> select.setEnabled(table.getSelectedRow() >= 0));
        Runnable choose = () -> { SearchResult.Hit hit = selected(); if (hit != null) accept.accept(hit); };
        select.addActionListener(event -> choose.run());
        table.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) choose.run(); } });
        table.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "selectResult");
        table.getActionMap().put("selectResult", new AbstractAction() { public void actionPerformed(ActionEvent event) { choose.run(); } });
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, new JScrollPane(table)); split.setResizeWeight(0.45); add(split, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clear = new JButton("Clear"), cancelButton = new JButton("Cancel");
        searchButton.addActionListener(event -> search()); clear.addActionListener(event -> clear()); cancelButton.addActionListener(event -> cancel.run());
        previous.addActionListener(event -> execute(active.page(Math.max(0, active.offset() - active.limit()))));
        next.addActionListener(event -> execute(active.page(active.offset() + active.limit())));
        for (JButton button : new JButton[]{searchButton, clear, previous, next, select, cancelButton}) buttons.add(button);
        bottom.add(buttons, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        term.addActionListener(event -> search());
        roleListener = event -> invalidateResults(); roles.addRoleListener(roleListener); invalidateResults();
    }
    private void listen(JTextField input) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { invalidateResults(); }
            public void removeUpdate(DocumentEvent event) { invalidateResults(); }
            public void changedUpdate(DocumentEvent event) { invalidateResults(); }
        });
    }
    private void invalidateResults() {
        requests.invalidate(); hits = List.of(); active = null; total = 0;
        model.fireTableDataChanged(); select.setEnabled(false); previous.setEnabled(false); next.setEnabled(false);
        status.setText("Filters changed. Search to load matching records.");
    }
    public void search() {
        try {
            Map<String, SearchCriteria.Filter> filters = new LinkedHashMap<>();
            for (Input input : inputs.values()) {
                String mode = (String) input.mode().getSelectedItem();
                if ("Is NULL".equals(mode)) { filters.put(input.field().name(), SearchCriteria.Filter.isNull()); continue; }
                String text = input.value() instanceof JTextField value ? value.getText() : Objects.toString(((JComboBox<?>) input.value()).getSelectedItem(), "");
                Object value = SearchCriteria.parse(input.field(), text);
                Object upper = "Range".equals(mode) ? SearchCriteria.parse(input.field(), input.upper().getText()) : null;
                if (value == null && upper == null) continue;
                filters.put(input.field().name(), switch (mode) {
                    case "Contains" -> SearchCriteria.Filter.contains(value.toString());
                    case "Range" -> SearchCriteria.Filter.range(value, upper);
                    default -> SearchCriteria.Filter.exact(value);
                });
            }
            execute(new SearchCriteria(filters, term.getText(), partial.isSelected(),
                    definition.fields().get(sort.getSelectedIndex()).name(), descending.isSelected(), (Integer) limit.getValue(), 0));
        } catch (IllegalArgumentException ex) { invalidateResults(); status.setText(ex.getMessage()); }
    }
    private void execute(SearchCriteria criteria) {
        service.plan(definition, criteria); // validate synchronously before starting a worker
        invalidateResults(); active = criteria; status.setText("Searching MySQL…");
        requests.run(this, roles, () -> service.search(definition, criteria), result -> {
            hits = result.hits(); total = result.total(); table.getRowSorter().setSortKeys(null); model.fireTableDataChanged(); UiStyle.sizeColumns(table);
            previous.setEnabled(criteria.offset() > 0); next.setEnabled(criteria.offset() + hits.size() < total);
            status.setText(total + " matches; " + hits.size() + " shown" + (hits.isEmpty() ? ". No matching records." : ". Select a specific row (Enter or double-click)."));
        }, error -> { invalidateResults(); status.setText(Errors.message(error)); });
    }
    JButton searchButton() { return searchButton; }
    public SearchResult.Hit selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : hits.get(table.convertRowIndexToModel(row));
    }
    private void clear() {
        term.setText(""); partial.setSelected(true); descending.setSelected(false); sort.setSelectedIndex(0);
        for (Input input : inputs.values()) {
            if (input.value() instanceof JTextField text) text.setText(""); else ((JComboBox<?>) input.value()).setSelectedIndex(0);
            input.upper().setText(""); input.mode().setSelectedIndex(0);
        }
        invalidateResults();
    }
    public void close() { requests.close(); autocomplete.forEach(SearchAutocomplete::close); roles.removeRoleListener(roleListener); }
}
