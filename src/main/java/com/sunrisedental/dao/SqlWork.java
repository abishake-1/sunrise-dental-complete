package com.sunrisedental.dao;

import java.sql.Connection;

/**
 * A unit of database work that returns a value.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface SqlWork<T> {

    /**
     * Runs the work on the given connection.
     *
     * @param connection the connection (inside a transaction when run by {@link TransactionManager#inTransaction})
     * @return the result
     */
    T execute(Connection connection);
}
