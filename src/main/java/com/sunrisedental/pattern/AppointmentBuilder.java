package com.sunrisedental.pattern;

import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.util.ValidationException;
import com.sunrisedental.util.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Builder for new {@link Appointment}s. {@link #build()} validates everything that can be checked without
 * the database; new appointments always start as SCHEDULED and receive their number from the database.
 */
public final class AppointmentBuilder {

    private static final int SLOT_MINUTES = 15;
    private static final int MAX_NOTES = 500;

    private final Clock clock;
    private Long patientId;
    private Long dentistId;
    private Long treatmentId;
    private Long createdBy;
    private LocalDate date;
    private LocalTime time;
    private String notes;

    /** Creates a builder that uses the system clock. */
    public AppointmentBuilder() {
        this(Clock.systemDefaultZone());
    }

    /**
     * Creates a builder with an explicit clock (tests).
     *
     * @param clock source of "today"
     */
    public AppointmentBuilder(Clock clock) {
        this.clock = clock;
    }

    /** @param value patient id @return this builder */
    public AppointmentBuilder patientId(long value) {
        this.patientId = value;
        return this;
    }

    /** @param value dentist id @return this builder */
    public AppointmentBuilder dentistId(long value) {
        this.dentistId = value;
        return this;
    }

    /** @param value treatment id @return this builder */
    public AppointmentBuilder treatmentId(long value) {
        this.treatmentId = value;
        return this;
    }

    /** @param value id of the staff member creating the booking @return this builder */
    public AppointmentBuilder createdBy(long value) {
        this.createdBy = value;
        return this;
    }

    /** @param value appointment day @return this builder */
    public AppointmentBuilder date(LocalDate value) {
        this.date = value;
        return this;
    }

    /** @param value start time @return this builder */
    public AppointmentBuilder time(LocalTime value) {
        this.time = value;
        return this;
    }

    /** @param value optional notes @return this builder */
    public AppointmentBuilder notes(String value) {
        this.notes = value;
        return this;
    }

    /**
     * Checks the rules every appointment date and time must satisfy (also used when rescheduling).
     *
     * @param date  the day, must not be before today
     * @param time  the start time, must be on a 15-minute boundary
     * @param clock source of "today"
     * @throws ValidationException if a rule is violated
     */
    public static void validateSchedule(LocalDate date, LocalTime time, Clock clock) {
        Validator.requireNonNull(date, "appointmentDate");
        Validator.requireNonNull(time, "appointmentTime");
        if (date.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("Appointment date cannot be in the past");
        }
        if (time.getSecond() != 0 || time.getNano() != 0 || time.getMinute() % SLOT_MINUTES != 0) {
            throw new ValidationException("Appointment time must be on a 15-minute boundary, e.g. 09:00 or 09:15");
        }
    }

    /**
     * Validates the collected values and creates the appointment.
     *
     * @return a new SCHEDULED appointment without id and number
     * @throws ValidationException if a value is missing or invalid
     */
    public Appointment build() {
        long validPatient = Validator.requireId(patientId, "patientId");
        long validDentist = Validator.requireId(dentistId, "dentistId");
        long validTreatment = Validator.requireId(treatmentId, "treatmentId");
        long validCreator = Validator.requireId(createdBy, "createdBy");
        validateSchedule(date, time, clock);
        LocalDate validDate = date;
        LocalTime validTime = time;
        Appointment appointment = new Appointment();
        appointment.setPatientId(validPatient);
        appointment.setDentistId(validDentist);
        appointment.setTreatmentId(validTreatment);
        appointment.setCreatedBy(validCreator);
        appointment.setAppointmentDate(validDate);
        appointment.setAppointmentTime(validTime);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setNotes(Validator.optionalText(notes, "notes", MAX_NOTES));
        return appointment;
    }
}
