package com.marine.search;

import java.time.LocalDate;
import java.util.*;

/** Immutable optional AND filters; blank input is omitted, NULL is an explicit operation. */
public record SearchCriteria(Map<String, Filter> filters, String term, boolean partial,
                             String sort, boolean descending, int limit, int offset) {
    public enum Match { EXACT, CONTAINS, RANGE, IS_NULL }
    public record Filter(Match match, Object value, Object upper) {
        public Filter { Objects.requireNonNull(match); }
        public static Filter exact(Object value) { return new Filter(Match.EXACT, value, null); }
        public static Filter contains(String value) { return new Filter(Match.CONTAINS, value, null); }
        public static Filter range(Object from, Object to) { return new Filter(Match.RANGE, from, to); }
        public static Filter isNull() { return new Filter(Match.IS_NULL, null, null); }
    }
    public SearchCriteria {
        filters = Collections.unmodifiableMap(new LinkedHashMap<>(filters));
        term = normalize(term);
        if (term.length() > 300) throw new IllegalArgumentException("Search text must be at most 300 characters.");
        if (limit < 1 || limit > 500 || offset < 0) throw new IllegalArgumentException("Use 1–500 rows per page and a nonnegative offset.");
    }
    public static SearchCriteria all() { return new SearchCriteria(Map.of(), "", true, null, false, 100, 0); }
    public SearchCriteria page(int nextOffset) { return new SearchCriteria(filters, term, partial, sort, descending, limit, nextOffset); }
    public SearchCriteria with(String name, Filter filter) {
        Map<String, Filter> next = new LinkedHashMap<>(filters); next.put(name, filter);
        return new SearchCriteria(next, term, partial, sort, descending, limit, 0);
    }
    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("(?U)\\s+", " ").strip();
    }
    public static Object parse(SearchField field, String input) {
        String text = normalize(input);
        if (text.isEmpty()) return null;
        try {
            return switch (field.kind()) {
                case INTEGER -> Integer.valueOf(text);
                case DATE, TIMESTAMP -> LocalDate.parse(text);
                default -> {
                    if (text.length() > 300) throw new IllegalArgumentException("Text is too long");
                    if (field.kind() == SearchField.Kind.CHOICE && !field.options().contains(text))
                        throw new IllegalArgumentException("Choose a listed value");
                    yield text;
                }
            };
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(field.label() + ": use " + switch (field.kind()) {
                case INTEGER -> "a whole number";
                case DATE, TIMESTAMP -> "a date in YYYY-MM-DD format";
                case CHOICE -> "a listed value";
                default -> "at most 300 characters";
            }, ex);
        }
    }
}
