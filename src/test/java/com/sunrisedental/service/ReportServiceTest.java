package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dao.DataAccessException;
import com.sunrisedental.dao.ReportDao;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.util.ValidationException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    private final List<Object> calls = new ArrayList<>();
    private RuntimeException failure;

    private ReportService service(EnumSet<DayOfWeek> days) {
        ReportDao dao = Stubs.of(ReportDao.class).on("execute", args -> {
            if (failure != null) {
                throw failure;
            }
            ReportType type = (ReportType) args[1];
            calls.add(type);
            calls.add(args[2]);
            return new ReportTable(type.slug(), type.title(), Map.of(), List.of("a"), List.of(List.of(1L)));
        }).make();
        return new ReportService(new TransactionManager(Stubs.fakeConnections()), dao, CLOCK, days);
    }

    private static EnumSet<DayOfWeek> monToSat() {
        return EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
    }

    @Test
    void utilizationCountsOnlyConfiguredWorkingDays() {
        LocalDate monday = LocalDate.of(2026, 10, 12);
        ReportTable table = service(monToSat()).dentistUtilization(monday, monday.plusDays(6));
        assertEquals(List.of(ReportType.DENTIST_UTILIZATION, List.of(monday, monday.plusDays(6), 6)), calls);
        assertEquals(6, table.parameters().get("workingDays"));

        calls.clear();
        service(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).dentistUtilization(monday, monday.plusDays(6));
        assertEquals(List.of(monday, monday.plusDays(6), 5), calls.get(1));
        assertEquals(2, service(monToSat()).countWorkingDays(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 19)));
    }

    @Test
    void reportsRecordTheirParameters() {
        ReportService service = service(monToSat());
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 12);
        assertEquals(from, service.dailyAppointmentsPerDentist(from, to).parameters().get("from"));
        assertEquals(5, service.topTreatments(from, to, 5).parameters().get("limit"));
        assertEquals(10, service.monthlyRevenueByTreatment(2026, 10).parameters().get("month"));
        assertEquals(List.of(2026, 10), calls.get(calls.size() - 1));
        assertEquals(to, service.cancellationRate(from, to).parameters().get("to"));
    }

    @Test
    void invalidInputIsRejectedBeforeTheDatabaseIsTouched() {
        ReportService service = service(monToSat());
        LocalDate day = LocalDate.of(2026, 10, 12);
        assertThrows(ValidationException.class, () -> service.cancellationRate(day, day.minusDays(1)));
        assertThrows(ValidationException.class, () -> service.cancellationRate(day, day.plusDays(367)));
        assertThrows(ValidationException.class, () -> service.cancellationRate(null, day));
        assertThrows(ValidationException.class, () -> service.topTreatments(day, day, 0));
        assertThrows(ValidationException.class, () -> service.topTreatments(day, day, 51));
        assertThrows(ValidationException.class, () -> service.monthlyRevenueByTreatment(1999, 5));
        assertThrows(ValidationException.class, () -> service.monthlyRevenueByTreatment(2026, 13));
        assertTrue(calls.isEmpty());
    }

    @Test
    void procedureRejectionsBecomeValidationErrors() {
        failure = new DataAccessException("sp failed", new SQLException("Invalid date range", "45000", 1644));
        LocalDate day = LocalDate.of(2026, 10, 12);
        ValidationException e = assertThrows(ValidationException.class, () -> service(monToSat()).cancellationRate(day, day));
        assertEquals("Invalid date range", e.getMessage());
    }
}
