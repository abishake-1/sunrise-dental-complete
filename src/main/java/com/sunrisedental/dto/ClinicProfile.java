package com.sunrisedental.dto;

/**
 * Clinic details printed on receipts.
 *
 * @param name     clinic name
 * @param address  address line (may be empty)
 * @param phone    phone number (may be empty)
 * @param currency ISO currency code, e.g. LKR
 */
public record ClinicProfile(String name, String address, String phone, String currency) {

    /** @return a generic profile for tests and defaults */
    public static ClinicProfile defaults() {
        return new ClinicProfile("Sunrise Dental Clinic", "", "", "LKR");
    }
}
