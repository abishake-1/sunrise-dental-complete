package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RequestValidatorsTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);
    private final RequestValidators validators = new RequestValidators(NOW);

    private List<String> fieldsOf(Map<String, Object> body) {
        RequestValidationException e = assertThrows(RequestValidationException.class, () -> validators.bookAppointment(body));
        return e.details().stream().map(FieldError::field).collect(Collectors.toList());
    }

    private Map<String, Object> booking(String date, String time) {
        return Json.object("patientId", 1L, "dentistId", 1L, "treatmentId", 1L, "date", date, "time", time);
    }

    @Test
    void acceptsAValidBooking() {
        assertEquals("10:00", validators.bookAppointment(booking("2026-10-12", "10:00")).time().toString());
    }

    @Test
    void timeMustBeInsideOpeningHoursOnTheQuarterHour() {
        assertTrue(fieldsOf(booking("2026-10-12", "07:45")).contains("time"));
        assertTrue(fieldsOf(booking("2026-10-12", "18:15")).contains("time"));
        assertTrue(fieldsOf(booking("2026-10-12", "10:10")).contains("time"));
        validators.bookAppointment(booking("2026-10-12", "08:00"));
        validators.bookAppointment(booking("2026-10-12", "18:00"));
    }

    @Test
    void dateMustBeInTheFuture() {
        assertTrue(fieldsOf(booking("2026-10-10", "10:00")).contains("date"));
        assertTrue(fieldsOf(booking("2026-10-11", "09:00")).contains("time")); // today, but earlier than now (10:00)
        validators.bookAppointment(booking("2026-10-11", "10:15"));           // today, later than now
        assertTrue(fieldsOf(booking("2026-02-30", "10:00")).contains("date")); // not a real date
    }

    @Test
    void patientPhoneMustBeSriLankanMobile() {
        for (String phone : List.of("0771234567", "0712345678")) {
            validators.patient(patient("Nimal", phone, "12 Main Street"));
        }
        for (String phone : List.of("077123456", "07712345678", "0112345678", "+94771234567", "07a1234567", "077 123 4567")) {
            assertThrows(RequestValidationException.class, () -> validators.patient(patient("Nimal", phone, "12 Main Street")));
        }
    }

    @Test
    void namesAcceptLettersSeparatedBySpacesHyphensAndApostrophes() {
        for (String name : List.of("Nimal", "De Silva", "Mary-Jane", "O'Connor", "Zo\u00eb", "\u0b95\u0bc1\u0bae\u0bbe\u0bb0\u0bcd", "\u0db1\u0dd2\u0db8\u0dbd\u0dca")) {
            validators.patient(patient(name, "0771234567", null));
        }
        for (String name : List.of("R2D2", "Nimal3", "-Nimal", "Nimal-", "Ni  mal", "Nimal!", "<b>x</b>")) {
            assertThrows(RequestValidationException.class, () -> validators.patient(patient(name, "0771234567", null)));
        }
    }

    @Test
    void addressLengthIsCheckedWhenPresent() {
        validators.patient(patient("Nimal", "0771234567", null));
        validators.patient(patient("Nimal", "0771234567", "12345"));
        assertThrows(RequestValidationException.class, () -> validators.patient(patient("Nimal", "0771234567", "1234")));
        assertThrows(RequestValidationException.class,
                () -> validators.patient(patient("Nimal", "0771234567", "x".repeat(256))));
    }

    @Test
    void loginRequiresBothFieldsAndKeepsThePasswordUntrimmed() {
        assertThrows(RequestValidationException.class, () -> validators.login(Json.object()));
        assertEquals(" pw ", validators.login(Json.object("username", "a", "password", " pw ")).password());
    }

    private static Map<String, Object> patient(String firstName, String phone, String address) {
        Map<String, Object> m = Json.object("firstName", firstName, "lastName", "Perera", "dateOfBirth", "1990-05-10",
                "gender", "MALE", "phone", phone);
        if (address != null) {
            m.put("address", address);
        }
        return m;
    }
}
