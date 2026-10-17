package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunrisedental.model.BookedSlot;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.util.ConflictException;
import com.sunrisedental.util.ValidationException;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppointmentRulesTest {

    private final List<BookedSlot> booked = List.of(new BookedSlot(LocalTime.of(10, 0), 45));

    private static Dentist dentist() {
        Dentist d = new Dentist();
        d.setFullName("Dr. Test");
        d.setWorkStart(LocalTime.of(9, 0));
        d.setWorkEnd(LocalTime.of(17, 0));
        return d;
    }

    @Test
    void overlapIsRejected() {
        assertThrows(ConflictException.class,
                () -> AppointmentService.ensureNoOverlap(booked, LocalTime.of(10, 30), 30));
        assertThrows(ConflictException.class,
                () -> AppointmentService.ensureNoOverlap(booked, LocalTime.of(9, 30), 60));
    }

    @Test
    void backToBackIsAllowed() {
        assertDoesNotThrow(() -> AppointmentService.ensureNoOverlap(booked, LocalTime.of(10, 45), 30));
        assertDoesNotThrow(() -> AppointmentService.ensureNoOverlap(booked, LocalTime.of(9, 0), 60));
    }

    @Test
    void workingHoursAreEnforced() {
        assertDoesNotThrow(() -> AppointmentService.ensureWithinWorkingHours(dentist(), LocalTime.of(16, 0), 60));
        assertThrows(ValidationException.class,
                () -> AppointmentService.ensureWithinWorkingHours(dentist(), LocalTime.of(16, 30), 60));
        assertThrows(ValidationException.class,
                () -> AppointmentService.ensureWithinWorkingHours(dentist(), LocalTime.of(8, 45), 30));
    }
}
