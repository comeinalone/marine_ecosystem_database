package com.marine.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/** Search database-backed names and IDs without allowing arbitrary editable keys. */
final class ResearchSelector extends JPanel {
    record Choice(Object key, String label) { @Override public String toString() { return label; } }
    private final JComboBox<Choice> combo = new JComboBox<>();
    private final JTextField search = new JTextField(13);
    private List<Choice> choices = List.of();
    private boolean changing;
    private Runnable changed = () -> { };
    ResearchSelector(String label) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0)); setOpaque(false);
        JLabel name = new JLabel(label); name.setLabelFor(combo); add(name); add(search); add(combo);
        search.setToolTipText("Search recorded names or identifiers");
        combo.setPreferredSize(new Dimension(290, 28));
        combo.addActionListener(event -> { if (!changing) changed.run(); });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
    }
    void onChange(Runnable callback) { changed = callback; }
    Object key() { Choice choice = (Choice) combo.getSelectedItem(); return choice == null ? null : choice.key(); }
    void setChoices(List<Choice> next) { choices = List.copyOf(next); filter(); }
    private void filter() {
        Object previous = key(); changing = true; combo.removeAllItems();
        String term = search.getText().strip().toLowerCase(Locale.ROOT);
        for (Choice choice : choices) if (choice.label().toLowerCase(Locale.ROOT).contains(term)) combo.addItem(choice);
        for (int i = 0; i < combo.getItemCount(); i++)
            if (combo.getItemAt(i).key().equals(previous)) combo.setSelectedIndex(i);
        changing = false; changed.run();
    }
}
