package com.marine.model;

/** In-memory demonstration roles; these do not change the JDBC account. */
public enum UserRole {
    ADMIN("Administrator"), RESEARCHER("Researcher"), VIEWER("Viewer");

    private final String label;

    UserRole(String label) { this.label = label; }

    @Override public String toString() { return label; }
}
