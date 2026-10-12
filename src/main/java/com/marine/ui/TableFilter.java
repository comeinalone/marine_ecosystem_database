package com.marine.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.regex.Pattern;

public final class TableFilter {
    private TableFilter() { }
    public static void attach(JTextField search, TableRowSorter<? extends TableModel> sorter) {
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() {
                String text = search.getText().trim();
                sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?iu)" + Pattern.quote(text)));
            }
            @Override public void insertUpdate(DocumentEvent event) { apply(); }
            @Override public void removeUpdate(DocumentEvent event) { apply(); }
            @Override public void changedUpdate(DocumentEvent event) { apply(); }
        });
    }
}
