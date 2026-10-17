package com.sunrisedental.model;

import com.sunrisedental.util.ValidationException;
import java.util.Locale;

/** The billing schemes the clinic offers. */
public enum BillingType {
    STANDARD, SENIOR, INSURANCE;

    /**
     * Parses a client-supplied name.
     *
     * @param value case-insensitive name; null or blank means {@link #STANDARD}
     * @return the matching type
     * @throws ValidationException for unknown names
     */
    public static BillingType parse(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("billingType must be STANDARD, SENIOR or INSURANCE");
        }
    }
}
