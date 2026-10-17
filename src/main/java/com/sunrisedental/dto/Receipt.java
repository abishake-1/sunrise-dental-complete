package com.sunrisedental.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Everything printed on a receipt, captured from the bill and the records it refers to.
 * Amounts are exact {@link BigDecimal}s with two decimals.
 *
 * @param receiptNumber     RCP-000001 style number derived from the bill id
 * @param billId            bill id
 * @param issuedAt          when the bill was created
 * @param issuedBy          full name of the staff member, or "-" when unknown
 * @param appointmentNumber APT-0001
 * @param appointmentDate   appointment day
 * @param appointmentTime   appointment start
 * @param patientName       patient full name
 * @param patientPhone      patient phone
 * @param dentistName       dentist full name
 * @param treatmentCode     treatment code
 * @param treatmentName     treatment name
 * @param treatmentPrice    price of the treatment at billing time
 * @param consultationFee   consultation fee charged (zero when waived)
 * @param subtotal          treatment price plus consultation fee
 * @param billingType       STANDARD, SENIOR or INSURANCE
 * @param discountPercent   discount percentage
 * @param discountAmount    discount in money
 * @param taxPercent        tax percentage
 * @param taxAmount         tax in money
 * @param total             amount to pay
 * @param paymentStatus     UNPAID, PAID or VOID
 * @param paymentMethod     payment method, null while unpaid
 * @param paidAt            payment time, null while unpaid
 */
public record Receipt(String receiptNumber, long billId, LocalDateTime issuedAt, String issuedBy,
                      String appointmentNumber, LocalDate appointmentDate, LocalTime appointmentTime,
                      String patientName, String patientPhone, String dentistName,
                      String treatmentCode, String treatmentName, BigDecimal treatmentPrice,
                      BigDecimal consultationFee, BigDecimal subtotal, String billingType,
                      BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal taxPercent,
                      BigDecimal taxAmount, BigDecimal total, String paymentStatus, String paymentMethod,
                      LocalDateTime paidAt) {

    /**
     * Builds the receipt number of a bill.
     *
     * @param billId bill id
     * @return e.g. {@code RCP-000042}
     */
    public static String numberFor(long billId) {
        return String.format("RCP-%06d", billId);
    }
}
