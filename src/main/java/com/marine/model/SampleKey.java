package com.marine.model;

/** sampleID alone is not a sample identity. */
public record SampleKey(int speciesID, int sampleID) {
    @Override public String toString() { return "(" + speciesID + ", " + sampleID + ")"; }
}
