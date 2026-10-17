package com.sunrisedental.dao;

import com.sunrisedental.model.Bill;
import com.sunrisedental.model.PaymentMethod;
import java.sql.Connection;
import java.util.Optional;

/** Data access for {@code bills}. */
public interface BillDao {

    /**
     * Inserts a bill. {@code UNIQUE(appointment_id)} guarantees at most one bill per appointment.
     *
     * @param connection connection to use
     * @param bill       the new bill (id ignored; payment status starts UNPAID)
     * @return the stored bill including id and timestamps
     * @throws DataAccessException with {@code isDuplicateKey()} when the appointment already has a bill
     */
    Bill insert(Connection connection, Bill bill);

    /**
     * Finds a bill by id.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return the bill, if present
     */
    Optional<Bill> findById(Connection connection, long id);

    /**
     * Finds the bill of an appointment.
     *
     * @param connection    connection to use
     * @param appointmentId appointment primary key
     * @return the bill, if present
     */
    Optional<Bill> findByAppointmentId(Connection connection, long appointmentId);

    /**
     * Marks an UNPAID bill as paid now.
     *
     * @param connection connection to use
     * @param id         primary key
     * @param method     how it was paid
     * @return {@code true} if the bill was unpaid and is now paid
     */
    boolean markPaid(Connection connection, long id, PaymentMethod method);

    /**
     * Voids an UNPAID bill.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return {@code true} if the bill was unpaid and is now void
     */
    boolean voidBill(Connection connection, long id);
}
