package com.sunrisedental.dao;

import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.BookedSlot;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link AppointmentDao}. */
public final class AppointmentDaoImpl implements AppointmentDao {

    private static final String SELECT = "SELECT appointment_id, appointment_number, patient_id, dentist_id, "
            + "treatment_id, appointment_date, appointment_time, status, notes, created_by, created_at, "
            + "updated_at FROM appointments";

    /**
     * The number column is NOT NULL with a format CHECK. The BEFORE INSERT trigger always overwrites this
     * placeholder with the real APT-nnnn value; sending one keeps the statement valid on every server mode.
     */
    private static final String NUMBER_PLACEHOLDER = "APT-0000";

    private static final String INSERT = "INSERT INTO appointments (appointment_number, patient_id, dentist_id, "
            + "treatment_id, appointment_date, appointment_time, status, notes, created_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public Appointment insert(Connection connection, Appointment a) {
        long id = JdbcHelper.insert(connection, INSERT, ps -> {
            ps.setString(1, NUMBER_PLACEHOLDER);
            ps.setLong(2, a.getPatientId());
            ps.setLong(3, a.getDentistId());
            ps.setLong(4, a.getTreatmentId());
            ps.setObject(5, a.getAppointmentDate());
            ps.setObject(6, a.getAppointmentTime());
            ps.setString(7, a.getStatus().name());
            JdbcHelper.setNullableString(ps, 8, a.getNotes());
            ps.setLong(9, a.getCreatedBy());
        });
        return findById(connection, id)
                .orElseThrow(() -> new DataAccessException("Inserted appointment not found: " + id));
    }

    @Override
    public Optional<Appointment> findById(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE appointment_id = ?",
                ps -> ps.setLong(1, id), AppointmentDaoImpl::map);
    }

    @Override
    public Optional<Appointment> findByIdForUpdate(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE appointment_id = ? FOR UPDATE",
                ps -> ps.setLong(1, id), AppointmentDaoImpl::map);
    }

    @Override
    public Optional<Appointment> findByNumber(Connection connection, String number) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE appointment_number = ?",
                ps -> ps.setString(1, number), AppointmentDaoImpl::map);
    }

    @Override
    public boolean reschedule(Connection connection, long id, LocalDate date, LocalTime time, String notes) {
        String sql = "UPDATE appointments SET appointment_date = ?, appointment_time = ?, notes = ? "
                + "WHERE appointment_id = ?";
        return JdbcHelper.update(connection, sql, ps -> {
            ps.setObject(1, date);
            ps.setObject(2, time);
            JdbcHelper.setNullableString(ps, 3, notes);
            ps.setLong(4, id);
        }) > 0;
    }

    @Override
    public List<Appointment> findByPatient(Connection connection, long patientId) {
        return JdbcHelper.queryList(connection,
                SELECT + " WHERE patient_id = ? ORDER BY appointment_date DESC, appointment_time DESC",
                ps -> ps.setLong(1, patientId), AppointmentDaoImpl::map);
    }

    @Override
    public List<Appointment> findByDate(Connection connection, LocalDate date) {
        return JdbcHelper.queryList(connection,
                SELECT + " WHERE appointment_date = ? ORDER BY appointment_time, appointment_id",
                ps -> ps.setObject(1, date), AppointmentDaoImpl::map);
    }

    @Override
    public List<BookedSlot> findActiveSlots(Connection connection, long dentistId, LocalDate date,
                                            long excludeAppointmentId) {
        String sql = "SELECT a.appointment_time, t.duration_minutes FROM appointments a "
                + "JOIN treatments t ON t.treatment_id = a.treatment_id "
                + "WHERE a.dentist_id = ? AND a.appointment_date = ? AND a.appointment_id <> ? "
                + "AND a.status NOT IN ('CANCELLED', 'NO_SHOW') ORDER BY a.appointment_time";
        return JdbcHelper.queryList(connection, sql, ps -> {
            ps.setLong(1, dentistId);
            ps.setObject(2, date);
            ps.setLong(3, excludeAppointmentId);
        }, rs -> new BookedSlot(rs.getObject("appointment_time", LocalTime.class), rs.getInt("duration_minutes")));
    }

    @Override
    public boolean updateStatus(Connection connection, long id, AppointmentStatus status) {
        return JdbcHelper.update(connection, "UPDATE appointments SET status = ? WHERE appointment_id = ?", ps -> {
            ps.setString(1, status.name());
            ps.setLong(2, id);
        }) > 0;
    }

    private static Appointment map(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getLong("appointment_id"));
        a.setAppointmentNumber(rs.getString("appointment_number"));
        a.setPatientId(rs.getLong("patient_id"));
        a.setDentistId(rs.getLong("dentist_id"));
        a.setTreatmentId(rs.getLong("treatment_id"));
        a.setAppointmentDate(rs.getObject("appointment_date", LocalDate.class));
        a.setAppointmentTime(rs.getObject("appointment_time", LocalTime.class));
        a.setStatus(AppointmentStatus.valueOf(rs.getString("status")));
        a.setNotes(rs.getString("notes"));
        a.setCreatedBy(rs.getLong("created_by"));
        a.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        a.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return a;
    }
}
