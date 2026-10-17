package com.sunrisedental.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.util.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AppointmentBuilderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC);

    private AppointmentBuilder valid() {
        return new AppointmentBuilder(CLOCK).patientId(1).dentistId(2).treatmentId(3).createdBy(4)
                .date(LocalDate.of(2026, 10, 12)).time(LocalTime.of(10, 15));
    }

    @Test
    void buildsScheduledAppointment() {
        Appointment a = valid().notes("  first visit ").build();
        assertEquals(AppointmentStatus.SCHEDULED, a.getStatus());
        assertEquals("first visit", a.getNotes());
    }

    @Test
    void allowsToday() {
        valid().date(LocalDate.of(2026, 10, 11)).build();
    }

    @Test
    void rejectsPastDate() {
        assertThrows(ValidationException.class, () -> valid().date(LocalDate.of(2026, 10, 10)).build());
    }

    @Test
    void rejectsOffGridTime() {
        assertThrows(ValidationException.class, () -> valid().time(LocalTime.of(10, 10)).build());
    }

    @Test
    void rejectsMissingValues() {
        assertThrows(ValidationException.class, () -> new AppointmentBuilder(CLOCK).build());
    }
}
