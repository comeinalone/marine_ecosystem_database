package com.marine.dao;

import com.marine.db.ConnectionProvider;
import com.marine.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Small reusable CRUD DAO. All values are bound through PreparedStatement. */
public final class EntityDao {
    private final ConnectionProvider connections;
    public EntityDao(ConnectionProvider connections) { this.connections = connections; }

    public List<EntityRow> list(EntityType type) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(SqlStatements.select(type));
             ResultSet result = statement.executeQuery()) {
            List<EntityRow> rows = new ArrayList<>();
            while (result.next()) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Field field : type.fields()) {
                    Object value = result.getObject(field.name());
                    if (value instanceof java.sql.Date date) value = date.toLocalDate();
                    values.put(field.name(), value);
                }
                rows.add(new EntityRow(type, values));
            }
            return rows;
        }
    }

    public List<LookupChoice> choices(EntityType type) throws SQLException {
        if (type.keys().size() != 1) throw new IllegalArgumentException("Lookup needs one key");
        List<LookupChoice> choices = new ArrayList<>();
        for (EntityRow row : list(type)) {
            String label = String.valueOf(row.get(type.displayColumn()));
            if (type == EntityType.RESEARCHER) label += " " + row.get("last_name");
            choices.add(new LookupChoice(((Number) row.get(type.keys().get(0).name())).intValue(), label));
        }
        return choices;
    }

    public void insert(EntityRow row) throws SQLException {
        execute(SqlStatements.insert(row.type()), row.type().fields(), row, List.of(), null);
    }

    public void update(EntityRow original, EntityRow replacement) throws SQLException {
        if (original.type() != replacement.type()) throw new IllegalArgumentException("Entity types differ");
        execute(SqlStatements.update(original.type()), original.type().editableFields(), replacement,
                original.type().keys(), original);
    }

    public void delete(EntityRow original) throws SQLException {
        execute(SqlStatements.delete(original.type()), original.type().keys(), original, List.of(), null);
    }

    private void execute(String sql, List<Field> fields, EntityRow row,
                         List<Field> keyFields, EntityRow original) throws SQLException {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Field field : fields) bind(statement, index++, row.get(field.name()));
            for (Field field : keyFields) bind(statement, index++, original.get(field.name()));
            int affected = statement.executeUpdate();
            if (affected == 0) throw new SQLException("The record no longer exists. Refresh and try again.");
        }
    }

    private static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof LocalDate date) statement.setDate(index, java.sql.Date.valueOf(date));
        else statement.setObject(index, value);
    }
}
