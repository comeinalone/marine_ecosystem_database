package com.marine.dao;

import com.marine.db.ConnectionProvider;
import com.marine.model.SampleKey;
import com.marine.model.TableData;
import java.sql.*;
import java.util.*;

/** Read-only research queries. Only enum/boolean choices form SQL; values are bound. */
public final class ResearchDao {
    private final ConnectionProvider connections;
    public ResearchDao(ConnectionProvider connections) { this.connections = connections; }

    public TableData species() throws SQLException {
        return query("SELECT speciesID, common_name, scientific_name FROM Fish_Species ORDER BY common_name, speciesID");
    }

    public TableData timeline(int speciesID, boolean newestFirst) throws SQLException {
        String direction = newestFirst ? "DESC" : "ASC";
        return query("""
                SELECT a.auditID, a.speciesID, f.common_name, a.old_status, a.new_status, a.changed_at
                FROM Species_Status_Audit a JOIN Fish_Species f ON f.speciesID = a.speciesID
                WHERE a.speciesID = ?
                ORDER BY a.changed_at %s, a.auditID %s
                """.formatted(direction, direction), speciesID);
    }

    public TableData samples() throws SQLException {
        return query("""
                SELECT d.speciesID, d.sampleID, f.common_name, d.collection_date
                FROM DNA_Sample d JOIN Fish_Species f ON f.speciesID = d.speciesID
                ORDER BY d.speciesID, d.sampleID
                """);
    }

    public TableData traceability(SampleKey key) throws SQLException {
        return query("""
                SELECT d.speciesID, d.sampleID, d.collection_date, d.gene_marker, d.DNA_quality,
                       f.common_name, f.scientific_name, f.conservation_status,
                       d.researcherID, r.first_name, r.middle_name, r.last_name,
                       r.researcher_specialization, r.email_address, r.date_of_birth,
                       d.vesselID, v.vessel_name, v.vessel_capacity, v.home_port,
                       d.locationID, l.location_name, l.ocean_region
                FROM DNA_Sample d
                LEFT JOIN Fish_Species f ON f.speciesID = d.speciesID
                LEFT JOIN Researcher r ON r.researcherID = d.researcherID
                LEFT JOIN Research_Vehicle v ON v.vesselID = d.vesselID
                LEFT JOIN Ocean_Sampling_Region l ON l.locationID = d.locationID
                WHERE d.speciesID = ? AND d.sampleID = ?
                """, key.speciesID(), key.sampleID());
    }

    public TableData regions() throws SQLException {
        // Each sample joins one species, so totals cannot be multiplied by contacts/subtypes.
        return query("""
                SELECT l.locationID, l.location_name, l.ocean_region,
                       COUNT(d.sampleID) AS total_samples,
                       COUNT(DISTINCT d.speciesID) AS distinct_species,
                       COUNT(DISTINCT CASE WHEN f.conservation_status IN ('Endangered', 'Critically Endangered')
                             THEN d.speciesID END) AS endangered_species
                FROM Ocean_Sampling_Region l
                LEFT JOIN DNA_Sample d ON d.locationID = l.locationID
                LEFT JOIN Fish_Species f ON f.speciesID = d.speciesID
                GROUP BY l.locationID, l.location_name, l.ocean_region
                ORDER BY l.location_name, l.locationID
                """);
    }

    public TableData regionSpecies(int locationID, int minimumSamples) throws SQLException {
        if (minimumSamples < 1) throw new IllegalArgumentException("Minimum samples must be positive");
        return query("""
                SELECT f.speciesID, f.common_name, f.scientific_name, f.conservation_status,
                       COUNT(*) AS sample_count
                FROM DNA_Sample d JOIN Fish_Species f ON f.speciesID = d.speciesID
                WHERE d.locationID = ?
                GROUP BY f.speciesID, f.common_name, f.scientific_name, f.conservation_status
                HAVING COUNT(*) >= ?
                ORDER BY sample_count DESC, f.speciesID
                """, locationID, minimumSamples);
    }

