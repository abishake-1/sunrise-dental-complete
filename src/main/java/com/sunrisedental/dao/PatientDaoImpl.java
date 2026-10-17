package com.sunrisedental.dao;

import com.sunrisedental.model.Gender;
import com.sunrisedental.model.Patient;
import com.sunrisedental.util.Validator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link PatientDao}. */
public final class PatientDaoImpl implements PatientDao {

    private static final String SELECT = "SELECT patient_id, first_name, last_name, date_of_birth, gender, phone, "
            + "email, address, medical_notes, created_at, updated_at FROM patients";
    private static final String INSERT = "INSERT INTO patients (first_name, last_name, date_of_birth, gender, "
            + "phone, email, address, medical_notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE patients SET first_name = ?, last_name = ?, date_of_birth = ?, "
            + "gender = ?, phone = ?, email = ?, address = ?, medical_notes = ? WHERE patient_id = ?";

    @Override
    public Patient insert(Connection connection, Patient patient) {
        long id = JdbcHelper.insert(connection, INSERT, ps -> bind(ps, patient));
        return findById(connection, id)
                .orElseThrow(() -> new DataAccessException("Inserted patient not found: " + id));
    }

    @Override
    public boolean update(Connection connection, Patient patient) {
        return JdbcHelper.update(connection, UPDATE, ps -> {
            bind(ps, patient);
            ps.setLong(9, patient.getId());
        }) > 0;
    }

    @Override
    public Optional<Patient> findById(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE patient_id = ?",
                ps -> ps.setLong(1, id), PatientDaoImpl::map);
    }

    @Override
    public List<Patient> search(Connection connection, String term, int limit) {
        String pattern = "%" + Validator.escapeLike(term) + "%";
        String sql = SELECT + " WHERE first_name LIKE ? OR last_name LIKE ? "
                + "OR CONCAT(first_name, ' ', last_name) LIKE ? OR phone LIKE ? "
                + "ORDER BY last_name, first_name LIMIT ?";
        return JdbcHelper.queryList(connection, sql, ps -> {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            ps.setInt(5, limit);
        }, PatientDaoImpl::map);
    }

    @Override
    public boolean delete(Connection connection, long id) {
        return JdbcHelper.update(connection, "DELETE FROM patients WHERE patient_id = ?",
                ps -> ps.setLong(1, id)) > 0;
    }

    private static void bind(PreparedStatement ps, Patient p) throws SQLException {
        ps.setString(1, p.getFirstName());
        ps.setString(2, p.getLastName());
        ps.setObject(3, p.getDateOfBirth());
        ps.setString(4, p.getGender().name());
        ps.setString(5, p.getPhone());
        JdbcHelper.setNullableString(ps, 6, p.getEmail());
        JdbcHelper.setNullableString(ps, 7, p.getAddress());
        JdbcHelper.setNullableString(ps, 8, p.getMedicalNotes());
    }

    private static Patient map(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getLong("patient_id"));
        p.setFirstName(rs.getString("first_name"));
        p.setLastName(rs.getString("last_name"));
        p.setDateOfBirth(rs.getObject("date_of_birth", LocalDate.class));
        p.setGender(Gender.valueOf(rs.getString("gender")));
        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setAddress(rs.getString("address"));
        p.setMedicalNotes(rs.getString("medical_notes"));
        p.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        p.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return p;
    }
}
