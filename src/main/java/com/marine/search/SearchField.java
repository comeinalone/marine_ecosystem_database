package com.marine.search;

import java.util.List;

/** Trusted SQL expressions, created only by SearchDefinition. */
public record SearchField(String name, String label, String expression, Kind kind,
                          List<String> options, boolean fullName, String reference, String referenceKey) {
    public enum Kind { TEXT, INTEGER, DATE, TIMESTAMP, CHOICE }
    public SearchField { options = List.copyOf(options); }
    public boolean text() { return kind == Kind.TEXT || kind == Kind.CHOICE; }
}
