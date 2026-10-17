package com.sunrisedental.api;

import com.sunrisedental.dto.AppointmentFilter;
import com.sunrisedental.dto.AppointmentUpdateRequest;
import com.sunrisedental.dto.BookAppointmentRequest;
import com.sunrisedental.dto.GenerateBillRequest;
import com.sunrisedental.dto.LoginRequest;
import com.sunrisedental.dto.ReportQuery;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.dto.ResponseFormat;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.BillingType;
import com.sunrisedental.model.Gender;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates request bodies and query strings. Every method inspects <em>all</em> fields and reports
 * <em>all</em> problems at once through a {@link RequestValidationException}.
 *
 * <p>Rules:
 * <ul>
 *   <li>phone: Sri Lankan mobile format {@code 07XXXXXXXX} (10 digits);</li>
 *   <li>names: letters of any alphabet (including the combining marks Tamil and Sinhala need), optionally
 *       separated by single spaces, hyphens or apostrophes;</li>
 *   <li>date/time: the appointment moment must be in the future and the start time between 08:00 and 18:00
 *       on a 15-minute boundary;</li>
 *   <li>address: 5 to 255 characters when supplied.</li>
 * </ul>
 */
public final class RequestValidators {

    /** Earliest accepted start time of an appointment. */
    public static final LocalTime OPENING = LocalTime.of(8, 0);
    /** Latest accepted start time of an appointment. */
    public static final LocalTime CLOSING = LocalTime.of(18, 0);

