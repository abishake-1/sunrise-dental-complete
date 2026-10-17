package com.sunrisedental.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton connection provider (initialization-on-demand holder, thread-safe and lazy).
 *
 * <p>Settings come from environment variables (nothing is hard-coded in the code):
 * <ul>
 *   <li>{@code SUNRISE_DB_URL} - optional, default {@code jdbc:mysql://localhost:3306/sunrise_dental}</li>
 *   <li>{@code SUNRISE_DB_USER} - optional, default {@code root}</li>
 *   <li>{@code SUNRISE_DB_PASSWORD} - optional, default empty (set it when your MySQL user has a password)</li>
 * </ul>
 * It deliberately opens a fresh connection per call (no pooling) to stay simple; callers close them.
 */
public final class DatabaseConnectionManager implements ConnectionProvider {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/sunrise_dental";
    private static final String DEFAULT_USER = "root";

    private final String url;
    private final String user;
    private final String password;

    private DatabaseConnectionManager() {
        String configuredUrl = System.getenv("SUNRISE_DB_URL");
        this.url = configuredUrl == null || configuredUrl.isBlank() ? DEFAULT_URL : configuredUrl.trim();
        String configuredUser = System.getenv("SUNRISE_DB_USER");
        this.user = configuredUser == null || configuredUser.isBlank() ? DEFAULT_USER : configuredUser.trim();
        String configuredPassword = System.getenv("SUNRISE_DB_PASSWORD");
        this.password = configuredPassword == null ? "" : configuredPassword;
    }

    private static final class Holder {
        private static final DatabaseConnectionManager INSTANCE = new DatabaseConnectionManager();
    }

    /** @return the single instance */
    public static DatabaseConnectionManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Opens a new connection.
     *
     * @return an open connection (auto-commit on)
     * @throws DataAccessException   if the database cannot be reached
     */
    @Override
    public Connection getConnection() {
        try {
            // Register the MySQL driver explicitly (DriverManager cannot see it under Maven exec:java)
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("MySQL JDBC driver not found on the classpath");
        }
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot connect to the database", e);
        }
    }
}