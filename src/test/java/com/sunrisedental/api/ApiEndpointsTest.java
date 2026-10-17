package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ApiEndpointsTest {

    private static String auth(TestApi api) throws Exception {
        return TestApi.bearer(api.login("reception", "reception123"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> errorFields(TestApi.Reply reply) {
        List<Object> details = (List<Object>) reply.error().get("details");
        return details.stream().map(d -> (String) ((Map<String, Object>) d).get("field")).collect(Collectors.toList());
    }

    @Test
    void bookingReturns201WithLocationAndNumber() throws Exception {
        try (TestApi api = TestApi.start()) {
            String body = "{\"patientId\":5,\"dentistId\":1,\"treatmentId\":2,\"date\":\"2026-10-12\","
                    + "\"time\":\"10:00\",\"notes\":\"first visit\"}";
            TestApi.Reply reply = api.send("POST", "/api/appointments", body, "Authorization", auth(api));
            assertEquals(201, reply.status());
            assertEquals("/api/appointments/APT-0010", reply.header("Location"));
            assertEquals("APT-0010", reply.json().get("appointmentNumber"));
            assertEquals("10:00", reply.json().get("time"));
        }
    }

    @Test
    void bookingReportsEveryProblemAtOnce() throws Exception {
        try (TestApi api = TestApi.start()) {
            String body = "{\"patientId\":0,\"dentistId\":\"x\",\"date\":\"2026-10-10\",\"time\":\"19:07\",\"colour\":\"red\"}";
            TestApi.Reply reply = api.send("POST", "/api/appointments", body, "Authorization", auth(api));
            assertEquals(400, reply.status());
            assertEquals("VALIDATION_FAILED", reply.error().get("code"));
            List<String> fields = errorFields(reply);
            for (String expected : List.of("patientId", "dentistId", "treatmentId", "date", "time", "colour")) {
                assertTrue(fields.contains(expected), "missing error for " + expected + " in " + fields);
            }
            assertNotNull(reply.error().get("timestamp"));
            assertEquals("/api/appointments", reply.error().get("path"));
        }
    }

    @Test
    void doubleBookingFromTheServiceBecomes409() throws Exception {
        try (TestApi api = TestApi.start()) {
            String body = "{\"patientId\":5,\"dentistId\":1,\"treatmentId\":99,\"date\":\"2026-10-12\",\"time\":\"10:00\"}";
            TestApi.Reply reply = api.send("POST", "/api/appointments", body, "Authorization", auth(api));
            assertEquals(409, reply.status());
            assertEquals("CONFLICT", reply.error().get("code"));
        }
    }

    @Test
    void readByNumberIsCaseInsensitiveAndUnknownIs404() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = auth(api);
            assertEquals(200, api.send("GET", "/api/appointments/apt-0010", null, "Authorization", auth).status());
            assertEquals(404, api.send("GET", "/api/appointments/APT-0999", null, "Authorization", auth).status());
            assertEquals(400, api.send("GET", "/api/appointments/banana", null, "Authorization", auth).status());
        }
    }

    @Test
    void listFiltersByDateAndDentistAndRejectsBadOrUnknownParameters() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = auth(api);
            TestApi.Reply ok = api.send("GET", "/api/appointments?date=2026-10-12&dentistId=1", null, "Authorization", auth);
            assertEquals(200, ok.status());
            assertEquals(1L, ok.json().get("count"));
            assertEquals("2026-10-12", api.facade.lastDateFilter.toString());
            assertEquals(1L, api.facade.lastDentistFilter);

            api.send("GET", "/api/appointments", null, "Authorization", auth);
            assertEquals("2026-10-11", api.facade.lastDateFilter.toString()); // defaults to today

            TestApi.Reply bad = api.send("GET", "/api/appointments?date=12-10-2026&dentistId=-4&foo=1", null,
                    "Authorization", auth);
            assertEquals(400, bad.status());
            assertEquals(List.of("foo", "date", "dentistId").size(), errorFields(bad).size());
        }
    }

    @Test
    void updateEitherChangesStatusOrReschedulesButNeverBoth() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = auth(api);
            TestApi.Reply status = api.send("PUT", "/api/appointments/10", "{\"status\":\"confirmed\"}", "Authorization", auth);
            assertEquals(200, status.status());
            assertEquals("CONFIRMED", status.json().get("status"));

            TestApi.Reply moved = api.send("PUT", "/api/appointments/10",
                    "{\"date\":\"2026-10-15\",\"time\":\"14:30\",\"notes\":\"moved\"}", "Authorization", auth);
            assertEquals(200, moved.status());
            assertEquals("2026-10-15", moved.json().get("date"));

            assertEquals(400, api.send("PUT", "/api/appointments/10",
                    "{\"status\":\"CONFIRMED\",\"date\":\"2026-10-15\",\"time\":\"14:30\"}", "Authorization", auth).status());
            assertEquals(400, api.send("PUT", "/api/appointments/10", "{\"status\":\"CANCELLED\"}", "Authorization", auth).status());
            assertEquals(400, api.send("PUT", "/api/appointments/10", "{}", "Authorization", auth).status());
            assertEquals(400, api.send("PUT", "/api/appointments/abc", "{\"status\":\"CONFIRMED\"}", "Authorization", auth).status());
        }
    }

    @Test
    void referenceDataEndpointsReturnLists() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = auth(api);
            TestApi.Reply dentists = api.send("GET", "/api/dentists", null, "Authorization", auth);
            assertEquals(200, dentists.status());
            assertEquals(1L, dentists.json().get("count"));
            TestApi.Reply treatments = api.send("GET", "/api/treatments", null, "Authorization", auth);
            assertEquals(200, treatments.status());
            assertTrue(treatments.body().contains("\"price\":25.00"));
        }
    }

    @Test
    void patientValidationUsesSriLankanPhoneAndLettersOnlyNames() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = auth(api);
            String bad = "{\"firstName\":\"R2D2\",\"lastName\":\"\",\"dateOfBirth\":\"2030-01-01\",\"gender\":\"x\","
                    + "\"phone\":\"0112345678\",\"address\":\"abc\"}";
            TestApi.Reply reply = api.send("POST", "/api/patients", bad, "Authorization", auth);
            assertEquals(400, reply.status());
            List<String> fields = errorFields(reply);
            for (String expected : List.of("firstName", "lastName", "dateOfBirth", "gender", "phone", "address")) {
                assertTrue(fields.contains(expected), "missing error for " + expected + " in " + fields);
            }
            String good = "{\"firstName\":\"Nimal\",\"lastName\":\"De Silva\",\"dateOfBirth\":\"1990-05-10\","
                    + "\"gender\":\"male\",\"phone\":\"0771234567\",\"address\":\"12 Main Street\"}";
            TestApi.Reply created = api.send("POST", "/api/patients", good, "Authorization", auth);
            assertEquals(201, created.status());
            assertEquals("/api/patients/5", created.header("Location"));
        }
    }

    @Test
    void protocolErrorsUseTheCommonFormat() throws Exception {
        try (TestApi api = TestApi.start()) {
            String auth = auth(api);
            TestApi.Reply missing = api.send("GET", "/api/nothing-here", null, "Authorization", auth);
            assertEquals(404, missing.status());
            assertEquals("NOT_FOUND", missing.error().get("code"));

            TestApi.Reply wrongMethod = api.send("PATCH", "/api/appointments", "{}", "Authorization", auth);
            assertEquals(405, wrongMethod.status());
            assertTrue(wrongMethod.header("Allow").contains("GET") && wrongMethod.header("Allow").contains("POST"));

            assertEquals(415, api.send("POST", "/api/appointments", "{}", "Authorization", auth,
                    "Content-Type", "text/plain").status());
            TestApi.Reply broken = api.send("POST", "/api/appointments", "{\"a\":", "Authorization", auth);
            assertEquals(400, broken.status());
            assertEquals("INVALID_JSON", broken.error().get("code"));
            assertEquals(400, api.send("POST", "/api/appointments", "[1,2]", "Authorization", auth).status());

            TestApi.Reply outside = api.send("GET", "/elsewhere", null);
            assertEquals(404, outside.status());
            assertEquals("application/json; charset=utf-8", outside.header("Content-Type"));
        }
    }
}
