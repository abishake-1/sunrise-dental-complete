package com.sunrisedental.dao;

import java.sql.Connection;

/** Data access for {@code audit_log} and the audit identity of the connection. */
public interface AuditDao {

    /**
     * Sets the MySQL session variable {@code @app_user} so the triggers attribute changes to the staff member.
     * The variable lives as long as the connection; connections must not be shared between users.
     *
     * @param connection connection to use
     * @param username   the staff member's login name
     */
    void setApplicationUser(Connection connection, String username);

    /**
     * Writes an application-level event to {@code audit_log}.
     *
     * @param connection  connection to use
     * @param tableName   logical source, e.g. "appointment_events"
     * @param recordId    id of the affected record
     * @param action      INSERT, UPDATE or DELETE
     * @param eventName   event name stored in the JSON payload
     * @param detail      free-text detail stored in the JSON payload
     * @param performedBy staff login name
     */
    void insertEvent(Connection connection, String tableName, long recordId, String action, String eventName,
                     String detail, String performedBy);
}