    public enum IssueCategory {
        ALL("All issues"), QUALITY("Recorded quality"), METADATA("Missing optional metadata"),
        DATES("Date warnings"), CONTENT("Content warnings"), CONSTRAINT("Constraint violations");
        private final String title;
        IssueCategory(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    // Independent flags retain multiple reasons per sample. NULL quality/status/capacity are valid.
    static final String QUALITY_SQL = """
            WITH checked AS (
                SELECT d.speciesID, d.sampleID, d.collection_date, d.DNA_quality, f.common_name,
                       CASE WHEN d.DNA_quality = 'Low' THEN 'Recorded DNA quality is Low' END AS quality_issue,
                       CASE WHEN d.DNA_quality IS NULL THEN 'DNA quality is not recorded' END AS metadata_issue,
                       CASE WHEN f.speciesID IS NOT NULL AND f.conservation_status IS NULL
                            THEN 'Linked species conservation status is not recorded' END AS status_issue,
                       CASE WHEN v.vesselID IS NOT NULL AND v.vessel_capacity IS NULL
                            THEN 'Linked vessel capacity is not recorded' END AS capacity_issue,
                       CASE WHEN d.collection_date > CURRENT_DATE THEN 'Collection date is in the future (server date)' END AS future_issue,
                       CASE WHEN d.collection_date < r.date_of_birth THEN 'Collection predates collecting researcher birth' END AS birth_issue,
                       CASE WHEN TRIM(d.gene_marker) = '' THEN 'Gene marker contains only whitespace' END AS content_issue,
                       CASE WHEN d.collection_date IS NULL OR d.collection_date < '1900-01-01'
                                  OR d.gene_marker IS NULL OR d.researcherID IS NULL
                                  OR d.vesselID IS NULL OR d.locationID IS NULL
                                  OR (d.DNA_quality IS NOT NULL AND d.DNA_quality NOT IN ('High','Medium','Low'))
                            THEN 'Stored sample violates a NOT NULL or CHECK rule' END AS constraint_issue,
                       CASE WHEN f.speciesID IS NULL OR r.researcherID IS NULL OR v.vesselID IS NULL OR l.locationID IS NULL
                            THEN 'Sample has a missing foreign-key parent' END AS reference_issue
                FROM DNA_Sample d
                LEFT JOIN Fish_Species f ON f.speciesID = d.speciesID
                LEFT JOIN Researcher r ON r.researcherID = d.researcherID
                LEFT JOIN Research_Vehicle v ON v.vesselID = d.vesselID
                LEFT JOIN Ocean_Sampling_Region l ON l.locationID = d.locationID
            ), issues AS (
                SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'QUALITY' AS category,
                       'Warning' AS severity, quality_issue AS reason FROM checked WHERE quality_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'METADATA', 'Warning', metadata_issue FROM checked WHERE metadata_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'METADATA', 'Warning', status_issue FROM checked WHERE status_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'METADATA', 'Warning', capacity_issue FROM checked WHERE capacity_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'DATES', 'Warning', future_issue FROM checked WHERE future_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'DATES', 'Warning', birth_issue FROM checked WHERE birth_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'CONTENT', 'Warning', content_issue FROM checked WHERE content_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'CONSTRAINT', 'Constraint violation', constraint_issue FROM checked WHERE constraint_issue IS NOT NULL
                UNION ALL SELECT speciesID, sampleID, common_name, collection_date, DNA_quality, 'CONSTRAINT', 'Constraint violation', reference_issue FROM checked WHERE reference_issue IS NOT NULL
            )
            SELECT * FROM issues WHERE (? = 'ALL' OR category = ?)
            ORDER BY speciesID, sampleID, category, reason
            """;

    public TableData quality(IssueCategory category) throws SQLException {
        Objects.requireNonNull(category);
        return query(QUALITY_SQL, category.name(), category.name());
    }

    public enum ContributionSort { SAMPLES, SPECIES, ACTIVITY }
    public TableData contributions(ContributionSort sort) throws SQLException {
        String order = switch (sort) {
            case SAMPLES -> "sample_count DESC";
            case SPECIES -> "distinct_species DESC, sample_count DESC";
            case ACTIVITY -> "most_recent_collection DESC, sample_count DESC";
        };
        return query("""
                WITH participation AS (
                    SELECT r.researcherID, CONCAT_WS(' ', r.first_name, r.middle_name, r.last_name) AS researcher,
                           r.researcher_specialization, COUNT(d.sampleID) AS sample_count,
                           COUNT(DISTINCT d.speciesID) AS distinct_species,
                           COUNT(DISTINCT d.locationID) AS distinct_regions,
                           MIN(d.collection_date) AS first_collection,
                           MAX(d.collection_date) AS most_recent_collection
                    FROM Researcher r LEFT JOIN DNA_Sample d ON d.researcherID = r.researcherID
                    GROUP BY r.researcherID, r.first_name, r.middle_name, r.last_name, r.researcher_specialization
                )
                SELECT *, DENSE_RANK() OVER (ORDER BY sample_count DESC) AS sample_count_rank
                FROM participation ORDER BY %s, researcherID
                """.formatted(order));
    }

    public TableData researcherSamples(int researcherID) throws SQLException {
        return query("""
                SELECT d.speciesID, d.sampleID, f.common_name, d.collection_date, d.gene_marker, d.DNA_quality,
                       d.locationID, l.location_name, d.vesselID, v.vessel_name
                FROM DNA_Sample d JOIN Fish_Species f ON f.speciesID = d.speciesID
                JOIN Ocean_Sampling_Region l ON l.locationID = d.locationID
                JOIN Research_Vehicle v ON v.vesselID = d.vesselID
                WHERE d.researcherID = ? ORDER BY d.collection_date, d.speciesID, d.sampleID
                """, researcherID);
    }

    private TableData query(String sql, Object... parameters) throws SQLException {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            try (ResultSet result = statement.executeQuery()) {
                List<String> columns = new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
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
}
