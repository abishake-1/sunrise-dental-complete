package com.sunrisedental.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Runs units of work on a dedicated connection. Write transactions commit on success and roll back on
 * any exception; the connection is always closed. Write transactions use READ COMMITTED so that, once
 * a dentist row has been locked, later reads in the same transaction see every committed booking.
 */
public final class TransactionManager {

    private final ConnectionProvider provider;

    /**
     * Creates the manager.
     *
     * @param provider where connections come from
     */
    public TransactionManager(ConnectionProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Runs read-only (or single-statement) work in auto-commit mode.
     *
     * @param work the work
     * @param <T>  result type
     * @return the work's result
     */
    public <T> T read(SqlWork<T> work) {
        try (Connection connection = provider.getConnection()) {
            return work.execute(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database access failed", e);
        }
    }

    /**
     * Runs the work atomically.
     *
     * @param work the work
     * @param <T>  result type
     * @return the work's result, after a successful commit
     */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = provider.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | Error e) {
                rollbackQuietly(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Transaction failed", e);
        }
    }

    /**
     * Runs an action atomically.
     *
     * @param action the action
     */
    public void runInTransaction(SqlAction action) {
        inTransaction(connection -> {
            action.execute(connection);
            return null;
        });
    }

    private static void rollbackQuietly(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
