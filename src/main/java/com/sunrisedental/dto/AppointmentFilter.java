package com.sunrisedental.dto;

import java.time.LocalDate;

/**
 * Validated query of {@code GET /api/appointments}.
 *
 * @param date      the day (today when not supplied)
 * @param dentistId optional dentist filter
 */
public record AppointmentFilter(LocalDate date, Long dentistId) {
}
