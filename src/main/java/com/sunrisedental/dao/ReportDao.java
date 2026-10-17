package com.sunrisedental.dao;

import com.sunrisedental.dto.DailyAppointmentRow;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.dto.RevenueSummary;
import com.sunrisedental.model.AppointmentStatus;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read-only reporting queries. */
public interface ReportDao {

    /**
     * Runs one of the report stored procedures through a CallableStatement and returns its single result set.
     *
     * @param connection connection to use
     * @param type       which report (its procedure name comes from a fixed list)
     * @param parameters the procedure arguments: {@link LocalDate} or {@link Integer} values in order
     * @return the columns (camelCase) and rows, without parameter labels
     * @throws DataAccessException with {@code isBusinessRuleViolation()} when the procedure rejects the input
     */
    ReportTable execute(Connection connection, ReportType type, List<Object> parameters);

    /**
     * Calls {@code sp_daily_appointments} (CallableStatement).
     *
     * @param connection connection to use
     * @param date       the day
     * @return the day sheet ordered by time
     */
    List<DailyAppointmentRow> dailyAppointments(Connection connection, LocalDate date);

    /**
     * Counts appointments per status in a date range.
     *
     * @param connection connection to use
     * @param from       first day
     * @param to         last day
     * @return a map with every status present
     */
    Map<AppointmentStatus, Long> countByStatus(Connection connection, LocalDate from, LocalDate to);

    /**
     * Sums bills of the appointments in a date range.
     *
     * @param connection connection to use
     * @param from       first day
     * @param to         last day
     * @return the revenue summary
     */
    RevenueSummary revenue(Connection connection, LocalDate from, LocalDate to);
}
