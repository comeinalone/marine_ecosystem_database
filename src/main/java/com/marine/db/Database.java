package com.marine.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens a connection per operation; callers own and close it. */
public final class Database implements ConnectionProvider {
    private final DatabaseConfig config;
    public Database(DatabaseConfig config) { this.config = config; }
    @Override public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
