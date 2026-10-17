package com.sunrisedental.service;

import com.sunrisedental.dao.DataAccessException;
import com.sunrisedental.dao.ReportDao;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dto.DailyReport;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.dto.RevenueSummary;
import com.sunrisedental.util.ValidationException;
import com.sunrisedental.util.Validator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only reports for the reception desk and the administrator. */
public final class ReportService {

    private static final long MAX_RANGE_DAYS = 366;

    private final TransactionManager transactions;
    private final ReportDao reportDao;
    private final Clock clock;
    private final Set<DayOfWeek> workingDays;

    /**
     * Creates the service.
     *
     * @param transactions connection handling
     * @param reportDao    report queries
     * @param clock        source of "today"
     * @param workingDays  the days the clinic works (for the utilisation report)
     */
    public ReportService(TransactionManager transactions, ReportDao reportDao, Clock clock,
                         Set<DayOfWeek> workingDays) {
        this.transactions = transactions;
        this.reportDao = reportDao;
        this.clock = clock;
        this.workingDays = Set.copyOf(workingDays);
    }

    /**
     * Appointments per dentist per day, split by status.
     *
     * @param from first day
     * @param to   last day
     * @return the report table
     */
    public ReportTable dailyAppointmentsPerDentist(LocalDate from, LocalDate to) {
        requireRange(from, to);
        return run(ReportType.DAILY_APPOINTMENTS, List.of(from, to), range(from, to));
    }

    /**
     * Revenue of one month by treatment.
     *
     * @param year  year (2000-2100)
     * @param month month (1-12)
     * @return the report table
     */
    public ReportTable monthlyRevenueByTreatment(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ValidationException("year must be 2000-2100 and month 1-12");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("year", year);
        parameters.put("month", month);
        return run(ReportType.MONTHLY_REVENUE, List.of(year, month), parameters);
    }

    /**
     * The most booked treatments.
     *
     * @param from  first day
     * @param to    last day
     * @param limit number of rows (1-50)
     * @return the report table
     */
    public ReportTable topTreatments(LocalDate from, LocalDate to, int limit) {
        requireRange(from, to);
        if (limit < 1 || limit > 50) {
            throw new ValidationException("limit must be between 1 and 50");
        }
        Map<String, Object> parameters = range(from, to);
        parameters.put("limit", limit);
        return run(ReportType.TOP_TREATMENTS, List.of(from, to, limit), parameters);
    }

    /**
     * Booked against available minutes per dentist; available time counts the configured working days only.
     *
     * @param from first day
     * @param to   last day
     * @return the report table
     */
    public ReportTable dentistUtilization(LocalDate from, LocalDate to) {
        requireRange(from, to);
        int days = countWorkingDays(from, to);
        Map<String, Object> parameters = range(from, to);
        parameters.put("workingDays", days);
        return run(ReportType.DENTIST_UTILIZATION, List.of(from, to, days), parameters);
    }

    /**
     * Cancellation and no-show rates per dentist and overall.
     *
     * @param from first day
     * @param to   last day
     * @return the report table
     */
    public ReportTable cancellationRate(LocalDate from, LocalDate to) {
        requireRange(from, to);
        return run(ReportType.CANCELLATION_RATE, List.of(from, to), range(from, to));
    }

    private ReportTable run(ReportType type, List<Object> arguments, Map<String, Object> parameters) {
        try {
            return transactions.read(c -> reportDao.execute(c, type, arguments)).withParameters(parameters);
        } catch (DataAccessException e) {
            if (e.isBusinessRuleViolation()) {
                throw new ValidationException(e.getSqlMessage());
            }
            throw e;
        }
    }

    int countWorkingDays(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (workingDays.contains(day.getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, Object> range(LocalDate from, LocalDate to) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("from", from);
        parameters.put("to", to);
        return parameters;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        Validator.requireNonNull(from, "from");
        Validator.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new ValidationException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ValidationException("The range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    /**
     * Builds the day sheet (via {@code sp_daily_appointments}) and per-status totals.
     *
     * @param date the day, {@code null} means today
     * @return the report
     */
    public DailyReport dailyReport(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        return transactions.read(c -> new DailyReport(day, reportDao.dailyAppointments(c, day),
                reportDao.countByStatus(c, day, day)));
    }

    /**
     * Summarises billed, collected and outstanding amounts.
     *
     * @param from first day
     * @param to   last day (at most 366 days after {@code from})
     * @return the summary
     * @throws ValidationException if the range is invalid
     */
    public RevenueSummary revenueSummary(LocalDate from, LocalDate to) {
        Validator.requireNonNull(from, "from");
        Validator.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new ValidationException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ValidationException("The range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return transactions.read(c -> reportDao.revenue(c, from, to));
    }
}
