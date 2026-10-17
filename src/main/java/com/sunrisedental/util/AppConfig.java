package com.sunrisedental.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Typed, validated access to {@code config.properties} (business settings only).
 * Secrets such as database credentials are never stored here; they come from environment variables.
 */
public final class AppConfig {

    /** Name of the classpath resource. */
    public static final String RESOURCE_NAME = "config.properties";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Properties properties;

    /**
     * Creates a configuration over the given properties (useful in tests).
     *
     * @param properties the raw properties
     */
    public AppConfig(Properties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Loads {@code config.properties} from the classpath.
     *
     * @return the configuration
     * @throws IllegalStateException if the file is missing or unreadable
     */
    public static AppConfig load() {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE_NAME + " not found on the classpath");
            }
            Properties properties = new Properties();
            properties.load(in);
            return new AppConfig(properties);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE_NAME, e);
        }
    }

    /** @return the consultation fee (2 decimals, never negative) */
    public BigDecimal consultationFee() {
        BigDecimal fee = decimal("clinic.consultation.fee");
        if (fee.signum() < 0 || fee.scale() > 2) {
            throw new IllegalStateException(
                    "clinic.consultation.fee must be a non-negative amount with at most 2 decimals");
        }
        return fee.setScale(2);
    }

    /**
     * @return the {@code treatments.code} of the treatment that already includes the consultation
     *         (no extra consultation fee is charged for it); blank when nothing is waived
     */
    public String consultationTreatmentCode() {
        return optional("clinic.consultation.treatment.code", "");
    }

    /** @return the clinic name printed on receipts */
    public String clinicName() {
        return optional("clinic.name", "Sunrise Dental Clinic");
    }

    /** @return the clinic address printed on receipts (may be empty) */
    public String clinicAddress() {
        return optional("clinic.address", "");
    }

    /** @return the clinic phone printed on receipts (may be empty) */
    public String clinicPhone() {
        return optional("clinic.phone", "");
    }

    /** @return the ISO currency code shown on receipts, e.g. LKR */
    public String currency() {
        String code = optional("clinic.currency", "LKR").toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalStateException("clinic.currency must be a 3-letter currency code");
        }
        return code;
    }

    /** @return the receipt file formats to write, lower case (txt, html) */
    public List<String> receiptFormats() {
        return Arrays.stream(optional("receipts.formats", "txt,html").split(","))
                .map(String::trim).filter(f -> !f.isEmpty()).map(f -> f.toLowerCase(Locale.ROOT)).toList();
    }

    /** @return whether the daily reminder job should run */
    public boolean remindersEnabled() {
        return Boolean.parseBoolean(optional("reminders.enabled", "true"));
    }

    /** @return the daily time the reminder job runs */
    public LocalTime reminderRunTime() {
        try {
            return LocalTime.parse(optional("reminders.run.time", "18:00"));
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("reminders.run.time must be HH:mm", e);
        }
    }

    /** @return the days of the week the clinic works (default Monday to Saturday) */
    public Set<DayOfWeek> workingDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : optional("clinic.working.days", "MON,TUE,WED,THU,FRI,SAT").split(",")) {
            String name = token.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            days.add(Arrays.stream(DayOfWeek.values()).filter(d -> d.name().startsWith(name.substring(0, Math.min(3, name.length()))))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Unknown day in clinic.working.days: " + token)));
        }
        if (days.isEmpty()) {
            throw new IllegalStateException("clinic.working.days must list at least one day");
        }
        return days;
    }

    /** @return the tax percentage (0..100) */
    public BigDecimal taxPercent() {
        return percent("billing.tax.percent");
    }

    /** @return the senior discount percentage (0..100) */
    public BigDecimal seniorDiscountPercent() {
        return percent("billing.senior.discount.percent");
    }

    /** @return the minimum age (inclusive) for the senior discount */
    public int seniorMinAge() {
        try {
            int age = Integer.parseInt(text("billing.senior.min.age"));
            if (age < 1 || age > 120) {
                throw new IllegalStateException("billing.senior.min.age must be between 1 and 120");
            }
            return age;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("billing.senior.min.age is not a whole number", e);
        }
    }

    /** @return the default insurance coverage percentage (0..100) */
    public BigDecimal defaultInsuranceCoveragePercent() {
        return percent("billing.insurance.default.coverage.percent");
    }

    private String optional(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private String text(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value.trim();
    }

    private BigDecimal decimal(String key) {
        try {
            return new BigDecimal(text(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " is not a number", e);
        }
    }

    private BigDecimal percent(String key) {
        BigDecimal value = decimal(key);
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalStateException(key + " must be between 0 and 100");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
