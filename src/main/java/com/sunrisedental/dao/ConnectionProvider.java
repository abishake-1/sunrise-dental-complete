package com.sunrisedental.dao;

import java.sql.Connection;

/** Source of JDBC connections; lets tests replace the real database. */
@FunctionalInterface
public interface ConnectionProvider {

    /**
     * Opens a new connection. The caller must close it.
     *
     * @return an open connection with auto-commit enabled
     */
    Connection getConnection();
}
