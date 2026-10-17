package com.sunrisedental.service;

import java.math.BigDecimal;

/**
 * The exact amounts of a bill; every value has two decimals and
 * {@code total = subtotal - discountAmount + taxAmount} always holds.
 *
 * @param treatmentPrice  treatment price
 * @param consultationFee consultation fee (zero when waived)
 * @param subtotal        treatment price plus consultation fee
 * @param discountPercent discount percentage
 * @param discountAmount  discount in money
 * @param taxPercent      tax percentage
 * @param taxAmount       tax in money
 * @param total           amount to pay
 */
public record BillAmounts(BigDecimal treatmentPrice, BigDecimal consultationFee, BigDecimal subtotal,
                          BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal taxPercent,
                          BigDecimal taxAmount, BigDecimal total) {
}
