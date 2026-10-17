package com.sunrisedental.dao;

import com.sunrisedental.dto.ReminderCandidate;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** JDBC implementation of {@link NotificationDao}. */
public final class NotificationDaoImpl implements NotificationDao {

    private static final int MAX_REASON = 255;

    private static final String INSERT = "INSERT INTO notifications (patient_id, appointment_id, channel, type, "
            + "message) VALUES (?, ?, ?, ?, ?)";

    private static final String CANDIDATES = "SELECT a.appointment_id, a.appointment_number, a.appointment_date, "
            + "a.appointment_time, p.patient_id, CONCAT(p.first_name, ' ', p.last_name) AS patient_name, "
            + "p.email, p.phone, d.full_name AS dentist_name, t.name AS treatment_name "
            + "FROM appointments a "
            + "JOIN patients p ON p.patient_id = a.patient_id "
            + "JOIN dentists d ON d.dentist_id = a.dentist_id "
            + "JOIN treatments t ON t.treatment_id = a.treatment_id "
            + "WHERE a.appointment_date = ? AND a.status IN ('SCHEDULED', 'CONFIRMED') "
            + "AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.appointment_id = a.appointment_id "
            + "AND n.type = 'REMINDER' AND n.status = 'SENT') "
            + "ORDER BY a.appointment_time, a.appointment_id";

    @Override
    public long insertPending(Connection connection, long patientId, Long appointmentId, String channel,
                              String type, String message) {
        return JdbcHelper.insert(connection, INSERT, ps -> {
            ps.setLong(1, patientId);
            JdbcHelper.setNullableLong(ps, 2, appointmentId);
            ps.setString(3, channel);
            ps.setString(4, type);
            ps.setString(5, message);
        });
    }

    @Override
    public boolean markSent(Connection connection, long id) {
        return JdbcHelper.update(connection,
                "UPDATE notifications SET status = 'SENT', sent_at = NOW(), failure_reason = NULL "
                        + "WHERE notification_id = ? AND status = 'PENDING'",
                ps -> ps.setLong(1, id)) > 0;
    }

    @Override
    public boolean markFailed(Connection connection, long id, String reason) {
        String bounded = reason == null ? "unknown" : reason.length() > MAX_REASON
                ? reason.substring(0, MAX_REASON) : reason;
        return JdbcHelper.update(connection,
                "UPDATE notifications SET status = 'FAILED', failure_reason = ? "
                        + "WHERE notification_id = ? AND status = 'PENDING'", ps -> {
                    ps.setString(1, bounded);
                    ps.setLong(2, id);
                }) > 0;
    }

    @Override
    public List<ReminderCandidate> findReminderCandidates(Connection connection, LocalDate date) {
        return JdbcHelper.queryList(connection, CANDIDATES, ps -> ps.setObject(1, date),
                rs -> new ReminderCandidate(rs.getLong("appointment_id"), rs.getString("appointment_number"),
                        rs.getObject("appointment_date", LocalDate.class),
                        rs.getObject("appointment_time", LocalTime.class), rs.getLong("patient_id"),
                        rs.getString("patient_name"), rs.getString("email"), rs.getString("phone"),
                        rs.getString("dentist_name"), rs.getString("treatment_name")));
    }
}
