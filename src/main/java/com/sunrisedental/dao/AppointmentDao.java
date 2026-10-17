package com.sunrisedental.dao;

import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.BookedSlot;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Data access for {@code appointments}. */
public interface AppointmentDao {

    /**
     * Inserts an appointment. The database trigger assigns the number (APT-0001, ...).
     *
     * @param connection connection to use
     * @param appointment the new appointment (id and number ignored)
     * @return the stored appointment including number, id and timestamps
     * @throws DataAccessException with {@code isDuplicateKey()} when the dentist slot is already taken
     */
    Appointment insert(Connection connection, Appointment appointment);

    /**
     * Finds an appointment by id.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return the appointment, if present
     */
    Optional<Appointment> findById(Connection connection, long id);

    /**
     * Finds an appointment by id and locks the row until the transaction ends.
     *
     * @param connection connection to use (inside a transaction)
     * @param id         primary key
     * @return the appointment, if present
     */
    Optional<Appointment> findByIdForUpdate(Connection connection, long id);

    /**
     * Lists the appointments of a patient, newest first.
     *
     * @param connection connection to use
     * @param patientId  patient primary key
     * @return the appointments
     */
    List<Appointment> findByPatient(Connection connection, long patientId);

    /**
     * Lists the appointments of a day ordered by time.
     *
     * @param connection connection to use
     * @param date       the day
     * @return the appointments
     */
    List<Appointment> findByDate(Connection connection, LocalDate date);

    /**
     * Finds an appointment by its public number (APT-0001).
     *
     * @param connection connection to use
     * @param number     appointment number
     * @return the appointment, if present
     */
    Optional<Appointment> findByNumber(Connection connection, String number);

    /**
     * Lists the time intervals a dentist is busy on a day (cancelled and no-show appointments excluded).
     *
     * @param connection           connection to use
     * @param dentistId            dentist primary key
     * @param date                 the day
     * @param excludeAppointmentId an appointment to ignore (the one being rescheduled), 0 for none
     * @return busy intervals ordered by start time
     */
    List<BookedSlot> findActiveSlots(Connection connection, long dentistId, LocalDate date,
                                     long excludeAppointmentId);

    /**
     * Moves an appointment to another day and time and replaces its notes.
     *
     * @param connection connection to use
     * @param id         primary key
     * @param date       new day
     * @param time       new start time
     * @param notes      new notes, may be null
     * @return {@code true} if a row matched
     * @throws DataAccessException with {@code isDuplicateKey()} when the dentist slot is already taken
     */
    boolean reschedule(Connection connection, long id, LocalDate date, LocalTime time, String notes);

    /**
     * Changes the status of an appointment.
     *
     * @param connection connection to use
     * @param id         primary key
     * @param status     new status
     * @return {@code true} if a row matched
     */
    boolean updateStatus(Connection connection, long id, AppointmentStatus status);
}
