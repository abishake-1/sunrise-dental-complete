package com.sunrisedental.pattern;

import java.math.BigDecimal;

/**
 * Facts a billing strategy may need.
 *
 * @param patientAge               age of the patient on the appointment day
 * @param insuranceCoveragePercent insurer's share requested by the caller, may be null
 */
public record BillingContext(int patientAge, BigDecimal insuranceCoveragePercent) {
}
