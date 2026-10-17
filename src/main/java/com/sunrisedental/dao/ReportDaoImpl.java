package com.sunrisedental.dao;

import com.sunrisedental.dto.DailyAppointmentRow;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.dto.RevenueSummary;
import com.sunrisedental.model.AppointmentStatus;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** JDBC implementation of {@link ReportDao}. */
public final class ReportDaoImpl implements ReportDao {

    private static final String COUNT_BY_STATUS = "SELECT status, COUNT(*) AS total FROM appointments "
            + "WHERE appointment_date BETWEEN ? AND ? GROUP BY status";

    private static final String REVENUE = "SELECT COUNT(*) AS bill_count, "
            + "COALESCE(SUM(b.total_amount), 0) AS billed, "
            + "COALESCE(SUM(CASE WHEN b.payment_status = 'PAID' THEN b.total_amount END), 0) AS collected, "
            + "COALESCE(SUM(CASE WHEN b.payment_status = 'UNPAID' THEN b.total_amount END), 0) AS outstanding "
            + "FROM bills b JOIN appointments a ON a.appointment_id = b.appointment_id "
            + "WHERE a.appointment_date BETWEEN ? AND ? AND b.payment_status <> 'VOID'";

    @Override
    public ReportTable execute(Connection connection, ReportType type, List<Object> parameters) {
        if (parameters.size() != type.parameterCount()) {
            throw new IllegalArgumentException(type.slug() + " needs " + type.parameterCount() + " parameters");
        }
        // The procedure name comes from the ReportType enum, never from user input.
        String call = "{CALL " + type.procedure() + "(" + "?,".repeat(parameters.size() - 1) + "?)}";
        try (CallableStatement statement = connection.prepareCall(call)) {
            for (int i = 0; i < parameters.size(); i++) {
                Object value = parameters.get(i);
                if (value instanceof LocalDate date) {
                    statement.setDate(i + 1, Date.valueOf(date));
                } else if (value instanceof Integer number) {
                    statement.setInt(i + 1, number);
                } else {
                    throw new IllegalArgumentException("Unsupported report parameter: " + value);
                }
            }
            List<String> columns = new ArrayList<>();
            List<List<Object>> rows = new ArrayList<>();
            if (statement.execute()) {
                try (ResultSet rs = statement.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        columns.add(toCamelCase(meta.getColumnLabel(i)));
                    }
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>(columns.size());
                        for (int i = 1; i <= columns.size(); i++) {
                            row.add(convert(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                }
            }
            return new ReportTable(type.slug(), type.title(), Map.of(), columns, rows);
        } catch (SQLException e) {
            throw new DataAccessException(type.procedure() + " failed", e);
        }
    }

    static String toCamelCase(String snake) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    private static Object convert(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value;
    }

    @Override
    public List<DailyAppointmentRow> dailyAppointments(Connection connection, LocalDate date) {
        try (CallableStatement call = connection.prepareCall("{CALL sp_daily_appointments(?)}")) {
            call.setDate(1, Date.valueOf(date));
            List<DailyAppointmentRow> rows = new ArrayList<>();
            if (call.execute()) {
                try (ResultSet rs = call.getResultSet()) {
                    while (rs.next()) {
                        rows.add(mapRow(rs));
                    }
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new DataAccessException("sp_daily_appointments failed", e);
        }
    }

    @Override
    public Map<AppointmentStatus, Long> countByStatus(Connection connection, LocalDate from, LocalDate to) {
        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            counts.put(status, 0L);
        }
        try (PreparedStatement ps = connection.prepareStatement(COUNT_BY_STATUS)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(AppointmentStatus.valueOf(rs.getString("status")), rs.getLong("total"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Counting appointments failed", e);
        }
        return counts;
    }

    @Override
    public RevenueSummary revenue(Connection connection, LocalDate from, LocalDate to) {
        return JdbcHelper.queryOne(connection, REVENUE, ps -> {
            ps.setObject(1, from);
            ps.setObject(2, to);
        }, rs -> new RevenueSummary(from, to, rs.getLong("bill_count"), rs.getBigDecimal("billed"),
                rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding")))
                .orElseThrow(() -> new DataAccessException("Revenue query returned no row"));
    }

    private static DailyAppointmentRow mapRow(ResultSet rs) throws SQLException {
        return new DailyAppointmentRow(
                rs.getString("appointment_number"),
                rs.getObject("appointment_date", LocalDate.class),
                rs.getObject("appointment_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getString("status"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("dentist_name"),
                rs.getString("treatment_name"),
                rs.getInt("duration_minutes"),
                rs.getBigDecimal("price"));
    }
}
