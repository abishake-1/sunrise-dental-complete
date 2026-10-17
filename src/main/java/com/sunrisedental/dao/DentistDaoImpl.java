package com.sunrisedental.dao;

import com.sunrisedental.model.Dentist;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link DentistDao}. */
public final class DentistDaoImpl implements DentistDao {

    private static final String SELECT = "SELECT dentist_id, full_name, specialization, license_number, phone, "
            + "email, work_start, work_end, is_active, created_at, updated_at FROM dentists";

    @Override
    public Optional<Dentist> findById(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE dentist_id = ?",
                ps -> ps.setLong(1, id), DentistDaoImpl::map);
    }

    @Override
    public Optional<Dentist> findByIdForUpdate(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE dentist_id = ? FOR UPDATE",
                ps -> ps.setLong(1, id), DentistDaoImpl::map);
    }

    @Override
    public List<Dentist> findAllActive(Connection connection) {
        return JdbcHelper.queryList(connection, SELECT + " WHERE is_active = 1 ORDER BY full_name",
                JdbcHelper.NO_PARAMS, DentistDaoImpl::map);
    }

    private static Dentist map(ResultSet rs) throws SQLException {
        Dentist d = new Dentist();
        d.setId(rs.getLong("dentist_id"));
        d.setFullName(rs.getString("full_name"));
        d.setSpecialization(rs.getString("specialization"));
        d.setLicenseNumber(rs.getString("license_number"));
        d.setPhone(rs.getString("phone"));
        d.setEmail(rs.getString("email"));
        d.setWorkStart(rs.getObject("work_start", LocalTime.class));
        d.setWorkEnd(rs.getObject("work_end", LocalTime.class));
        d.setActive(rs.getBoolean("is_active"));
        d.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        d.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return d;
    }
}
