package com.sunrisedental.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Input for booking an appointment.
 *
 * @param patientId  existing patient
 * @param dentistId  active dentist
 * @param treatmentId active treatment
 * @param date       appointment day (not in the past)
 * @param time       start time on a 15-minute boundary
 * @param notes      optional free text (max 500 characters)
 */
public record BookAppointmentRequest(long patientId, long dentistId, long treatmentId,
                                     LocalDate date, LocalTime time, String notes) {
}
