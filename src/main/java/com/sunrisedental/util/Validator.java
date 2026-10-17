package com.sunrisedental.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Stateless input validation helpers. All failures raise {@link ValidationException}. */
public final class Validator {

    private static final Pattern PHONE = Pattern.compile("^[0-9+() -]{7,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final int MAX_EMAIL = 120;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Validator() {
    }

    /**
     * Requires a non-blank text of bounded length.
     *
     * @param value     raw value
     * @param field     field name for error messages
     * @param maxLength maximum length after trimming
     * @return the trimmed value
     */
    public static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    /**
     * Validates an optional text.
     *
     * @param value     raw value, may be null or blank
     * @param field     field name for error messages
     * @param maxLength maximum length after trimming
     * @return the trimmed value, or {@code null} when blank
     */
    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    /**
     * Validates a phone number (digits, spaces, + ( ) -; 7 to 20 characters).
     *
     * @param value raw value
     * @return the trimmed phone number
     */
    public static String requirePhone(String value) {
        String phone = requireText(value, "phone", 20);
        if (!PHONE.matcher(phone).matches()) {
            throw new ValidationException("phone must contain 7-20 digits, spaces or + ( ) -");
        }
        return phone;
    }

    /**
     * Validates an optional e-mail address.
     *
     * @param value raw value, may be null or blank
     * @return the trimmed address, or {@code null} when blank
     */
    public static String optionalEmail(String value) {
        String email = optionalText(value, "email", MAX_EMAIL);
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new ValidationException("email is not a valid address");
        }
        return email;
    }

    /**
     * Requires a positive identifier.
     *
     * @param value the identifier, may be null
     * @param field field name for error messages
     * @return the identifier as a primitive
     */
    public static long requireId(Long value, String field) {
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
        if (value <= 0) {
            throw new ValidationException(field + " must be a positive number");
        }
        return value;
    }

    /**
     * Requires a non-null value.
     *
     * @param value the value
     * @param field field name for error messages
     * @param <T>   value type
     * @return the same value
     */
    public static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
        return value;
    }

    /**
     * Requires a percentage between 0 and 100 inclusive.
     *
     * @param value the percentage
     * @param field field name for error messages
     * @return the same value
     */
    public static BigDecimal requirePercent(BigDecimal value, String field) {
        requireNonNull(value, field);
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new ValidationException(field + " must be between 0 and 100");
        }
        return value;
    }

    /**
     * Escapes the LIKE wildcards of a user-supplied search term.
     *
     * @param term raw search term
     * @return the term with backslash, percent and underscore escaped
     */
    public static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
