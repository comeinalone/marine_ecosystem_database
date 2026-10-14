package com.marine.dao;

import com.marine.db.*;
import com.marine.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.lang.reflect.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL semantics, opt-in only on an explicitly designated disposable database. No DDL. */
@EnabledIfEnvironmentVariable(named = "MARINE_RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "MARINE_TEST_DB_DISPOSABLE", matches = "true")
class ResearchJdbcIntegrationTest {
    @Test void realResearchQueriesAuditAggregationNullsAndZeroParticipation() throws Exception {
        try (Connection connection = new Database(DatabaseConfig.load()).open()) {
            connection.setAutoCommit(false);
            ConnectionProvider borrowed = () -> (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        try { return method.invoke(connection, args); }
                        catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
            try {
                EntityDao entities = new EntityDao(borrowed); ResearchDao research = new ResearchDao(borrowed);
                entities.insert(new EntityRow(EntityType.RESEARCHER, Map.of("researcherID", 930001, "first_name", "Research",
                        "last_name", "Fixture", "researcher_specialization", "Genetics", "email_address", "research-fixture@example.invalid",
                        "date_of_birth", LocalDate.of(2000, 1, 1))));
                entities.insert(new EntityRow(EntityType.RESEARCHER, Map.of("researcherID", 930002, "first_name", "Zero",
                        "last_name", "Fixture", "researcher_specialization", "Genetics", "email_address", "zero-fixture@example.invalid",
                        "date_of_birth", LocalDate.of(2000, 1, 1))));
                entities.insert(new EntityRow(EntityType.VESSEL, Map.of("vesselID", 930001, "vessel_name", "Fixture vessel", "vessel_capacity", 2, "home_port", "Fixture port")));
                entities.insert(new EntityRow(EntityType.LOCATION, Map.of("locationID", 930001, "location_name", "Fixture station", "ocean_region", "Fixture ocean")));
                entities.insert(new EntityRow(EntityType.LOCATION, Map.of("locationID", 930002, "location_name", "Empty station", "ocean_region", "Fixture ocean")));
                EntityRow species = new EntityRow(EntityType.SPECIES, Map.of("speciesID", 930001, "common_name", "First fixture", "scientific_name", "Researchus primus", "conservation_status", "Least Concern"));
                entities.insert(species);
                entities.insert(new EntityRow(EntityType.SPECIES, Map.of("speciesID", 930002, "common_name", "Second fixture", "scientific_name", "Researchus secundus", "conservation_status", "Critically Endangered")));
                assertTrue(research.timeline(930001, false).rows().isEmpty());
                Map<String, Object> updated = new LinkedHashMap<>(species.values()); updated.put("conservation_status", "Endangered");
                EntityRow endangered = new EntityRow(EntityType.SPECIES, updated); entities.update(species, endangered); entities.update(endangered, endangered);
                TableData audit = research.timeline(930001, false); assertEquals(1, audit.rows().size());
                assertEquals("Least Concern", value(audit, audit.rows().get(0), "old_status"));
                assertEquals("Endangered", value(audit, audit.rows().get(0), "new_status"));
                assertNotNull(value(audit, audit.rows().get(0), "changed_at"));
                updated.put("conservation_status", null); entities.update(endangered, new EntityRow(EntityType.SPECIES, updated));
                TableData newest = research.timeline(930001, true); assertEquals(2, newest.rows().size());
                assertNull(value(newest, newest.rows().get(0), "new_status"));
                entities.update(new EntityRow(EntityType.SPECIES, updated), endangered);

                insertSample(entities, 930001, 1, LocalDate.of(1999, 1, 1), "Low", "COI");
                insertSample(entities, 930001, 2, LocalDate.of(2026, 1, 1), null, "COI");
                insertSample(entities, 930002, 1, LocalDate.of(2099, 1, 1), "Medium", " ");
                insertSample(entities, 930002, 2, LocalDate.of(2026, 1, 2), "High", "COI");
                TableData trace = research.traceability(new SampleKey(930002, 1)); assertEquals(1, trace.rows().size());
                assertEquals("Second fixture", value(trace, trace.rows().get(0), "common_name"));
                assertEquals(930001, ((Number) value(trace, trace.rows().get(0), "researcherID")).intValue());
                assertNull(entities.sample(new SampleKey(930001, 2)).orElseThrow().get("DNA_quality"));
                assertTrue(research.traceability(new SampleKey(930002, 999)).rows().isEmpty());

                TableData regions = research.regions(); List<Object> region = row(regions, "locationID", 930001);
                assertEquals(4, number(regions, region, "total_samples")); assertEquals(2, number(regions, region, "distinct_species"));
                assertEquals(2, number(regions, region, "endangered_species"));
                assertEquals(0, number(regions, row(regions, "locationID", 930002), "total_samples"));
                TableData represented = research.regionSpecies(930001, 2); assertEquals(2, represented.rows().size());
                assertTrue(research.regionSpecies(930001, 3).rows().isEmpty());

                assertEquals(1, fixtureIssues(research.quality(ResearchDao.IssueCategory.QUALITY)));
                assertEquals(1, fixtureIssues(research.quality(ResearchDao.IssueCategory.METADATA)));
                assertEquals(2, fixtureIssues(research.quality(ResearchDao.IssueCategory.DATES)));
                assertEquals(1, fixtureIssues(research.quality(ResearchDao.IssueCategory.CONTENT)));
                assertEquals(0, fixtureIssues(research.quality(ResearchDao.IssueCategory.CONSTRAINT)));
                assertEquals(5, fixtureIssues(research.quality(ResearchDao.IssueCategory.ALL)));
                for (ResearchDao.ContributionSort sort : ResearchDao.ContributionSort.values()) {
                    TableData contributions = research.contributions(sort); List<Object> active = row(contributions, "researcherID", 930001);
                    assertEquals(4, number(contributions, active, "sample_count"));
                    assertEquals(2, number(contributions, active, "distinct_species"));
                    assertEquals(1, number(contributions, active, "distinct_regions"));
                    assertEquals(java.sql.Date.valueOf("1999-01-01"), value(contributions, active, "first_collection"));
                    List<Object> empty = row(contributions, "researcherID", 930002);
                    assertEquals(0, number(contributions, empty, "sample_count"));
                    assertEquals(0, number(contributions, empty, "distinct_species"));
                    assertNull(value(contributions, empty, "first_collection"));
                    assertTrue(number(contributions, active, "sample_count_rank") < number(contributions, empty, "sample_count_rank"));
                }
                assertEquals(4, research.researcherSamples(930001).rows().size());
                assertTrue(research.researcherSamples(930002).rows().isEmpty());
            } finally { connection.rollback(); }
            assertTrue(new ResearchDao(borrowed).traceability(new SampleKey(930001, 1)).rows().isEmpty());
        }
    }
    private void insertSample(EntityDao dao, int speciesID, int sampleID, LocalDate date, String quality, String marker) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>(Map.of("speciesID", speciesID, "sampleID", sampleID,
                "collection_date", date, "gene_marker", marker, "researcherID", 930001, "vesselID", 930001, "locationID", 930001));
        values.put("DNA_quality", quality); dao.insert(new EntityRow(EntityType.SAMPLE, values));
    }
    private long fixtureIssues(TableData data) {
        return data.rows().stream().filter(row -> ((Number) row.get(0)).intValue() == 930001 || ((Number) row.get(0)).intValue() == 930002).count();
    }
    private static Object value(TableData data, List<Object> row, String column) { return row.get(data.columns().indexOf(column)); }
    private static long number(TableData data, List<Object> row, String column) { return ((Number) value(data, row, column)).longValue(); }
    private static List<Object> row(TableData data, String key, int id) {
        return data.rows().stream().filter(row -> ((Number) value(data, row, key)).intValue() == id).findFirst().orElseThrow();
    }
}
