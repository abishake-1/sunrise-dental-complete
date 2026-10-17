package com.sunrisedental.dao;

import java.sql.Connection;

/** A unit of database work without a result. */
@FunctionalInterface
public interface SqlAction {

    /**
     * Runs the action on the given connection.
     *
     * @param connection the connection
     */
    void execute(Connection connection);
}
