package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dto.GenerateBillRequest;
import com.sunrisedental.dto.ReportQuery;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.dto.ResponseFormat;
import com.sunrisedental.model.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BillingReportValidatorsTest {

    private final RequestValidators v = new RequestValidators(Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC));

    private static List<String> fields(RequestValidationException e) {
        return e.details().stream().map(FieldError::field).collect(Collectors.toList());
    }

    @Test
    void emptyBillRequestMeansStandard() {
        GenerateBillRequest r = v.generateBill(10, Json.object());
        assertEquals("STANDARD", r.billingType());
        assertNull(r.insuranceCoveragePercent());
        assertEquals(10L, r.appointmentId());
    }

    @Test
    void insuranceCoverageIsNormalisedAndOnlyAllowedForInsurance() {
        GenerateBillRequest r = v.generateBill(10, Json.object("billingType", "insurance", "insuranceCoveragePercent", 70L));
        assertEquals("INSURANCE", r.billingType());
        assertEquals("70.00", r.insuranceCoveragePercent().toPlainString());
        assertEquals("70.50", v.generateBill(10, parse("{\"billingType\":\"INSURANCE\",\"insuranceCoveragePercent\":70.5}"))
                .insuranceCoveragePercent().toPlainString());
        assertThrows(RequestValidationException.class,
                () -> v.generateBill(10, Json.object("billingType", "SENIOR", "insuranceCoveragePercent", 50L)));
        assertThrows(RequestValidationException.class,
                () -> v.generateBill(10, Json.object("insuranceCoveragePercent", 50L)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return (Map<String, Object>) Json.parse(json);
    }

    @Test
    void billRequestReportsAllProblemsAtOnce() {
        RequestValidationException e = assertThrows(RequestValidationException.class, () -> v.generateBill(10,
                Json.object("billingType", "GOLD", "insuranceCoveragePercent", 150L, "colour", "red")));
        assertTrue(fields(e).containsAll(List.of("billingType", "insuranceCoveragePercent", "colour")));
        assertThrows(RequestValidationException.class,
                () -> v.generateBill(10, Json.object("billingType", "INSURANCE", "insuranceCoveragePercent", "lots")));
        assertThrows(RequestValidationException.class,
                () -> v.generateBill(10, Json.object("billingType", "INSURANCE", "insuranceCoveragePercent", Json.parse("70.555"))));
    }

    @Test
    void paymentMethodIsRequiredAndChecked() {
        assertEquals(PaymentMethod.CARD, v.payment(Json.object("paymentMethod", "card")));
        assertThrows(RequestValidationException.class, () -> v.payment(Json.object()));
        assertThrows(RequestValidationException.class, () -> v.payment(Json.object("paymentMethod", "bitcoin")));
        assertThrows(RequestValidationException.class, () -> v.payment(Json.object("paymentMethod", "CASH", "tip", 5L)));
    }

    @Test
    void receiptFormatDefaultsToJsonAndRejectsCsvAndPdf() {
        assertEquals(ResponseFormat.JSON, v.receiptFormat(Map.of()));
        assertEquals(ResponseFormat.TEXT, v.receiptFormat(Map.of("format", "TEXT")));
        assertEquals(ResponseFormat.HTML, v.receiptFormat(Map.of("format", "html")));
        assertThrows(RequestValidationException.class, () -> v.receiptFormat(Map.of("format", "pdf")));
        assertThrows(RequestValidationException.class, () -> v.receiptFormat(Map.of("format", "csv")));
        assertThrows(RequestValidationException.class, () -> v.receiptFormat(Map.of("x", "1")));
    }

    @Test
    void reportDefaultsAreTheCurrentMonthAndFiveRows() {
        ReportQuery top = v.reportQuery(ReportType.TOP_TREATMENTS, Map.of());
        assertEquals(LocalDate.of(2026, 10, 1), top.from());
        assertEquals(LocalDate.of(2026, 10, 11), top.to());
        assertEquals(5, top.limit());
        assertEquals(ResponseFormat.JSON, top.format());

        ReportQuery daily = v.reportQuery(ReportType.DAILY_APPOINTMENTS, Map.of());
        assertEquals(LocalDate.of(2026, 10, 11), daily.from());
        assertEquals(LocalDate.of(2026, 10, 11), daily.to());

        ReportQuery monthly = v.reportQuery(ReportType.MONTHLY_REVENUE, Map.of("format", "CSV"));
        assertEquals(2026, monthly.year());
        assertEquals(10, monthly.month());
        assertEquals(ResponseFormat.CSV, monthly.format());
        assertNull(monthly.from());
    }

    @Test
    void reportQueriesReportEveryProblem() {
        RequestValidationException e = assertThrows(RequestValidationException.class, () -> v.reportQuery(
                ReportType.TOP_TREATMENTS, Map.of("from", "2026-10-12", "to", "2026-10-01", "limit", "99", "format", "xml", "foo", "1")));
        assertTrue(fields(e).containsAll(List.of("to", "limit", "format", "foo")), fields(e).toString());
        assertThrows(RequestValidationException.class, () -> v.reportQuery(ReportType.CANCELLATION_RATE, Map.of("limit", "5")));
        assertThrows(RequestValidationException.class, () -> v.reportQuery(ReportType.MONTHLY_REVENUE, Map.of("from", "2026-10-01")));
        assertThrows(RequestValidationException.class, () -> v.reportQuery(ReportType.MONTHLY_REVENUE, Map.of("month", "13")));
        assertThrows(RequestValidationException.class, () -> v.reportQuery(ReportType.DAILY_APPOINTMENTS, Map.of("format", "text")));
        assertThrows(RequestValidationException.class, () -> v.reportQuery(ReportType.DENTIST_UTILIZATION,
                Map.of("from", "2024-01-01", "to", "2026-10-11")));
        assertThrows(RequestValidationException.class, () -> v.reportQuery(ReportType.DENTIST_UTILIZATION, Map.of("from", "yesterday")));
    }

    @Test
    void reminderDateMustBeWithinTheNext30Days() {
        assertNull(v.reminderDate(Json.object()));
        assertEquals(LocalDate.of(2026, 10, 12), v.reminderDate(Json.object("date", "2026-10-12")));
        assertEquals(LocalDate.of(2026, 10, 11), v.reminderDate(Json.object("date", "2026-10-11")));
        assertThrows(RequestValidationException.class, () -> v.reminderDate(Json.object("date", "2026-10-10")));
        assertThrows(RequestValidationException.class, () -> v.reminderDate(Json.object("date", "2026-11-11")));
        assertThrows(RequestValidationException.class, () -> v.reminderDate(Json.object("date", "tomorrow")));
        assertThrows(RequestValidationException.class, () -> v.reminderDate(Json.object("when", "2026-10-12")));
    }
}
