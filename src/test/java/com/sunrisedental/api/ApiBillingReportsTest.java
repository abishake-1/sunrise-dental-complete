package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ApiBillingReportsTest {

    private static String reception(TestApi api) throws Exception {
        return TestApi.bearer(api.login("reception", "reception123"));
    }

    private static String admin(TestApi api) throws Exception {
        return TestApi.bearer(api.login("admin", "admin123"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> errorFields(TestApi.Reply reply) {
        return ((List<Object>) reply.error().get("details")).stream()
                .map(d -> (String) ((Map<String, Object>) d).get("field")).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ billing

    @Test
    void creatingABillWithoutBodyReturnsTheReceiptAndWritesTheFiles() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply reply = api.send("POST", "/api/appointments/10/bill", null, "Authorization", reception(api));
            assertEquals(201, reply.status());
            assertEquals("/api/bills/7", reply.header("Location"));
            assertTrue(reply.body().contains("\"total\":97.75"), reply.body());
            assertEquals("STANDARD", api.facade.lastBillRequest.billingType());
            assertEquals(List.of("RCP-000007.txt", "RCP-000007.html"), reply.json().get("files"));
            String txt = Files.readString(api.receiptsDir.resolve("RCP-000007.txt"));
            assertTrue(txt.contains("TOTAL LKR") && txt.contains("97.75"));
            assertTrue(Files.readString(api.receiptsDir.resolve("RCP-000007.html")).contains("<!DOCTYPE html>"));
        }
    }

    @Test
    void billingTypeAndCoverageArePassedOnAndValidated() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = reception(api);
            assertEquals(201, api.send("POST", "/api/appointments/10/bill",
                    "{\"billingType\":\"insurance\",\"insuranceCoveragePercent\":60}", "Authorization", auth).status());
            assertEquals("INSURANCE", api.facade.lastBillRequest.billingType());
            assertEquals("60.00", api.facade.lastBillRequest.insuranceCoveragePercent().toPlainString());

            TestApi.Reply bad = api.send("POST", "/api/appointments/10/bill",
                    "{\"billingType\":\"GOLD\",\"insuranceCoveragePercent\":150,\"x\":1}", "Authorization", auth);
            assertEquals(400, bad.status());
            assertTrue(errorFields(bad).containsAll(List.of("billingType", "insuranceCoveragePercent", "x")));
            assertEquals(400, api.send("POST", "/api/appointments/abc/bill", null, "Authorization", auth).status());
        }
    }

    @Test
    void duplicateAndMissingAppointmentsMapTo409And404() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = reception(api);
            TestApi.Reply duplicate = api.send("POST", "/api/appointments/409/bill", null, "Authorization", auth);
            assertEquals(409, duplicate.status());
            assertEquals("CONFLICT", duplicate.error().get("code"));
            assertEquals(404, api.send("POST", "/api/appointments/404/bill", null, "Authorization", auth).status());
        }
    }

    @Test
    void receiptCanBeReadAsJsonTextAndHtml() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = reception(api);
            TestApi.Reply json = api.send("GET", "/api/bills/7", null, "Authorization", auth);
            assertEquals(200, json.status());
            assertEquals("RCP-000007", json.json().get("receiptNumber"));

            TestApi.Reply text = api.send("GET", "/api/bills/7?format=text", null, "Authorization", auth);
            assertEquals("text/plain; charset=utf-8", text.header("Content-Type"));
            assertTrue(text.body().startsWith("=========="));
            assertTrue(text.body().contains("TOTAL LKR"));

            TestApi.Reply html = api.send("GET", "/api/bills/7?format=html", null, "Authorization", auth);
            assertEquals("text/html; charset=utf-8", html.header("Content-Type"));
            assertEquals(1, html.headers().allValues("Content-Security-Policy").size());
            assertTrue(html.header("Content-Security-Policy").contains("style-src 'unsafe-inline'"));
            assertEquals("nosniff", html.header("X-Content-Type-Options"));

            assertEquals(400, api.send("GET", "/api/bills/7?format=pdf", null, "Authorization", auth).status());
            assertEquals(404, api.send("GET", "/api/bills/404", null, "Authorization", auth).status());
            assertEquals(400, api.send("GET", "/api/bills/abc", null, "Authorization", auth).status());
        }
    }

    @Test
    void payingARefreshesTheReceiptFiles() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = reception(api);
            TestApi.Reply paid = api.send("POST", "/api/bills/7/pay", "{\"paymentMethod\":\"cash\"}", "Authorization", auth);
            assertEquals(200, paid.status());
            assertTrue(paid.body().contains("\"status\":\"PAID\""));
            assertTrue(Files.readString(api.receiptsDir.resolve("RCP-000007.txt")).contains("Paid with"));
            assertEquals(400, api.send("POST", "/api/bills/7/pay", "{\"paymentMethod\":\"bitcoin\"}", "Authorization", auth).status());
            assertEquals(400, api.send("POST", "/api/bills/7/pay", "{}", "Authorization", auth).status());
        }
    }

    // ------------------------------------------------------------------ reports

    @Test
    void reportsAreForAdministratorsOnly() throws Exception {
        try (TestApi api = TestApi.start()) {
            String receptionist = reception(api);
            for (String slug : List.of("daily-appointments", "monthly-revenue", "top-treatments",
                    "dentist-utilization", "cancellation-rate")) {
                TestApi.Reply denied = api.send("GET", "/api/reports/" + slug, null, "Authorization", receptionist);
                assertEquals(403, denied.status(), slug);
                assertEquals("FORBIDDEN", denied.error().get("code"));
            }
            String admin = admin(api);
            for (String slug : List.of("daily-appointments", "monthly-revenue", "top-treatments",
                    "dentist-utilization", "cancellation-rate")) {
                TestApi.Reply ok = api.send("GET", "/api/reports/" + slug, null, "Authorization", admin);
                assertEquals(200, ok.status(), slug);
                assertEquals(slug, ok.json().get("report"));
                assertEquals(3L, ok.json().get("rowCount"));
            }
            assertEquals(404, api.send("GET", "/api/reports/unknown", null, "Authorization", admin).status());
        }
    }

    @Test
    void reportJsonHasColumnsRowsAndParameters() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply reply = api.send("GET", "/api/reports/top-treatments?from=2026-10-01&to=2026-10-12&limit=3", null,
                    "Authorization", admin(api));
            assertEquals("top:2026-10-01:2026-10-12:3", api.facade.lastReport);
            assertTrue(reply.body().contains("\"columns\":[\"dentistName\",\"total\",\"day\"]"));
            assertTrue(reply.body().contains("\"parameters\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-12\",\"limit\":3}"));
            assertTrue(reply.body().contains("{\"dentistName\":\"Dr. A\",\"total\":3,\"day\":\"2026-10-12\"}"));
        }
    }

    @Test
    void reportDefaultsUseTheCurrentMonthAndFiveRows() throws Exception {
        try (TestApi api = TestApi.start()) {
            String admin = admin(api);
            api.send("GET", "/api/reports/top-treatments", null, "Authorization", admin);
            assertEquals("top:2026-10-01:2026-10-11:5", api.facade.lastReport);
            api.send("GET", "/api/reports/daily-appointments", null, "Authorization", admin);
            assertEquals("daily:2026-10-11:2026-10-11", api.facade.lastReport);
            api.send("GET", "/api/reports/monthly-revenue?year=2026&month=9", null, "Authorization", admin);
            assertEquals("monthly:2026:9", api.facade.lastReport);
        }
    }

    @Test
    void csvExportIsAnExcelFriendlyDownload() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply csv = api.send("GET", "/api/reports/top-treatments?from=2026-10-01&to=2026-10-12&format=csv", null,
                    "Authorization", admin(api));
            assertEquals(200, csv.status());
            assertEquals("text/csv; charset=utf-8", csv.header("Content-Type"));
            assertEquals("attachment; filename=\"top-treatments_2026-10-01_2026-10-12_5.csv\"",
                    csv.header("Content-Disposition"));
            assertTrue(csv.body().startsWith("\uFEFFdentistName,total,day\r\n"), csv.body());
            assertTrue(csv.body().contains("Dr. A,3,2026-10-12\r\n"));
            assertTrue(csv.body().contains("\"Dr. B, Jr.\",1,\r\n"));
            assertTrue(csv.body().contains("'=cmd|' /C calc'!A0,0,\r\n"));
        }
    }

    @Test
    void reportQueriesAreValidatedAndEveryProblemIsReported() throws Exception {
        try (TestApi api = TestApi.start()) {
            String admin = admin(api);
            TestApi.Reply bad = api.send("GET",
                    "/api/reports/top-treatments?from=2026-10-12&to=2026-10-01&limit=99&format=xml&foo=1", null,
                    "Authorization", admin);
            assertEquals(400, bad.status());
            assertTrue(errorFields(bad).containsAll(List.of("to", "limit", "format", "foo")), errorFields(bad).toString());
            assertEquals(400, api.send("GET", "/api/reports/monthly-revenue?month=13", null, "Authorization", admin).status());
            assertEquals(400, api.send("GET", "/api/reports/cancellation-rate?format=text", null, "Authorization", admin).status());
            assertNull(api.facade.lastReport);
        }
    }

    // ------------------------------------------------------------------ reminders

    @Test
    void reminderRunIsAdminOnlyAndDefaultsToTomorrow() throws Exception {
        try (TestApi api = TestApi.start()) {
            assertEquals(403, api.send("POST", "/api/admin/reminders/run", null, "Authorization", reception(api)).status());

            String admin = admin(api);
            TestApi.Reply reply = api.send("POST", "/api/admin/reminders/run", null, "Authorization", admin);
            assertEquals(200, reply.status());
            assertEquals(2L, reply.json().get("candidates"));
            assertEquals(1L, reply.json().get("sent"));
            assertEquals(1L, reply.json().get("failed"));
            assertNull(api.facade.lastReminderDate);

            api.send("POST", "/api/admin/reminders/run", "{\"date\":\"2026-10-14\"}", "Authorization", admin);
            assertEquals("2026-10-14", String.valueOf(api.facade.lastReminderDate));
            assertEquals(400, api.send("POST", "/api/admin/reminders/run", "{\"date\":\"2026-10-01\"}", "Authorization", admin).status());
        }
    }
}
