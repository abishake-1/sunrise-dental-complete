package com.sunrisedental.dao;

import com.sunrisedental.model.Treatment;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link TreatmentDao}. */
public final class TreatmentDaoImpl implements TreatmentDao {

    private static final String SELECT = "SELECT treatment_id, code, name, description, duration_minutes, price, "
            + "is_active, created_at, updated_at FROM treatments";

    @Override
    public Optional<Treatment> findById(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE treatment_id = ?",
                ps -> ps.setLong(1, id), TreatmentDaoImpl::map);
    }

    @Override
    public List<Treatment> findAllActive(Connection connection) {
        return JdbcHelper.queryList(connection, SELECT + " WHERE is_active = 1 ORDER BY name",
                JdbcHelper.NO_PARAMS, TreatmentDaoImpl::map);
    }

    @Override
    public boolean updatePrice(Connection connection, long id, BigDecimal price) {
        return JdbcHelper.update(connection, "UPDATE treatments SET price = ? WHERE treatment_id = ?", ps -> {
            ps.setBigDecimal(1, price);
            ps.setLong(2, id);
        }) > 0;
    }

    private static Treatment map(ResultSet rs) throws SQLException {
        Treatment t = new Treatment();
        t.setId(rs.getLong("treatment_id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setDescription(rs.getString("description"));
        t.setDurationMinutes(rs.getInt("duration_minutes"));
        t.setPrice(rs.getBigDecimal("price"));
        t.setActive(rs.getBoolean("is_active"));
        t.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        t.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return t;
    }
}
