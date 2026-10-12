package com.marine.dao;

import com.marine.model.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A JDBC test double verifies parameter binding and closure, not MySQL behavior. */
class EntityDaoTest {
    private static final class JdbcCapture {
        String sql;
        final Map<Integer, Object> bindings = new TreeMap<>();
        boolean connectionClosed, statementClosed, fail;

        Connection open() {
            PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setObject", "setDate": bindings.put((Integer) args[0], args[1]); return null;
                            case "executeUpdate": if (fail) throw new SQLException("Simulated rejection", "23000", 1451); return 1;
                            case "close": statementClosed = true; return null;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "prepareStatement": sql = (String) args[0]; return statement;
                            case "close": connectionClosed = true; return null;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
    private EntityRow sample() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("speciesID", 301); values.put("sampleID", 1);
        values.put("collection_date", LocalDate.of(2026, 1, 12));
        values.put("gene_marker", "COI'); DELETE FROM DNA_Sample; --"); values.put("DNA_quality", null);
        values.put("researcherID", 1); values.put("vesselID", 101); values.put("locationID", 201);
        return new EntityRow(EntityType.SAMPLE, values);
    }
    @Test void deleteUsesBothKeysAndClosesResources() throws SQLException {
        JdbcCapture jdbc = new JdbcCapture(); new EntityDao(jdbc::open).delete(sample());
        assertEquals("DELETE FROM `DNA_Sample` WHERE `speciesID` = ? AND `sampleID` = ?", jdbc.sql);
        assertEquals(Map.of(1, 301, 2, 1), jdbc.bindings);
        assertTrue(jdbc.statementClosed); assertTrue(jdbc.connectionClosed);
    }
    @Test void updateBindsNewValuesAndOriginalCompositeKey() throws SQLException {
        JdbcCapture jdbc = new JdbcCapture(); EntityRow sample = sample(); new EntityDao(jdbc::open).update(sample, sample);
        assertTrue(jdbc.sql.endsWith("WHERE `speciesID` = ? AND `sampleID` = ?"));
        assertFalse(jdbc.sql.contains("DELETE FROM"));
        assertEquals(sample.get("gene_marker"), jdbc.bindings.get(2));
        assertNull(jdbc.bindings.get(3));
        assertEquals(java.sql.Date.valueOf("2026-01-12"), jdbc.bindings.get(1));
        assertEquals(301, jdbc.bindings.get(7)); assertEquals(1, jdbc.bindings.get(8));
    }
    @Test void databaseFailureStillClosesStatementAndConnection() {
        JdbcCapture jdbc = new JdbcCapture(); jdbc.fail = true;
        assertThrows(SQLException.class, () -> new EntityDao(jdbc::open).delete(sample()));
        assertTrue(jdbc.connectionClosed); assertTrue(jdbc.statementClosed);
    }
    @Test void contactUpdateUsesOriginalThreePartKey() throws SQLException {
        EntityRow original = new EntityRow(EntityType.CONTACT,
                Map.of("researcherID", 1, "contact_type", "Work", "phone_number", "123"));
        EntityRow changed = new EntityRow(EntityType.CONTACT,
                Map.of("researcherID", 2, "contact_type", "Home", "phone_number", "456"));
        JdbcCapture jdbc = new JdbcCapture(); new EntityDao(jdbc::open).update(original, changed);
        assertEquals(Map.of(1, 2, 2, "Home", 3, "456", 4, 1, 5, "Work", 6, "123"), jdbc.bindings);
        assertTrue(jdbc.sql.endsWith("WHERE `researcherID` = ? AND `contact_type` = ? AND `phone_number` = ?"));
    }
}
