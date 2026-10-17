package com.sunrisedental.dto;

import java.time.LocalDate;

/**
 * Validated query parameters of a report request. Fields a report does not use are {@code null}.
 *
 * @param from   first day (inclusive)
 * @param to     last day (inclusive)
 * @param year   year of a monthly report
 * @param month  month (1-12) of a monthly report
 * @param limit  number of rows of a top-N report
 * @param format JSON or CSV
 */
public record ReportQuery(LocalDate from, LocalDate to, Integer year, Integer month, Integer limit,
                          ResponseFormat format) {
}
