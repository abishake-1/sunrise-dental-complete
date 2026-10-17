package com.sunrisedental.dao;

import java.sql.SQLException;

/**
 * Unchecked wrapper for {@link SQLException}. It keeps the SQL state and vendor code so the service
 * layer can translate well-known failures (duplicate key, foreign key, trigger/procedure rule) into
 * business exceptions without parsing messages.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int DUPLICATE_ENTRY = 1062;
    private static final int ROW_IS_REFERENCED = 1451;
    private static final int NO_REFERENCED_ROW = 1452;
    private static final int CHECK_VIOLATED = 3819;
    private static final String USER_DEFINED_STATE = "45000";

    private final String sqlState;
    private final int vendorCode;
    private final String sqlMessage;

    /**
     * Creates an exception that has no SQL cause.
     *
     * @param message description of the failure
     */
    public DataAccessException(String message) {
        super(message);
        this.sqlState = null;
        this.vendorCode = 0;
        this.sqlMessage = null;
    }

    /**
     * Wraps a JDBC failure.
     *
     * @param message description of the failed operation
     * @param cause   the original exception
     */
    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
        this.sqlState = cause.getSQLState();
        this.vendorCode = cause.getErrorCode();
        this.sqlMessage = cause.getMessage();
    }

    /** @return {@code true} for a UNIQUE / PRIMARY KEY violation */
    public boolean isDuplicateKey() {
        return vendorCode == DUPLICATE_ENTRY;
    }

    /** @return {@code true} when a foreign key blocks a delete or an insert */
    public boolean isForeignKeyViolation() {
        return vendorCode == ROW_IS_REFERENCED || vendorCode == NO_REFERENCED_ROW;
    }

    /** @return {@code true} when a CHECK constraint rejected the data */
    public boolean isCheckViolation() {
        return vendorCode == CHECK_VIOLATED;
    }

    /** @return {@code true} when a trigger or stored procedure raised SQLSTATE 45000 via SIGNAL */
    public boolean isBusinessRuleViolation() {
        return USER_DEFINED_STATE.equals(sqlState);
    }

    /** @return the message text produced by the database (the SIGNAL text for rule violations), or null */
    public String getSqlMessage() {
        return sqlMessage;
    }
}
