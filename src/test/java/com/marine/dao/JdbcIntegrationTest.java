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

/** Opt-in ONLY: requires an initialized disposable database and write privileges. */
@EnabledIfEnvironmentVariable(named = "MARINE_RUN_DB_TESTS", matches = "true")
class JdbcIntegrationTest {
    @Test void realJdbcCrudRoutinesReportsAndAuditRollBackTogether() throws Exception {
        try (Connection connection = new Database(DatabaseConfig.load()).open()) {
            connection.setAutoCommit(false);
            // DAOs normally own their connection. During this test they borrow a
            // transaction-scoped connection; only the outer try closes it.
            ConnectionProvider borrowed = () -> (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        try { return method.invoke(connection, args); }
                        catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
            try {
                EntityDao dao = new EntityDao(borrowed);
                EntityRow species = new EntityRow(EntityType.SPECIES, Map.of(
                        "speciesID", 920001, "common_name", "Integration Species",
                        "scientific_name", "Integrationus rollbackus", "conservation_status", "Least Concern"));
                dao.insert(species);
                EntityRow sample = new EntityRow(EntityType.SAMPLE, Map.of(
                        "speciesID", 920001, "sampleID", 1, "collection_date", LocalDate.of(2026, 1, 1),
                        "gene_marker", "COI", "DNA_quality", "High", "researcherID", 1, "vesselID", 101, "locationID", 201));
                dao.insert(sample);
                Map<String, Object> changed = new LinkedHashMap<>(sample.values()); changed.put("gene_marker", "16S");
                dao.update(sample, new EntityRow(EntityType.SAMPLE, changed));
                assertTrue(dao.list(EntityType.SAMPLE).stream().anyMatch(row ->
                        row.get("speciesID").equals(920001) && row.get("sampleID").equals(1) && row.get("gene_marker").equals("16S")));
                try (PreparedStatement statement = connection.prepareStatement("SELECT CountSpeciesSamples(?)")) {
                    statement.setInt(1, 920001);
                    try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); assertEquals(1, result.getLong(1)); }
                }
                try (CallableStatement call = connection.prepareCall("{CALL AddDNASample(?,?,?,?,?,?,?,?)}")) {
                    call.setInt(1, 920001); call.setInt(2, 2); call.setDate(3, java.sql.Date.valueOf("2026-01-02"));
                    call.setString(4, "COI"); call.setNull(5, Types.VARCHAR); call.setInt(6, 1); call.setInt(7, 101); call.setInt(8, 201);
                    call.execute();
                }
                try (CallableStatement call = connection.prepareCall("{CALL UpdateSpeciesStatus(?,?)}")) {
                    call.setInt(1, 920001); call.setString(2, "Endangered"); call.execute();
                }
                ReportDao reports = new ReportDao(borrowed);
                assertTrue(reports.report(ReportDao.Report.ENDANGERED).rows().stream().anyMatch(row -> row.get(0).equals(920001)));
                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM Species_Status_Audit WHERE speciesID=?")) {
                    statement.setInt(1, 920001);
                    try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); assertEquals(1, result.getLong(1)); }
                }
                dao.delete(sample);
                // Deleting (920001,1) must retain the independent baseline (301,1).
                assertTrue(dao.list(EntityType.SAMPLE).stream().anyMatch(row -> row.get("speciesID").equals(301) && row.get("sampleID").equals(1)));
                assertThrows(SQLException.class, () -> dao.delete(species)); // sample 2 and audit retain FK references
            } finally {
                connection.rollback();
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM Fish_Species WHERE speciesID=?")) {
                statement.setInt(1, 920001);
                try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); assertEquals(0, result.getLong(1)); }
            }
        }
    }
}
