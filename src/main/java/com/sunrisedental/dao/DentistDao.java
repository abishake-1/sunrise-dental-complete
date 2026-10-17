package com.sunrisedental.dao;

import com.sunrisedental.model.Dentist;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Data access for {@code dentists}. */
public interface DentistDao {

    /**
     * Finds a dentist by id.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return the dentist, if present
     */
    Optional<Dentist> findById(Connection connection, long id);

    /**
     * Finds a dentist and locks the row until the transaction ends. Used to serialise bookings of one dentist.
     *
     * @param connection connection to use (inside a transaction)
     * @param id         primary key
     * @return the dentist, if present
     */
    Optional<Dentist> findByIdForUpdate(Connection connection, long id);

    /**
     * Lists dentists that accept appointments.
     *
     * @param connection connection to use
     * @return active dentists ordered by name
     */
    List<Dentist> findAllActive(Connection connection);
}
