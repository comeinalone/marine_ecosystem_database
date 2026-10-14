package com.marine.dao;

import com.marine.model.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline JDBC contract tests; SQL result semantics are covered by the opt-in MySQL test. */
class ResearchDaoTest {
    private static final class Capture {
        String sql;
        Map<Integer, Object> parameters = new TreeMap<>();
        List<String> columns = List.of("speciesID", "sampleID", "optional");
        List<List<Object>> rows = List.of(Arrays.asList(7, 3, null));
        boolean connectionClosed, statementClosed, resultClosed, fail;
        Connection open() {
            return proxy(Connection.class, (method, args) -> switch (method) {
                case "prepareStatement" -> { sql = (String) args[0]; yield statement(); }
                case "close" -> { connectionClosed = true; yield null; }
                default -> throw new UnsupportedOperationException(method);
            });
        }
        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (method, args) -> switch (method) {
                case "setObject" -> { parameters.put((Integer) args[0], args[1]); yield null; }
                case "executeQuery" -> { if (fail) throw new SQLException("test failure"); yield result(); }
                case "close" -> { statementClosed = true; yield null; }
                default -> throw new UnsupportedOperationException(method);
            });
        }
        ResultSet result() {
            int[] row = {-1};
            return proxy(ResultSet.class, (method, args) -> switch (method) {
                case "next" -> ++row[0] < rows.size();
                case "getObject" -> rows.get(row[0]).get(args[0] instanceof Integer i ? i - 1 : columns.indexOf(args[0]));
                case "getMetaData" -> proxy(ResultSetMetaData.class, (name, values) -> switch (name) {
                    case "getColumnCount" -> columns.size();
                    case "getColumnLabel" -> columns.get((Integer) values[0] - 1);
                    default -> throw new UnsupportedOperationException(name);
                });
                case "close" -> { resultClosed = true; yield null; }
                default -> throw new UnsupportedOperationException(method);
            });
        }
        interface Call { Object invoke(String method, Object[] args) throws Exception; }
        <T> T proxy(Class<T> type, Call call) {
            return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                    (object, method, args) -> call.invoke(method.getName(), args)));
        }
    }

    @Test void traceabilityBindsBothKeysPreservesNullAndClosesResources() throws Exception {
        Capture jdbc = new Capture(); TableData result = new ResearchDao(jdbc::open).traceability(new SampleKey(7, 3));
        assertEquals(Map.of(1, 7, 2, 3), jdbc.parameters);
        assertTrue(jdbc.sql.contains("d.speciesID = ? AND d.sampleID = ?"));
        assertEquals(Arrays.asList(7, 3, null), result.rows().get(0));
        assertTrue(jdbc.connectionClosed && jdbc.statementClosed && jdbc.resultClosed);
        assertThrows(UnsupportedOperationException.class, () -> result.rows().get(0).set(0, 8));
    }
    @Test void timelineBindsSpeciesAndWhitelistsChronologicalDirection() throws Exception {
        for (boolean newest : new boolean[]{false, true}) {
            Capture jdbc = new Capture(); new ResearchDao(jdbc::open).timeline(7, newest);
            assertEquals(Map.of(1, 7), jdbc.parameters);
            assertTrue(jdbc.sql.contains("a.changed_at " + (newest ? "DESC" : "ASC")));
            assertTrue(jdbc.sql.contains("Species_Status_Audit"));
        }
    }
    @Test void aggregationFiltersBindUserValuesAndRejectInvalidMinimum() throws Exception {
        Capture jdbc = new Capture(); ResearchDao dao = new ResearchDao(jdbc::open);
        dao.regionSpecies(201, 2); assertEquals(Map.of(1, 201, 2, 2), jdbc.parameters);
        assertThrows(IllegalArgumentException.class, () -> dao.regionSpecies(201, 0));
        for (ResearchDao.IssueCategory category : ResearchDao.IssueCategory.values()) {
            dao.quality(category); assertEquals(Map.of(1, category.name(), 2, category.name()), jdbc.parameters);
        }
        jdbc.parameters.clear(); dao.researcherSamples(12); assertEquals(Map.of(1, 12), jdbc.parameters);
    }
    @Test void emptyResultsAndSqlFailuresReleaseResources() throws Exception {
        Capture jdbc = new Capture(); jdbc.rows = List.of(); ResearchDao dao = new ResearchDao(jdbc::open);
        assertTrue(dao.traceability(new SampleKey(-1, -1)).rows().isEmpty());
        assertTrue(dao.species().rows().isEmpty()); assertTrue(dao.samples().rows().isEmpty());
        assertTrue(dao.regions().rows().isEmpty());
        for (ResearchDao.ContributionSort sort : ResearchDao.ContributionSort.values()) assertTrue(dao.contributions(sort).rows().isEmpty());
        jdbc.fail = true; jdbc.connectionClosed = false; jdbc.statementClosed = false;
        assertThrows(SQLException.class, () -> dao.regions());
        assertTrue(jdbc.connectionClosed && jdbc.statementClosed);
    }
    @Test void editorLookupUsesTheCompositeKeyAndHandlesMissingSample() throws Exception {
        Capture jdbc = new Capture(); jdbc.rows = List.of();
        assertTrue(new EntityDao(jdbc::open).sample(new SampleKey(7, 3)).isEmpty());
        assertEquals(Map.of(1, 7, 2, 3), jdbc.parameters);
        assertTrue(jdbc.sql.contains("`speciesID` = ? AND `sampleID` = ?"));
    }
}
