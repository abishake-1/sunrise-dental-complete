package com.sunrisedental.dto;

import com.sunrisedental.model.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Validated body of {@code PUT /api/appointments/{id}}: either a status change (status set, rest null)
 * or a reschedule (date and time set, status null).
 *
 * @param status new status, or null when rescheduling
 * @param date   new day, or null when changing the status
 * @param time   new start time, or null when changing the status
 * @param notes  new notes (reschedule only), null keeps the existing notes
 */
public record AppointmentUpdateRequest(AppointmentStatus status, LocalDate date, LocalTime time, String notes) {
}
