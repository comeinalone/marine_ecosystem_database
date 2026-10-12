package com.marine.db;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public record DatabaseConfig(String url, String user, String password) {
    public static DatabaseConfig load() throws IOException {
        return load(System.getenv(), Path.of("config", "database.properties"));
    }

    public static DatabaseConfig load(Map<String, String> environment, Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) { properties.load(reader); }
        }
        String url = environment.getOrDefault("MARINE_DB_URL", properties.getProperty("db.url",
                "jdbc:mysql://localhost:3306/Marine_Ecosystem_Database?connectTimeout=5000&socketTimeout=15000"));
        String user = environment.getOrDefault("MARINE_DB_USER", properties.getProperty("db.user", "marine_app"));
        String password = environment.getOrDefault("MARINE_DB_PASSWORD", properties.getProperty("db.password", ""));
        if (!url.startsWith("jdbc:mysql://")) throw new IOException("MARINE_DB_URL must be a MySQL JDBC URL.");
        if (user.isBlank()) throw new IOException("Set MARINE_DB_USER or db.user.");
        return new DatabaseConfig(url, user, password);
    }

    // Prevent accidental logging of credentials through record.toString().
    @Override public String toString() { return "DatabaseConfig[credentials hidden]"; }
}
