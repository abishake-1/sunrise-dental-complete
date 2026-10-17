package com.sunrisedental.model;

/** Bill lifecycle; values match the {@code bills.payment_status} CHECK constraint. */
public enum PaymentStatus {
    UNPAID, PAID, VOID
}
