package com.sunrisedental.dto;

import com.sunrisedental.model.AppointmentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Day sheet plus per-status totals.
 *
 * @param date           the day
 * @param appointments   rows ordered by time
 * @param countsByStatus number of appointments per status (all statuses present, zero when none)
 */
public record DailyReport(LocalDate date, List<DailyAppointmentRow> appointments,
                          Map<AppointmentStatus, Long> countsByStatus) {
}
