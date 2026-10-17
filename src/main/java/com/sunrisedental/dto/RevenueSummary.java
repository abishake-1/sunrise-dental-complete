package com.sunrisedental.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Revenue of the appointments of a date range (void bills excluded).
 *
 * @param from        first day
 * @param to          last day
 * @param billCount   number of bills
 * @param billed      sum of all totals
 * @param collected   sum of paid totals
 * @param outstanding sum of unpaid totals
 */
public record RevenueSummary(LocalDate from, LocalDate to, long billCount, BigDecimal billed,
                             BigDecimal collected, BigDecimal outstanding) {
}
