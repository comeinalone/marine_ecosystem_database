package com.marine.dao;

import com.marine.db.ConnectionProvider;
import com.marine.model.*;
import com.marine.search.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Small reusable CRUD DAO. All values are bound through PreparedStatement. */
public final class EntityDao {
    private final ConnectionProvider connections;
    public EntityDao(ConnectionProvider connections) { this.connections = connections; }

    public SearchService search() { return new SearchService(connections); }

    public List<EntityRow> list(EntityType type) throws SQLException {
        SearchDefinition definition = SearchDefinition.entity(type);
        String order = type.keys().stream().map(f -> "e.`" + f.name() + "`")
                .collect(java.util.stream.Collectors.joining(", "));
        String sql = "SELECT " + definition.select() + " FROM " + definition.from() + " ORDER BY " + order;
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<EntityRow> rows = new ArrayList<>();
            while (result.next()) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (SearchField field : definition.fields()) {
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
        for (EntityRow row : search().search(SearchDefinition.entity(type), SearchCriteria.all()).hits()
                .stream().map(SearchResult.Hit::entityRow).toList()) {
            String label = String.valueOf(row.get(type.displayColumn()));
            if (type == EntityType.RESEARCHER) label = row.get("full_name") + " / "
                    + row.get("researcher_specialization") + " / " + row.get("email_address");
            choices.add(new LookupChoice(((Number) row.get(type.keys().get(0).name())).intValue(), label));
        }
        return choices;
    }

    /** Identity lookup uses raw primary-key values, with no name normalization. */
    public Optional<EntityRow> find(EntityRow identity) throws SQLException {
        SearchDefinition definition = SearchDefinition.entity(identity.type());
        String where = identity.type().keys().stream().map(f -> "e.`" + f.name() + "` = ?")
                .collect(java.util.stream.Collectors.joining(" AND "));
        String sql = "SELECT " + definition.select() + " FROM " + definition.from() + " WHERE " + where;
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Field key : identity.type().keys()) bind(statement, index++, Objects.requireNonNull(identity.get(key.name()), "Missing key " + key.name()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Map<String, Object> values = new LinkedHashMap<>();
                for (SearchField field : definition.fields()) {
                    Object value = result.getObject(field.name());
                    values.put(field.name(), value instanceof java.sql.Date date ? date.toLocalDate() : value);
                }
                return Optional.of(new EntityRow(identity.type(), values));
            }
        }
    }

    public Optional<EntityRow> sample(SampleKey key) throws SQLException {
        EntityType type = EntityType.SAMPLE;
        String sql = "SELECT " + SqlStatements.names(type.fields()) + " FROM `DNA_Sample` WHERE " + SqlStatements.predicate(type);
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key.speciesID()); statement.setObject(2, key.sampleID());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Map<String, Object> values = new LinkedHashMap<>();
                for (Field field : type.fields()) {
                    Object value = result.getObject(field.name());
                    values.put(field.name(), value instanceof java.sql.Date date ? date.toLocalDate() : value);
                }
                return Optional.of(new EntityRow(type, values));
            }
        }
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
