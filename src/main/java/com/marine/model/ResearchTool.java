package com.marine.model;

/** Shared navigation titles for the read-only research tools. */
public enum ResearchTool {
    TIMELINE("Conservation Status Timeline"), TRACEABILITY("DNA Sample Traceability"),
    BIODIVERSITY("Biodiversity Explorer"), QUALITY("Research Data Quality Checker"),
    CONTRIBUTIONS("Researcher Contribution Dashboard");

    private final String title;
    ResearchTool(String title) { this.title = title; }
    @Override public String toString() { return title; }
}
