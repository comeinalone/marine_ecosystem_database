package com.marine.ui;

import com.marine.model.*;
import com.marine.search.*;
import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.List;

public final class EntityTableModel extends AbstractTableModel {
    private final List<SearchField> fields;
    private List<EntityRow> rows = List.of();
    public EntityTableModel(EntityType type) { this.fields = SearchDefinition.entity(type).fields(); }
    public void setRows(List<EntityRow> rows) { this.rows = List.copyOf(rows); fireTableDataChanged(); }
    public EntityRow row(int index) { return rows.get(index); }
    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return fields.size(); }
    @Override public String getColumnName(int column) { return fields.get(column).label(); }
    @Override public Object getValueAt(int row, int column) { return rows.get(row).get(fields.get(column).name()); }
    @Override public Class<?> getColumnClass(int column) {
        return switch (fields.get(column).kind()) {
            case INTEGER -> Integer.class;
            case DATE -> LocalDate.class;
            case TIMESTAMP -> java.sql.Timestamp.class;
            default -> String.class;
        };
    }
}
