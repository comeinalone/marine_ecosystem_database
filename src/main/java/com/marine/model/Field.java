package com.marine.model;

import java.util.List;

/** Explicit form metadata; names come from our schema, never from user input. */
public record Field(String name, String label, Kind kind, boolean required,
                    boolean key, int maxLength, Integer minimum,
                    List<String> options, String reference) {
    public enum Kind { TEXT, INTEGER, DATE, CHOICE, REFERENCE }

    public Field {
        options = List.copyOf(options);
    }

    public static Field id(String name, String label) {
        return new Field(name, label, Kind.INTEGER, true, true, 0, null, List.of(), null);
    }
    public static Field text(String name, String label, int length, boolean required) {
        return new Field(name, label, Kind.TEXT, required, false, length, null, List.of(), null);
    }
    public static Field keyText(String name, String label, int length, List<String> choices) {
        return new Field(name, label, choices.isEmpty() ? Kind.TEXT : Kind.CHOICE,
                true, true, length, null, choices, null);
    }
    public static Field number(String name, String label, int minimum) {
        return new Field(name, label, Kind.INTEGER, false, false, 0, minimum, List.of(), null);
    }
    public static Field date(String name, String label) {
        return new Field(name, label, Kind.DATE, true, false, 0, null, List.of(), null);
    }
    public static Field choice(String name, String label, int length, String... choices) {
        return new Field(name, label, Kind.CHOICE, false, false, length, null, List.of(choices), null);
    }
    public static Field ref(String name, String label, boolean key, String entity) {
        return new Field(name, label, Kind.REFERENCE, true, key, 0, null, List.of(), entity);
    }
}
