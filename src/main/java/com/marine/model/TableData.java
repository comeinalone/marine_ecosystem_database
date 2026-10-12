package com.marine.model;

import java.util.List;

/** Detached JDBC result, safe to hand from a worker to the Swing event thread. */
public record TableData(List<String> columns, List<List<Object>> rows) {
    public TableData {
        columns = List.copyOf(columns);
        rows = rows.stream().map(row -> java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(row))).toList();
    }
}
