package com.marine.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {
    @TempDir Path directory;
    @Test void environmentOverridesFileAndSecretsAreNotPrinted() throws IOException {
        Path file = directory.resolve("database.properties");
        Files.writeString(file, "db.url=jdbc:mysql://localhost/test\ndb.user=file-user\ndb.password=file-secret\n");
        DatabaseConfig config = DatabaseConfig.load(Map.of("MARINE_DB_USER", "env-user", "MARINE_DB_PASSWORD", "env-secret"), file);
        assertEquals("env-user", config.user()); assertEquals("env-secret", config.password());
        assertEquals("jdbc:mysql://localhost/test", config.url());
        assertFalse(config.toString().contains("secret"));
    }
    @Test void missingFileHasUsefulDefaultsAndInvalidUrlIsRejected() throws IOException {
        DatabaseConfig config = DatabaseConfig.load(Map.of(), directory.resolve("absent"));
        assertEquals("marine_app", config.user()); assertEquals("", config.password());
        assertThrows(IOException.class, () -> DatabaseConfig.load(Map.of("MARINE_DB_URL", "jdbc:other:test"), directory.resolve("absent")));
    }
}
