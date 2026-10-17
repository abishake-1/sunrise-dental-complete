package com.sunrisedental.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Typed access to the fields of a JSON object. Wrong types, missing required fields and malformed values
 * are recorded in a {@link ValidationErrors} collector (never thrown one by one), and fields nobody asked
 * for can be reported with {@link #rejectUnknownFields()}.
 */
final class FieldReader {

    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final Map<String, Object> body;
    private final ValidationErrors errors;
    private final Set<String> used = new HashSet<>();

    FieldReader(Map<String, Object> body, ValidationErrors errors) {
        this.body = body;
        this.errors = errors;
    }

    /** @return the trimmed text, or {@code null} when absent/invalid (a problem is recorded if required) */
    String text(String key, boolean required, int maxLength) {
        String value = rawText(key, required, Integer.MAX_VALUE);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                errors.add(key, "must not be blank");
            }
            return null;
        }
        if (trimmed.length() > maxLength) {
            errors.add(key, "must be at most " + maxLength + " characters");
            return null;
        }
        return trimmed;
    }

    /** @return the text exactly as sent (used for passwords), or {@code null} */
    String rawText(String key, boolean required, int maxLength) {
        used.add(key);
        Object value = body.get(key);
        if (value == null) {
            if (required) {
                errors.add(key, "is required");
            }
            return null;
        }
        if (!(value instanceof String text)) {
            errors.add(key, "must be a string");
            return null;
        }
        if (text.length() > maxLength) {
            errors.add(key, "must be at most " + maxLength + " characters");
            return null;
        }
        return text;
    }

    /** @return a positive whole number, or {@code null} */
    Long id(String key, boolean required) {
        used.add(key);
        Object value = body.get(key);
        if (value == null) {
            if (required) {
                errors.add(key, "is required");
            }
            return null;
        }
        if (!(value instanceof Long number) || number <= 0) {
            errors.add(key, "must be a positive whole number");
            return null;
        }
        return number;
    }

    /** @return a percentage 0..100 with at most 2 decimals (scale 2), or {@code null} when absent/invalid */
    java.math.BigDecimal percent(String key) {
        used.add(key);
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        java.math.BigDecimal number;
        if (value instanceof Long whole) {
            number = java.math.BigDecimal.valueOf(whole);
        } else if (value instanceof java.math.BigDecimal decimal) {
            number = decimal;
        } else {
            errors.add(key, "must be a number between 0 and 100");
            return null;
        }
        if (number.signum() < 0 || number.compareTo(java.math.BigDecimal.valueOf(100)) > 0
                || number.stripTrailingZeros().scale() > 2) {
            errors.add(key, "must be between 0 and 100 with at most 2 decimals");
            return null;
        }
        return number.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    /** @return the date parsed from {@code yyyy-MM-dd}, or {@code null} */
    LocalDate date(String key, boolean required) {
        String text = text(key, required, 10);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            errors.add(key, "must be a valid date in yyyy-MM-dd format");
            return null;
        }
    }

    /** @return the time parsed from {@code HH:mm}, or {@code null} */
    LocalTime time(String key, boolean required) {
        String text = text(key, required, 5);
        if (text == null) {
            return null;
        }
        if (!HH_MM.matcher(text).matches()) {
            errors.add(key, "must be a valid time in HH:mm format");
            return null;
        }
        return LocalTime.parse(text);
    }

    /** @return whether the body contains the key with a non-null value (marks it as known) */
    boolean has(String key) {
        used.add(key);
        return body.get(key) != null;
    }

    /** Records one problem per field that no accessor asked for. */
    void rejectUnknownFields() {
        for (String key : body.keySet()) {
            if (!used.contains(key)) {
                errors.add(key, "is not a recognised field");
            }
        }
    }
}
