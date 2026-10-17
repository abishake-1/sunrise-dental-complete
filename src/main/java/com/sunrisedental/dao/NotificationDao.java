package com.sunrisedental.dao;

import com.sunrisedental.dto.ReminderCandidate;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

/** Data access for {@code notifications}: the log of every message the system sends. */
public interface NotificationDao {

    /**
     * Logs a PENDING notification before delivery is attempted.
     *
     * @param connection    connection to use
     * @param patientId     recipient
     * @param appointmentId related appointment, may be null
     * @param channel       EMAIL, SMS or IN_APP
     * @param type          BOOKING_CONFIRMATION, REMINDER, CANCELLATION or BILL
     * @param message       text (max 500 characters)
     * @return the generated id
     */
    long insertPending(Connection connection, long patientId, Long appointmentId, String channel, String type,
                       String message);

    /**
     * Marks a PENDING notification as delivered.
     *
     * @param connection connection to use
     * @param id         notification id
     * @return {@code true} if it was pending
     */
    boolean markSent(Connection connection, long id);

    /**
     * Marks a PENDING notification as failed.
     *
     * @param connection connection to use
     * @param id         notification id
     * @param reason     why delivery failed (shortened to 255 characters)
     * @return {@code true} if it was pending
     */
    boolean markFailed(Connection connection, long id, String reason);

    /**
     * Finds the SCHEDULED or CONFIRMED appointments of a day that have no SENT reminder yet. A reminder that
     * FAILED earlier is therefore retried by the next run.
     *
     * @param connection connection to use
     * @param date       the appointment day
     * @return candidates ordered by time
     */
    List<ReminderCandidate> findReminderCandidates(Connection connection, LocalDate date);
}
