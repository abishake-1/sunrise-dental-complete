package com.sunrisedental.pattern;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Published after a booking has been committed.
 *
 * @param appointmentId     new appointment id
 * @param appointmentNumber APT-nnnn
 * @param patientId         patient id
 * @param patientName       patient full name
 * @param patientEmail      patient e-mail, null when unknown
 * @param dentistName       dentist full name
 * @param treatmentName     treatment name
 * @param date              appointment day
 * @param time              appointment start time
 * @param performedBy       login name of the staff member who booked
 */
public record AppointmentCreatedEvent(long appointmentId, String appointmentNumber, long patientId,
                                      String patientName, String patientEmail, String dentistName,
                                      String treatmentName, LocalDate date, LocalTime time,
                                      String performedBy) implements DomainEvent {
}
