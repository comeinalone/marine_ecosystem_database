package com.marine.ui;

import com.marine.model.TableData;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

/** Read-only, typed sorting with literal-text filtering and detached JDBC rows. */
final class ResearchTable extends JPanel {
    private TableData data = new TableData(List.of(), List.of());
    private final AbstractTableModel model = new AbstractTableModel() {
        @Override public int getRowCount() { return data.rows().size(); }
        @Override public int getColumnCount() { return data.columns().size(); }
        @Override public String getColumnName(int column) { return data.columns().get(column); }
        @Override public Object getValueAt(int row, int column) { return data.rows().get(row).get(column); }
        @Override public Class<?> getColumnClass(int column) {
            for (List<Object> row : data.rows()) if (row.get(column) != null) return row.get(column).getClass();
            return Object.class;
        }
    };
    final JTable table = new JTable(model);
    ResearchTable(String title) {
        super(new BorderLayout(8, 8)); setOpaque(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); tools.setOpaque(false);
        tools.add(new JLabel(title)); tools.add(new JLabel("Filter"));
        JTextField search = new JTextField(18); tools.add(search); add(tools, BorderLayout.NORTH);
        UiStyle.configure(table);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) { super.setValue(value == null ? "Not recorded" : value); }
        });
        TableRowSorter<AbstractTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter); TableFilter.attach(search, sorter);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
    void showData(TableData next) { data = next; model.fireTableStructureChanged(); UiStyle.sizeColumns(table); }
    void clear() { showData(new TableData(List.of(), List.of())); }
    List<Object> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : data.rows().get(table.convertRowIndexToModel(row));
    }
}