    private static final Pattern PHONE = Pattern.compile("^07\\d{8}$");
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M}]*(?:[ '\\-]\\p{L}[\\p{L}\\p{M}]*)*$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final int MAX_PASSWORD = 128;

    private final Clock clock;

    /**
     * Creates the validators.
     *
     * @param clock source of the current date and time
     */
    public RequestValidators(Clock clock) {
        this.clock = clock;
    }

    /**
     * Validates login credentials.
     *
     * @param body the JSON body
     * @return the credentials
     */
    public LoginRequest login(Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        String username = in.text("username", true, 50);
        String password = in.rawText("password", true, MAX_PASSWORD);
        if (password != null && password.isEmpty()) {
            errors.add("password", "must not be empty");
        }
        in.rejectUnknownFields();
        errors.throwIfAny();
        return new LoginRequest(username, password);
    }

    /**
     * Validates a booking request.
     *
     * @param body the JSON body
     * @return the request
     */
    public BookAppointmentRequest bookAppointment(Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        Long patientId = in.id("patientId", true);
        Long dentistId = in.id("dentistId", true);
        Long treatmentId = in.id("treatmentId", true);
        LocalDate date = in.date("date", true);
        LocalTime time = in.time("time", true);
        String notes = in.text("notes", false, 500);
        checkSchedule(errors, date, time);
        in.rejectUnknownFields();
        errors.throwIfAny();
        return new BookAppointmentRequest(patientId, dentistId, treatmentId, date, time, notes);
    }

    /**
     * Validates an update: either {@code status}, or {@code date} and {@code time} (with optional notes).
     * Cancelling goes through DELETE and is therefore not accepted as a status here.
     *
     * @param body the JSON body
     * @return the request
     */
    public AppointmentUpdateRequest appointmentUpdate(Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        boolean hasStatus = in.has("status");
        boolean hasDate = in.has("date");
        boolean hasTime = in.has("time");
        in.has("notes"); // marks the field as known; its value is read below when relevant
        boolean hasSchedule = hasDate || hasTime;
        AppointmentStatus status = null;
        LocalDate date = null;
        LocalTime time = null;
        String notes = null;

        if (hasStatus && hasSchedule) {
            errors.add("status", "cannot be combined with date/time; send them in separate requests");
        } else if (!hasStatus && !hasSchedule) {
            errors.add("body", "provide either 'status' or both 'date' and 'time'");
        } else if (hasStatus) {
            status = status(in.text("status", true, 20), errors);
            if (in.has("notes")) {
                errors.add("notes", "can only be changed together with date and time");
            }
        } else {
            date = in.date("date", true);
            time = in.time("time", true);
            notes = in.text("notes", false, 500);
            checkSchedule(errors, date, time);
        }
        in.rejectUnknownFields();
        errors.throwIfAny();
        return new AppointmentUpdateRequest(status, date, time, notes);
    }

    /**
     * Validates the query of the appointment list.
     *
     * @param query the decoded query parameters
     * @return the filter (date defaults to today)
     */
    public AppointmentFilter appointmentFilter(Map<String, String> query) {
        ValidationErrors errors = new ValidationErrors();
        rejectUnknownParameters(query, Set.of("date", "dentistId"), errors);
        LocalDate date = LocalDate.now(clock);
        String rawDate = query.get("date");
        if (rawDate != null) {
            try {
                date = LocalDate.parse(rawDate.trim());
            } catch (java.time.format.DateTimeParseException e) {
                errors.add("date", "must be a valid date in yyyy-MM-dd format");
            }
        }
        Long dentistId = null;
        String rawDentist = query.get("dentistId");
        if (rawDentist != null) {
            try {
                dentistId = Long.parseLong(rawDentist.trim());
                if (dentistId <= 0) {
                    errors.add("dentistId", "must be a positive whole number");
                }
            } catch (NumberFormatException e) {
                errors.add("dentistId", "must be a positive whole number");
            }
        }
        errors.throwIfAny();
        return new AppointmentFilter(date, dentistId);
    }

    /**
     * Validates the patient search query.
     *
     * @param query the decoded query parameters
     * @return the trimmed search term (1 to 50 characters)
     */
    public String searchTerm(Map<String, String> query) {
        ValidationErrors errors = new ValidationErrors();
        rejectUnknownParameters(query, Set.of("search"), errors);
        String term = query.get("search");
        if (term == null || term.isBlank()) {
            errors.add("search", "is required");
        } else if (term.trim().length() > 50) {
            errors.add("search", "must be at most 50 characters");
        }
        errors.throwIfAny();
        return term.trim();
    }

    /**
     * Validates a patient registration.
     *
     * @param body the JSON body
     * @return an unsaved patient
     */
    public Patient patient(Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        String firstName = in.text("firstName", true, 50);
        String lastName = in.text("lastName", true, 50);
        LocalDate dob = in.date("dateOfBirth", true);
        String genderText = in.text("gender", true, 10);
        String phone = in.text("phone", true, 20);
        String email = in.text("email", false, 120);
        String address = in.text("address", false, 255);
        String notes = in.text("medicalNotes", false, 5000);

        checkName(errors, "firstName", firstName);
        checkName(errors, "lastName", lastName);
        if (dob != null && (dob.isAfter(LocalDate.now(clock)) || dob.isBefore(EARLIEST_BIRTH_DATE))) {
            errors.add("dateOfBirth", "must be between 1900-01-01 and today");
        }
        Gender gender = null;
        if (genderText != null) {
            try {
                gender = Gender.valueOf(genderText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("gender", "must be MALE, FEMALE or OTHER");
            }
        }
        if (phone != null && !PHONE.matcher(phone).matches()) {
            errors.add("phone", "must be a Sri Lankan mobile number in the format 07XXXXXXXX");
        }
        if (email != null && !EMAIL.matcher(email).matches()) {
            errors.add("email", "must be a valid e-mail address");
        }
        if (address != null && address.length() < 5) {
            errors.add("address", "must be at least 5 characters when provided");
        }
        in.rejectUnknownFields();
        errors.throwIfAny();

        Patient patient = new Patient();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setDateOfBirth(dob);
        patient.setGender(gender);
        patient.setPhone(phone);
        patient.setEmail(email);
        patient.setAddress(address);
        patient.setMedicalNotes(notes);
        return patient;
    }

    /**
     * Validates the body of {@code POST /api/appointments/{id}/bill}. The body is optional; everything defaults to
     * a STANDARD bill.
     *
     * @param appointmentId the appointment from the path
     * @param body          the JSON body (may be empty)
     * @return the request
     */
    public GenerateBillRequest generateBill(long appointmentId, Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        String typeText = in.text("billingType", false, 10);
        BigDecimal coverage = in.percent("insuranceCoveragePercent");
        BillingType type = BillingType.STANDARD;
        if (typeText != null) {
            try {
                type = BillingType.valueOf(typeText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("billingType", "must be STANDARD, SENIOR or INSURANCE");
            }
        }
        if (coverage != null && type != BillingType.INSURANCE) {
            errors.add("insuranceCoveragePercent", "is only allowed with billingType INSURANCE");
        }
        in.rejectUnknownFields();
        errors.throwIfAny();
        return new GenerateBillRequest(appointmentId, type.name(), coverage);
    }

    /**
     * Validates the body of {@code POST /api/bills/{id}/pay}.
     *
     * @param body the JSON body
     * @return the payment method
     */
    public PaymentMethod payment(Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        String text = in.text("paymentMethod", true, 15);
        PaymentMethod method = null;
        if (text != null) {
            try {
                method = PaymentMethod.valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("paymentMethod", "must be CASH, CARD, ONLINE or INSURANCE");
            }
        }
        in.rejectUnknownFields();
        errors.throwIfAny();
        return method;
    }

    /**
     * Validates the output format of a receipt request.
     *
     * @param query the decoded query parameters
     * @return JSON (default), TEXT or HTML
     */
    public ResponseFormat receiptFormat(Map<String, String> query) {
        ValidationErrors errors = new ValidationErrors();
        rejectUnknownParameters(query, Set.of("format"), errors);
        ResponseFormat format = formatParam(query, Set.of(ResponseFormat.JSON, ResponseFormat.TEXT, ResponseFormat.HTML),
                "json, text or html", errors);
        errors.throwIfAny();
        return format;
    }

    /**
     * Validates the query of a report request.
     *
     * @param type  which report
     * @param query the decoded query parameters
     * @return the validated query with defaults applied (current month, five rows, JSON)
     */
    public ReportQuery reportQuery(ReportType type, Map<String, String> query) {
        ValidationErrors errors = new ValidationErrors();
        LocalDate today = LocalDate.now(clock);
        boolean monthly = type == ReportType.MONTHLY_REVENUE;
        boolean limited = type == ReportType.TOP_TREATMENTS;

        Set<String> allowed = new HashSet<>(Set.of("format"));
        allowed.addAll(monthly ? Set.of("year", "month") : Set.of("from", "to"));
        if (limited) {
            allowed.add("limit");
        }
        rejectUnknownParameters(query, allowed, errors);

        LocalDate from = null;
        LocalDate to = null;
        Integer year = null;
        Integer month = null;
        Integer limit = null;
        if (monthly) {
            year = intParam(query, "year", today.getYear(), 2000, 2100, errors);
            month = intParam(query, "month", today.getMonthValue(), 1, 12, errors);
        } else {
            LocalDate defaultFrom = type == ReportType.DAILY_APPOINTMENTS ? today : today.withDayOfMonth(1);
            from = dateParam(query, "from", defaultFrom, errors);
            to = dateParam(query, "to", today, errors);
            if (from != null && to != null) {
                if (to.isBefore(from)) {
                    errors.add("to", "must not be before 'from'");
                } else if (ChronoUnit.DAYS.between(from, to) > 366) {
                    errors.add("to", "the range must not exceed 366 days");
                }
            }
        }
        if (limited) {
            limit = intParam(query, "limit", 5, 1, 20, errors);
        }
        ResponseFormat format = formatParam(query, Set.of(ResponseFormat.JSON, ResponseFormat.CSV), "json or csv", errors);
        errors.throwIfAny();
        return new ReportQuery(from, to, year, month, limit, format);
    }

    /**
     * Validates the optional body of the manual reminder run.
     *
     * @param body the JSON body (may be empty)
     * @return the appointment day, or {@code null} for tomorrow; must be today or within the next 30 days
     */
    public LocalDate reminderDate(Map<String, Object> body) {
        ValidationErrors errors = new ValidationErrors();
        FieldReader in = new FieldReader(body, errors);
        LocalDate date = in.date("date", false);
        LocalDate today = LocalDate.now(clock);
        if (date != null && (date.isBefore(today) || date.isAfter(today.plusDays(30)))) {
            errors.add("date", "must be today or within the next 30 days");
        }
        in.rejectUnknownFields();
        errors.throwIfAny();
        return date;
    }

    private static ResponseFormat formatParam(Map<String, String> query, Set<ResponseFormat> allowed, String hint,
                                              ValidationErrors errors) {
        String raw = query.get("format");
        if (raw == null) {
            return ResponseFormat.JSON;
        }
        return ResponseFormat.parse(raw).filter(allowed::contains).orElseGet(() -> {
            errors.add("format", "must be " + hint);
            return ResponseFormat.JSON;
        });
    }

    private static LocalDate dateParam(Map<String, String> query, String key, LocalDate fallback,
                                       ValidationErrors errors) {
        String raw = query.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            errors.add(key, "must be a valid date in yyyy-MM-dd format");
            return null;
        }
    }

    private static Integer intParam(Map<String, String> query, String key, int fallback, int min, int max,
                                    ValidationErrors errors) {
        String raw = query.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        errors.add(key, "must be a whole number between " + min + " and " + max);
        return null;
    }

    private void checkSchedule(ValidationErrors errors, LocalDate date, LocalTime time) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (time != null) {
            if (time.isBefore(OPENING) || time.isAfter(CLOSING)) {
                errors.add("time", "must be between 08:00 and 18:00");
            }
            if (time.getMinute() % 15 != 0) {
                errors.add("time", "must be on a 15-minute boundary, e.g. 09:00 or 09:15");
            }
        }
        if (date != null) {
            if (date.isBefore(now.toLocalDate())) {
                errors.add("date", "must be in the future");
            } else if (date.equals(now.toLocalDate()) && time != null
                    && !LocalDateTime.of(date, time).isAfter(now)) {
                errors.add("time", "must be later than the current time when booking for today");
            }
        }
    }

    private static void checkName(ValidationErrors errors, String field, String value) {
        if (value != null && !NAME.matcher(value).matches()) {
            errors.add(field, "must contain letters only (single spaces, hyphens and apostrophes are allowed between letters)");
        }
    }

    private static AppointmentStatus status(String text, ValidationErrors errors) {
        if (text == null) {
            return null;
        }
        AppointmentStatus status;
        try {
            status = AppointmentStatus.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add("status", "must be one of SCHEDULED, CONFIRMED, COMPLETED, NO_SHOW");
            return null;
        }
        if (status == AppointmentStatus.CANCELLED) {
            errors.add("status", "use DELETE /api/appointments/{id} to cancel an appointment");
            return null;
        }
        if (status == AppointmentStatus.SCHEDULED) {
            errors.add("status", "an appointment cannot be moved back to SCHEDULED");
            return null;
        }
        return status;
    }

    private static void rejectUnknownParameters(Map<String, String> query, Set<String> allowed,
                                                ValidationErrors errors) {
        for (String key : query.keySet()) {
            if (!allowed.contains(key)) {
                errors.add(key, "is not a supported query parameter");
            }
        }
    }
}
