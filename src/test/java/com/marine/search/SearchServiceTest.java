package com.marine.search;

import com.marine.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.marine.search.SearchCriteria.Filter.*;

class SearchServiceTest {
    private final SearchService service = new SearchService(() -> { throw new AssertionError("Plan validation cannot open JDBC"); });
    private final SearchDefinition researchers = SearchDefinition.entity(EntityType.RESEARCHER);
    private SearchService.Plan plan(String field, SearchCriteria.Filter filter) { return service.plan(researchers, SearchCriteria.all().with(field, filter)); }

    @Test void exactNamesNormalizeCaseWhitespaceAndOptionalMiddleNamesInSql() {
        var plan = plan("full_name", exact("  SANA\t  Khan \n"));
        assertEquals(List.of("sana khan"), plan.count().parameters());
        assertTrue(plan.rows().sql().contains("REGEXP_REPLACE(CONCAT_WS(' ', e.first_name, e.middle_name, e.last_name)"));
        assertTrue(plan.count().sql().contains("COLLATE utf8mb4_bin = ?"));
        assertFalse(plan.count().sql().contains(" LIKE "));
        assertEquals("Sana Khan", SearchCriteria.normalize(" Sana \t\n Khan "));
        assertEquals("Sana Khan", SearchCriteria.normalize("Sana\u2003Khan"));
    }
    @Test void partialFullNamesAndSurnameOnlyUseLiteralLikeAndExactPriority() {
        var plan = plan("full_name", contains("Sana Khan"));
        assertEquals(List.of("%sana khan%"), plan.count().parameters());
        assertTrue(plan.rows().sql().contains("CASE WHEN"));
        assertEquals("sana khan", plan.rows().parameters().get(1));
        var surname = plan("last_name", contains("KHAN"));
        assertTrue(surname.count().sql().contains("e.`last_name`"));
        assertEquals(List.of("%khan%"), surname.count().parameters());
    }
    @Test void quickSearchPrioritizesFullNameAndAllowsExactIdsWithoutAcceptingRawSql() {
        var quick = service.plan(researchers, new SearchCriteria(Map.of(), "Sana Khan", true, "email_address", true, 25, 50));
        assertTrue(quick.rows().sql().contains("ORDER BY CASE WHEN LOWER(CONVERT(TRIM(REGEXP_REPLACE"));
        assertTrue(quick.rows().sql().contains("e.`email_address` DESC"));
        assertEquals(List.of(25, 50), quick.rows().parameters().subList(quick.rows().parameters().size() - 2, quick.rows().parameters().size()));
        assertFalse(quick.rows().sql().contains("Sana Khan"));
        var id = service.plan(researchers, new SearchCriteria(Map.of(), "17", false, null, false, 100, 0));
        assertTrue(id.count().sql().contains("e.`researcherID` = ?")); assertTrue(id.count().parameters().contains(17));
    }
    @Test void combinedForeignKeyNamesIdsAndDateRangesAreAndPredicatesWithExplicitAliases() {
        var def = SearchDefinition.entity(EntityType.SAMPLE);
        var criteria = SearchCriteria.all().with("species_name", contains("Shark"))
                .with("researcher_name", exact("Sana Khan")).with("vesselID", exact(7))
                .with("location_name", contains("station"))
                .with("collection_date", range(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)));
        var query = service.plan(def, criteria).count();
        assertEquals(List.of("%shark%", "sana khan", 7, "%station%", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)), query.parameters());
        assertTrue(query.sql().contains(" AND ")); assertTrue(query.sql().contains("LEFT JOIN Researcher"));
        assertTrue(query.sql().contains("LEFT JOIN Research_Vehicle"));
        assertFalse(service.plan(def, criteria).rows().sql().contains("SELECT *"));
        assertFalse(query.sql().contains("Researcher_Contact"), "No fan-out joins");
    }
    @Test void numericRangesOpenBoundsNullAndBlankFiltersAreValidated() {
        var def = SearchDefinition.entity(EntityType.VESSEL);
        var plan = service.plan(def, SearchCriteria.all().with("vessel_capacity", range(null, 20)).with("home_port", exact(" ")));
        assertEquals(List.of(20), plan.count().parameters()); assertTrue(plan.count().sql().contains("<= ?"));
        var nullable = service.plan(def, SearchCriteria.all().with("vessel_capacity", isNull()));
        assertTrue(nullable.count().sql().contains("IS NULL")); assertTrue(nullable.count().parameters().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.plan(def, SearchCriteria.all().with("vessel_capacity", range(20, 10))));
        assertThrows(IllegalArgumentException.class, () -> plan("full_name", range("a", "z")));
        assertThrows(IllegalArgumentException.class, () -> plan("researcherID", contains("1")));
        assertThrows(IllegalArgumentException.class, () -> plan("date_of_birth", exact("not a date")));
    }
    @Test void auditTimestampEndIncludesEntireDayAndStatusesHaveWhitelistedChoices() {
        var def = SearchDefinition.audit(); LocalDate end = LocalDate.of(2026, 10, 14);
        var plan = service.plan(def, SearchCriteria.all().with("changed_at", range(null, end)).with("new_status", exact("Endangered")));
        assertEquals(List.of(end.plusDays(1), "endangered"), plan.count().parameters());
        assertTrue(plan.count().sql().contains("e.changed_at < ?"));
        assertThrows(IllegalArgumentException.class, () -> service.plan(def, SearchCriteria.all().with("new_status", exact("Invented"))));
        assertThrows(IllegalStateException.class, () -> new SearchResult.Hit(def, Map.of("auditID", 1)).entityRow());
    }
    @Test void injectionLikeValuesStayBoundAndWildcardsAndEscapeCharactersStayLiteral() {
        String malicious = "Sana%_!'); DROP TABLE Researcher; --";
        var plan = plan("full_name", contains(malicious));
        assertFalse(plan.rows().sql().contains(malicious));
        assertEquals("%sana!%!_!!'); drop table researcher; --%", plan.count().parameters().get(0));
        assertTrue(plan.count().sql().contains("ESCAPE '!'"));
        assertThrows(IllegalArgumentException.class, () -> service.plan(researchers,
                new SearchCriteria(Map.of(), "", false, "researcherID; DROP TABLE Researcher", false, 10, 0)));
        assertThrows(IllegalArgumentException.class, () -> plan("made_up", exact("x")));
        assertThrows(IllegalArgumentException.class, () -> new SearchCriteria(Map.of(), "", false, null, false, 501, 0));
        assertThrows(IllegalArgumentException.class, () -> new SearchCriteria(Map.of(), "", false, null, false, 10, -1));
    }
    @ParameterizedTest @EnumSource(EntityType.class)
    void eachEntityHasExplicitSqlAndCompleteIdentity(EntityType type) throws Exception {
        SearchDefinition def = SearchDefinition.entity(type);
        Map<String, Object> row = new LinkedHashMap<>(); for (Field key : type.keys()) row.put(key.name(), key.kind() == Field.Kind.INTEGER || key.kind() == Field.Kind.REFERENCE ? 7 : "Work");
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, params) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(1) : SearchJdbcStub.rows(def, List.of(row)));
        SearchResult result = new SearchService(jdbc).search(def, SearchCriteria.all());
        assertEquals(row, result.hits().get(0).identity()); assertEquals(type, result.hits().get(0).entityRow().type());
        assertEquals(1, jdbc.connectionsClosed.get()); assertEquals(2, jdbc.statementsClosed.get()); assertEquals(2, jdbc.resultsClosed.get());
        assertEquals(List.of(100, 0), jdbc.calls.get(1).parameters());
    }
    @Test void duplicateNamesRemainDistinctAndNullsAndCompositeKeysSurviveDetachedResults() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of("researcherID", 1, "full_name", "Sana Khan"), Map.of("researcherID", 2, "full_name", "Sana Khan"));
        SearchJdbcStub jdbc = new SearchJdbcStub((sql, params) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(2) : SearchJdbcStub.rows(researchers, rows));
        SearchResult result = new SearchService(jdbc).search(researchers, SearchCriteria.all());
        assertEquals(2, result.total()); assertEquals(2, result.hits().size());
        assertNotEquals(result.hits().get(0).identity(), result.hits().get(1).identity());
        assertNull(result.hits().get(0).get("middle_name"));
        assertThrows(UnsupportedOperationException.class, () -> result.hits().get(0).values().put("researcherID", 3));
        var sample = new SearchResult.Hit(SearchDefinition.entity(EntityType.SAMPLE), Map.of("speciesID", 31, "sampleID", 7));
        assertEquals(new SampleKey(31, 7), sample.sampleKey()); assertEquals(2, sample.identity().size());
        var contact = new SearchResult.Hit(SearchDefinition.entity(EntityType.CONTACT), Map.of("researcherID", 2, "contact_type", "Work", "phone_number", "123"));
        assertEquals(3, contact.identity().size());
    }
    @Test void emptyAndLargeCountsUseBoundedPagesAndFailuresCloseResources() throws Exception {
        SearchJdbcStub empty = new SearchJdbcStub((sql, params) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(0) : SearchJdbcStub.rows(researchers, List.of()));
        assertTrue(new SearchService(empty).search(researchers, SearchCriteria.all()).hits().isEmpty());
        List<Map<String, Object>> rows = java.util.stream.IntStream.range(0, 50).mapToObj(i -> Map.<String, Object>of("researcherID", i)).toList();
        SearchJdbcStub large = new SearchJdbcStub((sql, params) -> sql.contains("COUNT(*)") ? SearchJdbcStub.count(1_000_000) : SearchJdbcStub.rows(researchers, rows));
        var result = new SearchService(large).search(researchers, new SearchCriteria(Map.of(), "", true, null, false, 50, 500));
        assertEquals(1_000_000, result.total()); assertEquals(50, result.hits().size()); assertEquals(List.of(50, 500), large.calls.get(1).parameters());
        SearchJdbcStub failure = new SearchJdbcStub((sql, params) -> { throw new SQLException("offline"); });
        assertThrows(SQLException.class, () -> new SearchService(failure).search(researchers, SearchCriteria.all()));
        assertEquals(1, failure.connectionsClosed.get()); assertEquals(1, failure.statementsClosed.get());
    }
    @Test void contactAndSubtypeResearcherNamesFollowIdAndAreJoinedOnly() {
        for (EntityType type : List.of(EntityType.CONTACT, EntityType.BIOLOGIST, EntityType.GENETICIST)) {
            var def = SearchDefinition.entity(type);
            assertEquals("researcherID", def.fields().get(0).name()); assertEquals("researcher_name", def.fields().get(1).name());
            assertTrue(def.from().contains("LEFT JOIN Researcher r ON r.researcherID = e.researcherID"));
            assertTrue(service.plan(def, SearchCriteria.all().with("researcher_name", exact("Sana Khan"))).count().sql().contains("r.middle_name"));
        }
    }
}
