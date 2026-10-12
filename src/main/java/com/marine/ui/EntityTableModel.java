package com.marine.ui;

import com.marine.model.*;
import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.List;

public final class EntityTableModel extends AbstractTableModel {
    private final EntityType type;
    private List<EntityRow> rows = List.of();
    public EntityTableModel(EntityType type) { this.type = type; }
    public void setRows(List<EntityRow> rows) { this.rows = List.copyOf(rows); fireTableDataChanged(); }
    public EntityRow row(int index) { return rows.get(index); }
    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return type.fields().size(); }
    @Override public String getColumnName(int column) { return type.fields().get(column).label(); }
    @Override public Object getValueAt(int row, int column) { return rows.get(row).get(type.fields().get(column).name()); }
    @Override public Class<?> getColumnClass(int column) {
        return switch (type.fields().get(column).kind()) {
            case INTEGER, REFERENCE -> Integer.class;
            case DATE -> LocalDate.class;
            default -> String.class;
        };
    }
}
