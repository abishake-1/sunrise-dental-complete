package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ApiSecurityTest {

    private static final String LOGIN = "/api/auth/login";

    @Test
    void loginReturnsABearerTokenAndNeverThePassword() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply reply = api.send("POST", LOGIN, "{\"username\":\"admin\",\"password\":\"admin123\"}");
            assertEquals(200, reply.status());
            assertEquals("Bearer", reply.json().get("tokenType"));
            assertNotNull(reply.json().get("token"));
            assertFalse(reply.body().contains("must-never-leak"));
            assertFalse(reply.body().contains("admin123"));
            assertEquals(null, reply.header("Set-Cookie"));
        }
    }

    @Test
    void wrongPasswordGivesAGenericError() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply reply = api.send("POST", LOGIN, "{\"username\":\"admin\",\"password\":\"nope\"}");
            assertEquals(401, reply.status());
            assertEquals("INVALID_CREDENTIALS", reply.error().get("code"));
            TestApi.Reply unknown = api.send("POST", LOGIN, "{\"username\":\"ghost\",\"password\":\"nope\"}");
            assertEquals(reply.error().get("message"), unknown.error().get("message"));
            TestApi.Reply wrongCase = api.send("POST", LOGIN, "{\"username\":\"admin\",\"password\":\"ADMIN123\"}");
            assertEquals(401, wrongCase.status());
        }
    }

    @Test
    void thereIsNoLockoutAndTheCorrectPasswordAlwaysWorks() throws Exception {
        try (TestApi api = TestApi.start()) {
            String bad = "{\"username\":\"other\",\"password\":\"wrong\"}";
            for (int i = 1; i <= 8; i++) {
                assertEquals(401, api.send("POST", LOGIN, bad).status());
            }
            assertEquals(200, api.send("POST", LOGIN, "{\"username\":\"other\",\"password\":\"other123\"}").status());
        }
    }

    @Test
    void everyApiRouteExceptLoginRequiresAuthentication() throws Exception {
        try (TestApi api = TestApi.start()) {
            String[][] routes = {{"GET", "/api/dentists"}, {"GET", "/api/treatments"}, {"GET", "/api/appointments"},
                {"GET", "/api/appointments/APT-0010"}, {"POST", "/api/appointments"}, {"PUT", "/api/appointments/10"},
                {"DELETE", "/api/appointments/10"}, {"GET", "/api/auth/me"}, {"POST", "/api/auth/logout"},
                {"GET", "/api/health"}, {"GET", "/api/patients?search=a"}, {"GET", "/api/does-not-exist"},
                {"POST", "/api/appointments/10/bill"}, {"GET", "/api/bills/7"}, {"POST", "/api/bills/7/pay"},
                {"GET", "/api/reports/top-treatments"}, {"GET", "/api/reports/monthly-revenue"},
                {"POST", "/api/admin/reminders/run"}};
            for (String[] route : routes) {
                String body = "POST".equals(route[0]) || "PUT".equals(route[0]) ? "{}" : null;
                TestApi.Reply reply = api.send(route[0], route[1], body);
                assertEquals(401, reply.status(), route[0] + " " + route[1]);
                assertEquals("UNAUTHORIZED", reply.error().get("code"));
                assertNotNull(reply.header("WWW-Authenticate"));
            }
        }
    }

    @Test
    void bearerTokenWorksAndLogoutEndsTheSession() throws Exception {
        try (TestApi api = TestApi.start()) {
            String token = api.login("admin", "admin123");
            assertEquals(200, api.send("GET", "/api/dentists", null, "Authorization", TestApi.bearer(token)).status());
            assertEquals(401, api.send("GET", "/api/dentists", null, "Cookie", "SUNRISE_SESSION=" + token).status());
            assertEquals(401, api.send("GET", "/api/dentists", null, "Authorization", "Bearer not-a-token").status());
            assertEquals(401, api.send("GET", "/api/dentists", null, "Authorization", "Basic abc").status());

            TestApi.Reply logout = api.send("POST", "/api/auth/logout", null, "Authorization", TestApi.bearer(token));
            assertEquals(204, logout.status());
            assertEquals(401, api.send("GET", "/api/dentists", null, "Authorization", TestApi.bearer(token)).status());
        }
    }

    @Test
    void aSessionLastsUntilLogoutWithNoTimeout() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = TestApi.bearer(api.login("admin", "admin123"));
            api.clock.advance(Duration.ofDays(3));
            assertEquals(200, api.send("GET", "/api/auth/me", null, "Authorization", auth).status());
        }
    }

    @Test
    void receptionistsMayOnlyCancelTheirOwnAppointments() throws Exception {
        try (TestApi api = TestApi.start()) {
            String other = TestApi.bearer(api.login("other", "other123"));
            String owner = TestApi.bearer(api.login("reception", "reception123"));
            String admin = TestApi.bearer(api.login("admin", "admin123"));
            assertEquals(403, api.send("DELETE", "/api/appointments/10", null, "Authorization", other).status());
            assertEquals(200, api.send("DELETE", "/api/appointments/10", null, "Authorization", owner).status());
            TestApi.Reply byAdmin = api.send("DELETE", "/api/appointments/10", null, "Authorization", admin);
            assertEquals(200, byAdmin.status());
            assertEquals("CANCELLED", byAdmin.json().get("status"));
        }
    }

    @Test
    void reportsAreForAdministratorsOnly() throws Exception {
        try (TestApi api = TestApi.start()) {
            String staff = TestApi.bearer(api.login("reception", "reception123"));
            assertEquals(403, api.send("GET", "/api/reports/top-treatments?from=2026-10-01&to=2026-10-31&limit=5",
                    null, "Authorization", staff).status());
        }
    }

    @Test
    void browserRequestsWithAnOriginHeaderWork() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply reply = api.send("POST", LOGIN, "{\"username\":\"admin\",\"password\":\"admin123\"}",
                    "Origin", "http://localhost:8081");
            assertEquals(200, reply.status());
        }
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        try (TestApi api = TestApi.start()) {
            TestApi.Reply unauth = api.send("GET", "/api/dentists", null);
            assertEquals("nosniff", unauth.header("X-Content-Type-Options"));
            assertEquals("DENY", unauth.header("X-Frame-Options"));
            assertEquals("no-store", unauth.header("Cache-Control"));
            assertNotNull(unauth.header("Content-Security-Policy"));
            assertTrue(unauth.header("Content-Security-Policy").contains("frame-ancestors 'none'"));
        }
    }
}
