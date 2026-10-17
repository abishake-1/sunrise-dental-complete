package com.sunrisedental.dto;

import java.time.LocalDate;

/**
 * Outcome of one reminder run.
 *
 * @param date       the appointment day that was processed
 * @param candidates appointments that needed a reminder
 * @param sent       reminders delivered
 * @param failed     reminders that could not be delivered (logged as FAILED, retried by the next run)
 */
public record ReminderResult(LocalDate date, int candidates, int sent, int failed) {
}
