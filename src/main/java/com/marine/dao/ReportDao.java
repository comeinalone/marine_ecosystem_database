package com.marine.dao;

import com.marine.db.ConnectionProvider;
import com.marine.model.TableData;
import java.sql.*;
import java.util.*;

public final class ReportDao {
    public enum Report {
        ENDANGERED("Endangered species", "EndangeredSpeciesView", "speciesID"),
        SPECIES("Species sample summary", "SpeciesSampleSummary", "speciesID"),
        RESEARCHERS("Researcher activity", "ResearcherSampleReport", "researcherID"),
        LOCATIONS("Sampling location summary", "SamplingLocationReport", "locationID"),
        AUDIT("Conservation status history", "Species_Status_Audit", "auditID");
        private final String title, table, order;
        Report(String title, String table, String order) { this.title = title; this.table = table; this.order = order; }
        @Override public String toString() { return title; }
    }
    private final ConnectionProvider connections;
    public ReportDao(ConnectionProvider connections) { this.connections = connections; }

    public TableData report(Report report) throws SQLException {
        return query("SELECT * FROM `" + report.table + "` ORDER BY `" + report.order + "`");
    }

    public Map<String, Long> dashboard() throws SQLException {
        // One statement gives a consistent snapshot of all four dashboard counts.
        String sql = "SELECT (SELECT COUNT(*) FROM Researcher) AS researchers, "
                + "(SELECT COUNT(*) FROM Fish_Species) AS species, "
                + "(SELECT COUNT(*) FROM DNA_Sample) AS samples, "
                + "(SELECT COUNT(*) FROM EndangeredSpeciesView) AS endangered";
        TableData data = query(sql);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < data.columns().size(); i++)
            counts.put(data.columns().get(i), ((Number) data.rows().get(0).get(i)).longValue());
        return counts;
    }

    private TableData query(String sql) throws SQLException {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            ResultSetMetaData metadata = result.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) columns.add(metadata.getColumnLabel(i));
            List<List<Object>> rows = new ArrayList<>();
            while (result.next()) {
                List<Object> values = new ArrayList<>();
                for (int i = 1; i <= columns.size(); i++) values.add(result.getObject(i));
                rows.add(values);
            }
            return new TableData(columns, rows);
        }
    }
}
