package com.marine.search;

import com.marine.db.ConnectionProvider;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Search values are always bound. Only registry expressions form SQL identifiers. */
public final class SearchService {
    private final ConnectionProvider connections;
    public SearchService(ConnectionProvider connections) { this.connections = connections; }
    public record Query(String sql, List<Object> parameters) {
        public Query { parameters = List.copyOf(parameters); }
    }
    public record Plan(Query count, Query rows) { }
    public static String literalLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
    private static String folded(SearchField field) {
        // Binary collation keeps accent differences significant, while LOWER ignores case.
        return "LOWER(CONVERT(" + (field.fullName() ? field.expression() : "TRIM(" + field.expression() + ")") + " USING utf8mb4)) COLLATE utf8mb4_bin";
    }
    private static String text(Object value) { return SearchCriteria.normalize(value.toString()).toLowerCase(Locale.ROOT); }
    public Plan plan(SearchDefinition def, SearchCriteria criteria) {
        List<String> predicates = new ArrayList<>(), exact = new ArrayList<>();
        List<Object> values = new ArrayList<>(), rankValues = new ArrayList<>();
        for (var entry : criteria.filters().entrySet()) {
            SearchField field = def.field(entry.getKey()); SearchCriteria.Filter filter = entry.getValue();
            String expression = field.expression();
            if (filter.match() == SearchCriteria.Match.IS_NULL) {
                if (filter.value() != null || filter.upper() != null) throw new IllegalArgumentException("NULL filter has no bounds");
                predicates.add(expression + " IS NULL"); continue;
            }
            Object lower = validated(field, filter.value()), upper = validated(field, filter.upper());
            if (filter.match() != SearchCriteria.Match.RANGE && upper != null) throw new IllegalArgumentException("Only ranges have an upper bound");
            if (filter.match() == SearchCriteria.Match.RANGE) {
                if (field.text()) throw new IllegalArgumentException("Ranges need numeric or date fields");
                if (lower != null && upper != null && compare(lower, upper) > 0) throw new IllegalArgumentException(field.label() + ": range start exceeds end");
                if (lower != null) { predicates.add(expression + " >= ?"); values.add(lower); }
                if (upper != null) { predicates.add(expression + (field.kind() == SearchField.Kind.TIMESTAMP ? " < ?" : " <= ?"));
                    values.add(field.kind() == SearchField.Kind.TIMESTAMP ? ((LocalDate) upper).plusDays(1) : upper); }
            } else if (lower != null) {
                if (field.text()) {
                    String term = text(lower);
                    if (filter.match() == SearchCriteria.Match.CONTAINS && field.kind() != SearchField.Kind.TEXT)
                        throw new IllegalArgumentException("Choose an exact enumerated value");
                    predicates.add(folded(field) + (filter.match() == SearchCriteria.Match.CONTAINS ? " LIKE ? ESCAPE '!'" : " = ?"));
                    values.add(filter.match() == SearchCriteria.Match.CONTAINS ? "%" + literalLike(term) + "%" : term);
                    exact.add(folded(field) + " = ?"); rankValues.add(term);
                } else {
                    if (filter.match() != SearchCriteria.Match.EXACT) throw new IllegalArgumentException("IDs and numbers require exact or range matching");
                    if (field.kind() == SearchField.Kind.TIMESTAMP) {
                        predicates.add(expression + " >= ? AND " + expression + " < ?"); values.add(lower); values.add(((LocalDate) lower).plusDays(1));
                    } else { predicates.add(expression + " = ?"); values.add(lower); }
                }
            }
        }
        if (!criteria.term().isEmpty()) {
            String term = text(criteria.term()); List<String> quick = new ArrayList<>(), quickExact = new ArrayList<>();
            for (SearchField field : def.fields()) {
                if (field.text()) {
                    quick.add(folded(field) + (criteria.partial() ? " LIKE ? ESCAPE '!'" : " = ?"));
                    values.add(criteria.partial() ? "%" + literalLike(term) + "%" : term);
                    quickExact.add(folded(field) + " = ?"); rankValues.add(term);
                } else if (field.kind() == SearchField.Kind.INTEGER && def.keys().contains(field.name())) {
                    try { int id = Integer.parseInt(term); quick.add(field.expression() + " = ?"); values.add(id);
                        quickExact.add(field.expression() + " = ?"); rankValues.add(id);
                    } catch (NumberFormatException ignored) { }
                }
            }
            predicates.add("(" + String.join(" OR ", quick) + ")");
            exact.add("(" + String.join(" OR ", quickExact) + ")");
        }
        String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        String sortName = criteria.sort() == null ? def.keys().get(0) : criteria.sort();
        SearchField sort = def.field(sortName); // fail closed for injected order identifiers
        List<Object> priorityValues = new ArrayList<>();
        List<String> namePriority = new ArrayList<>();
        if (!criteria.term().isEmpty()) for (SearchField field : def.fields()) if (field.fullName()) {
            namePriority.add(folded(field) + " = ?"); priorityValues.add(text(criteria.term()));
        }
        String priority = namePriority.isEmpty() ? "" : "CASE WHEN " + String.join(" OR ", namePriority) + " THEN 0 ELSE 1 END, ";
        String order = exact.isEmpty() ? "" : "CASE WHEN " + String.join(" AND ", exact) + " THEN 0 ELSE 1 END, ";
        order += sort.expression() + (criteria.descending() ? " DESC" : " ASC");
        for (String key : def.keys()) if (!key.equals(sortName)) order += ", " + def.field(key).expression() + " ASC";
        List<Object> rowValues = new ArrayList<>(values); rowValues.addAll(priorityValues); rowValues.addAll(rankValues);
        rowValues.add(criteria.limit()); rowValues.add(criteria.offset());
        return new Plan(new Query("SELECT COUNT(*) AS match_count FROM " + def.from() + where, values),
                new Query("SELECT " + def.select() + " FROM " + def.from() + where + " ORDER BY " + priority + order + " LIMIT ? OFFSET ?", rowValues));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compare(Object a, Object b) { return ((Comparable) a).compareTo(b); }
    private static Object validated(SearchField field, Object value) {
        return value == null ? null : SearchCriteria.parse(field, value.toString());
    }
    public SearchResult search(SearchDefinition def, SearchCriteria criteria) throws SQLException {
        Plan plan = plan(def, criteria);
        try (Connection connection = connections.open()) {
            long total = 0;
            try (PreparedStatement statement = prepare(connection, plan.count()); ResultSet rs = statement.executeQuery()) {
                if (rs.next()) total = ((Number) rs.getObject(1)).longValue();
            }
            List<SearchResult.Hit> hits = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, plan.rows()); ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < def.fields().size(); i++) {
                        Object value = rs.getObject(i + 1);
                        if (value instanceof java.sql.Date date) value = date.toLocalDate();
                        row.put(def.fields().get(i).name(), value);
                    }
                    hits.add(new SearchResult.Hit(def, row));
                }
            }
            return new SearchResult(def, hits, total, criteria.offset(), criteria.limit());
        }
    }
    private static PreparedStatement prepare(Connection connection, Query query) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query.sql());
        try {
            for (int i = 0; i < query.parameters().size(); i++) {
                Object value = query.parameters().get(i);
                statement.setObject(i + 1, value instanceof LocalDate date ? java.sql.Date.valueOf(date) : value);
            }
            return statement;
        } catch (SQLException | RuntimeException ex) { statement.close(); throw ex; }
    }
}
