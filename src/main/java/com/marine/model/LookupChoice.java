package com.marine.model;

public record LookupChoice(int id, String label) {
    @Override public String toString() { return id + " — " + label; }
}
