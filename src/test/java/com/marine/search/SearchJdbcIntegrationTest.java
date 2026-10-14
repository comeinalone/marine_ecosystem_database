package com.marine.search;

import com.marine.dao.*;
import com.marine.db.*;
import com.marine.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.lang.reflect.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import static com.marine.search.SearchCriteria.Filter.*;
import static org.junit.jupiter.api.Assertions.*;

/** MySQL matching/joins/ordering semantics. Existing initialized disposable schema only; no DDL or reseed. */
@EnabledIfEnvironmentVariable(named = "MARINE_RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "MARINE_TEST_DB_DISPOSABLE", matches = "true")
class SearchJdbcIntegrationTest {
    @Test void realMysqlNamesDuplicatesLiteralWildcardsRangesJoinsAndCompositeKeys() throws Exception {
        try (Connection connection = new Database(DatabaseConfig.load()).open()) {
            connection.setAutoCommit(false);
            ConnectionProvider borrowed = () -> (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        try { return method.invoke(connection, args); } catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
            EntityDao entities = new EntityDao(borrowed); SearchService search = new SearchService(borrowed);
            try {
                researcher(entities, 940001, "Sana", null, "Khan", "Genetics");
                researcher(entities, 940002, "  SANA  ", " ", " KHAN  ", "Marine biology");
                researcher(entities, 940003, "Sana", "Ali", "Khan", "Genetics");
                researcher(entities, 940004, "Sanaya", null, "Khan", "Biology");
                researcher(entities, 940005, "Sana%", null, "_Khan", "Genetics");
                researcher(entities, 940006, "X'; --", null, "Fixture", "Genetics");
                researcher(entities, 940007, "Sana", null, "Khan Jr", "Biology");
                SearchDefinition def = SearchDefinition.entity(EntityType.RESEARCHER);
                SearchCriteria fixture = SearchCriteria.all().with("researcherID", range(940001, 940007));
                SearchResult exact = search.search(def, fixture.with("full_name", exact("  sAnA\t KHAN ")));
                assertEquals(2, exact.total()); assertEquals(Set.of(940001, 940002), ids(exact, "researcherID"));
                SearchResult partial = search.search(def, fixture.with("full_name", contains("Sana")));
                assertEquals(6, partial.total()); assertEquals(940001, partial.hits().get(0).get("researcherID"));
                SearchCriteria quick = new SearchCriteria(fixture.filters(), "SANA KHAN", true, "researcherID", true, 100, 0);
                SearchResult priority = search.search(def, quick);
                assertEquals(3, priority.total()); assertEquals(940002, priority.hits().get(0).get("researcherID"));
                assertEquals(940001, priority.hits().get(1).get("researcherID")); assertEquals(940007, priority.hits().get(2).get("researcherID"));
                assertEquals(4, search.search(def, fixture.with("last_name", exact("khan"))).total());
                assertEquals(1, search.search(def, fixture.with("full_name", exact("Sana Khan")).with("researcher_specialization", exact("Genetics"))).total());
                assertEquals(1, search.search(def, fixture.with("first_name", contains("Sana%"))).total());
                assertEquals(1, search.search(def, fixture.with("last_name", contains("_Khan"))).total());
                assertEquals(1, search.search(def, fixture.with("first_name", exact("X'; --"))).total());
                assertEquals(0, search.search(def, fixture.with("full_name", contains("' OR 1=1 --"))).total());
                assertEquals(5, search.search(def, fixture.with("middle_name", isNull())).total());
                assertEquals(0, search.search(def, fixture.with("full_name", exact("Absent Researcher"))).total());
                assertEquals(2, search.search(def, new SearchCriteria(fixture.filters(), "", true, null, false, 2, 2)).hits().size());

                entities.insert(new EntityRow(EntityType.CONTACT, Map.of("researcherID", 940001, "contact_type", "Work", "phone_number", "940001")));
                entities.insert(new EntityRow(EntityType.CONTACT, Map.of("researcherID", 940001, "contact_type", "Mobile", "phone_number", "940002")));
                entities.insert(new EntityRow(EntityType.BIOLOGIST, Map.of("researcherID", 940001, "field_experience", 12, "diving_certified", "Yes")));
                entities.insert(new EntityRow(EntityType.GENETICIST, Map.of("researcherID", 940001, "laboratory_name", "Search fixture lab", "research_focus", "COI")));
                for (EntityType type : List.of(EntityType.CONTACT, EntityType.BIOLOGIST, EntityType.GENETICIST)) {
                    SearchResult related = search.search(SearchDefinition.entity(type), SearchCriteria.all().with("researcherID", exact(940001)).with("researcher_name", exact("Sana Khan")));
                    assertEquals(type == EntityType.CONTACT ? 2 : 1, related.total()); assertEquals("Sana Khan", related.hits().get(0).get("researcher_name"));
                }
                var contacts = search.search(SearchDefinition.entity(EntityType.CONTACT), SearchCriteria.all().with("researcherID", exact(940001)));
                EntityRow original = contacts.hits().get(0).entityRow();
                Map<String, Object> replacement = new LinkedHashMap<>(original.values()); replacement.put("phone_number", "940009");
                entities.update(original, new EntityRow(EntityType.CONTACT, replacement));
                assertEquals(2, search.search(SearchDefinition.entity(EntityType.CONTACT), SearchCriteria.all().with("researcherID", exact(940001))).total());

                entities.insert(new EntityRow(EntityType.VESSEL, Map.of("vesselID", 940001, "vessel_name", "Search fixture vessel", "vessel_capacity", 12, "home_port", "Search port")));
                entities.insert(new EntityRow(EntityType.LOCATION, Map.of("locationID", 940001, "location_name", "Search fixture station", "ocean_region", "Fixture ocean")));
                EntityRow species = new EntityRow(EntityType.SPECIES, Map.of("speciesID", 940001, "common_name", "Search fixture shark", "scientific_name", "Searchus fixture primus", "conservation_status", "Least Concern"));
                entities.insert(species);
                entities.insert(new EntityRow(EntityType.SPECIES, Map.of("speciesID", 940002, "common_name", "Search fixture ray", "scientific_name", "Searchus fixture secundus", "conservation_status", "Endangered")));
                sample(entities, 940001, 7, "High", LocalDate.of(2026, 1, 1));
                sample(entities, 940002, 7, null, LocalDate.of(2026, 1, 2));
                SearchDefinition samples = SearchDefinition.entity(EntityType.SAMPLE);
                SearchCriteria sampleFixture = SearchCriteria.all().with("speciesID", range(940001, 940002));
                var found = search.search(samples, sampleFixture.with("researcher_name", exact("Sana Khan")).with("vessel_name", contains("fixture vessel"))
                        .with("location_name", exact("Search fixture station")).with("sampleID", exact(7))
                        .with("collection_date", range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))));
                assertEquals(2, found.total(), "Contacts must not multiply samples");
                assertEquals(new SampleKey(940001, 7), found.hits().get(0).sampleKey());
                assertEquals(new SampleKey(940002, 7), found.hits().get(1).sampleKey());
                var nullQuality = search.search(samples, sampleFixture.with("DNA_quality", isNull()));
                assertEquals(new SampleKey(940002, 7), nullQuality.hits().get(0).sampleKey());
                assertEquals(1, search.search(samples, sampleFixture.with("species_name", contains("shark"))).total());
                assertEquals(1, search.search(SearchDefinition.entity(EntityType.VESSEL), SearchCriteria.all().with("vesselID", exact(940001)).with("vessel_capacity", range(10, 20))).total());
                Map<String, Object> edited = new LinkedHashMap<>(found.hits().get(1).entityRow().values()); edited.put("gene_marker", "Search-edited");
                entities.update(found.hits().get(1).entityRow(), new EntityRow(EntityType.SAMPLE, edited));
                assertEquals("Search-edited", entities.sample(new SampleKey(940002, 7)).orElseThrow().get("gene_marker"));
                assertEquals("COI", entities.sample(new SampleKey(940001, 7)).orElseThrow().get("gene_marker"));

                Map<String, Object> updated = new LinkedHashMap<>(species.values()); updated.put("conservation_status", "Endangered"); entities.update(species, new EntityRow(EntityType.SPECIES, updated));
                LocalDate serverDate;
                try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT CURRENT_DATE")) { assertTrue(rs.next()); serverDate = rs.getDate(1).toLocalDate(); }
                SearchResult audit = search.search(SearchDefinition.audit(), SearchCriteria.all().with("speciesID", exact(940001))
                        .with("old_status", exact("Least Concern")).with("new_status", exact("Endangered")).with("changed_at", range(serverDate, serverDate)));
                assertEquals(1, audit.total()); assertNotNull(audit.hits().get(0).identity().get("auditID"));
                ResearchDao research = new ResearchDao(borrowed);
                assertEquals(1, research.timeline(940001, false).rows().size()); assertEquals(1, research.traceability(new SampleKey(940002, 7)).rows().size());
                assertEquals(2, research.regionSpecies(940001, 1).rows().size()); assertTrue(research.regionSpecies(940001, 2).rows().isEmpty());
                assertFalse(research.quality(ResearchDao.IssueCategory.METADATA, new SampleKey(940002, 7)).rows().isEmpty());
                for (ResearchDao.ContributionSort sort : ResearchDao.ContributionSort.values()) assertFalse(research.contributions(sort).rows().isEmpty());
            } finally { connection.rollback(); }
            assertTrue(entities.sample(new SampleKey(940001, 7)).isEmpty());
        }
    }
    private static Set<Object> ids(SearchResult result, String column) {
        Set<Object> ids = new HashSet<>(); result.hits().forEach(hit -> ids.add(hit.get(column))); return ids;
    }
    private static void researcher(EntityDao dao, int id, String first, String middle, String last, String specialization) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(Map.of("researcherID", id, "first_name", first, "last_name", last,
                "researcher_specialization", specialization, "email_address", "search-" + id + "@example.invalid", "date_of_birth", LocalDate.of(2000, 1, 1)));
        row.put("middle_name", middle); dao.insert(new EntityRow(EntityType.RESEARCHER, row));
    }
    private static void sample(EntityDao dao, int species, int sample, String quality, LocalDate date) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(Map.of("speciesID", species, "sampleID", sample, "collection_date", date, "gene_marker", "COI",
                "researcherID", 940001, "vesselID", 940001, "locationID", 940001)); row.put("DNA_quality", quality); dao.insert(new EntityRow(EntityType.SAMPLE, row));
    }
}
