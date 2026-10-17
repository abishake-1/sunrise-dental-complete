package com.sunrisedental.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** One line of the day sheet returned by {@code sp_daily_appointments}. */
public record DailyAppointmentRow(String appointmentNumber, LocalDate date, LocalTime time, LocalTime endTime,
                                  String status, String patientName, String patientPhone, String dentistName,
                                  String treatmentName, int durationMinutes, BigDecimal price) {
}
