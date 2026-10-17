package com.sunrisedental.dao;

import java.sql.Connection;

/** JDBC implementation of {@link AuditDao}. */
public final class AuditDaoImpl implements AuditDao {

    private static final String INSERT_EVENT = "INSERT INTO audit_log (table_name, record_id, action, new_values, "
            + "performed_by) VALUES (?, ?, ?, JSON_OBJECT('event', ?, 'detail', ?), ?)";

    @Override
    public void setApplicationUser(Connection connection, String username) {
        JdbcHelper.update(connection, "SET @app_user = ?", ps -> ps.setString(1, username));
    }

    @Override
    public void insertEvent(Connection connection, String tableName, long recordId, String action,
                            String eventName, String detail, String performedBy) {
        JdbcHelper.update(connection, INSERT_EVENT, ps -> {
            ps.setString(1, tableName);
            ps.setLong(2, recordId);
            ps.setString(3, action);
            ps.setString(4, eventName);
            ps.setString(5, detail);
            ps.setString(6, performedBy);
        });
    }
}
