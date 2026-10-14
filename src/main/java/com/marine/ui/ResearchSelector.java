package com.marine.ui;

import com.marine.model.*;
import com.marine.search.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/** Database-backed identity selector; ambiguous names never choose an arbitrary row. */
final class ResearchSelector extends JPanel {
    record Choice(Object key, String label) { @Override public String toString() { return label; } }
    private final JComboBox<Choice> combo = new JComboBox<>();
    private final JTextField search = new JTextField(13);
    private final SearchRequests requests = new SearchRequests();
    private final Timer timer;
    private boolean changing;
    private Runnable changed = () -> { };
    private SearchService service;
    private SearchDefinition definition;
    private RoleContext roles;
    private final JLabel status = new JLabel();
    ResearchSelector(String label) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0)); setOpaque(false);
        JLabel name = new JLabel(label); name.setLabelFor(combo); add(name); add(search); add(combo); add(status);
        search.setToolTipText("Search MySQL by recorded names or keys; choose one result.");
        combo.setPreferredSize(new Dimension(290, 28));
        combo.addActionListener(event -> { if (!changing) changed.run(); });
        timer = new Timer(300, event -> query()); timer.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void edited() { requests.invalidate(); timer.restart(); setChoices(List.of()); }
            public void insertUpdate(DocumentEvent e) { edited(); }
            public void removeUpdate(DocumentEvent e) { edited(); }
            public void changedUpdate(DocumentEvent e) { edited(); }
        });
        search.addActionListener(event -> { timer.stop(); query(); });
    }
    void configure(SearchService service, SearchDefinition definition, RoleContext roles) {
        this.service = service; this.definition = definition; this.roles = roles;
        roles.addRoleListener(event -> { requests.invalidate(); timer.stop(); });
    }
    void onChange(Runnable callback) { changed = callback; }
    Object key() { Choice choice = (Choice) combo.getSelectedItem(); return choice == null ? null : choice.key(); }
    void setChoices(List<Choice> next) {
        Object previous = key(); changing = true; combo.removeAllItems();
        next.forEach(combo::addItem); combo.setSelectedIndex(-1);
        if (next.size() == 1) combo.setSelectedIndex(0);
        for (int i = 0; i < combo.getItemCount(); i++) if (combo.getItemAt(i).key().equals(previous)) combo.setSelectedIndex(i);
        changing = false;
        status.setText(next.size() > 1 && key() == null ? "Choose a specific record" : ""); changed.run();
    }
    void select(SearchResult.Hit hit) {
        if (definition == null) return;
        Object key = definition.entity() == EntityType.SAMPLE ? hit.sampleKey() : hit.get(definition.keys().get(0));
        select(new Choice(key, hit.label()));
    }
    void select(Choice choice) {
        timer.stop(); requests.invalidate(); changing = true; combo.removeAllItems(); combo.addItem(choice); combo.setSelectedIndex(0);
        changing = false; status.setText(""); changed.run();
    }
    private void query() {
        if (service == null) return;
        SearchCriteria criteria;
        try { criteria = new SearchCriteria(Map.of(), search.getText(), true, null, false, 100, 0); }
        catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); return; }
        status.setText("Searching…");
        requests.run(this, roles, () -> service.search(definition, criteria), result -> {
            setChoices(result.hits().stream().map(hit -> new Choice(definition.entity() == EntityType.SAMPLE
                    ? hit.sampleKey() : hit.get(definition.keys().get(0)), hit.label())).toList());
            if (result.total() > result.hits().size()) status.setText(result.total() + " matches — use Advanced Search");
        }, error -> status.setText(Errors.message(error)));
    }
}
