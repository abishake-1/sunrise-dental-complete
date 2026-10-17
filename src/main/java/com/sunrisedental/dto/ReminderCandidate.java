package com.sunrisedental.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An appointment that still needs a reminder.
 *
 * @param appointmentId     appointment id
 * @param appointmentNumber APT-0001
 * @param date              appointment day
 * @param time              appointment start
 * @param patientId         patient id
 * @param patientName       patient full name
 * @param patientEmail      patient e-mail, null when unknown
 * @param patientPhone      patient phone
 * @param dentistName       dentist full name
 * @param treatmentName     treatment name
 */
public record ReminderCandidate(long appointmentId, String appointmentNumber, LocalDate date, LocalTime time,
                                long patientId, String patientName, String patientEmail, String patientPhone,
                                String dentistName, String treatmentName) {
}
