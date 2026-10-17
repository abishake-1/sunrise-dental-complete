package com.sunrisedental.dto;

import java.math.BigDecimal;

/**
 * Input for generating a bill.
 *
 * @param appointmentId           the appointment to bill
 * @param billingType             STANDARD, SENIOR or INSURANCE (blank means STANDARD)
 * @param insuranceCoveragePercent insurer's share 0..100 for INSURANCE, otherwise ignored; may be null
 */
public record GenerateBillRequest(long appointmentId, String billingType, BigDecimal insuranceCoveragePercent) {
}
