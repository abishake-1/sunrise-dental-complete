package com.sunrisedental.dao;

import com.sunrisedental.model.Patient;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Data access for {@code patients}. */
public interface PatientDao {

    /**
     * Inserts a patient.
     *
     * @param connection connection to use
     * @param patient    the new patient (id ignored)
     * @return the stored patient including id and timestamps
     */
    Patient insert(Connection connection, Patient patient);

    /**
     * Updates all editable columns of a patient.
     *
     * @param connection connection to use
     * @param patient    the patient (id required)
     * @return {@code true} if a row matched
     */
    boolean update(Connection connection, Patient patient);

    /**
     * Finds a patient by id.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return the patient, if present
     */
    Optional<Patient> findById(Connection connection, long id);

    /**
     * Searches by name fragment or phone fragment.
     *
     * @param connection connection to use
     * @param term       raw search term (wildcards are escaped here)
     * @param limit      maximum rows
     * @return matching patients ordered by name
     */
    List<Patient> search(Connection connection, String term, int limit);

    /**
     * Deletes a patient.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return {@code true} if a row was deleted
     */
    boolean delete(Connection connection, long id);
}
