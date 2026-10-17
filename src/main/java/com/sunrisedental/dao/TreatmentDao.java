package com.sunrisedental.dao;

import com.sunrisedental.model.Treatment;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Data access for {@code treatments}. */
public interface TreatmentDao {

    /**
     * Finds a treatment by id.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return the treatment, if present
     */
    Optional<Treatment> findById(Connection connection, long id);

    /**
     * Lists bookable treatments.
     *
     * @param connection connection to use
     * @return active treatments ordered by name
     */
    List<Treatment> findAllActive(Connection connection);

    /**
     * Changes the price of a treatment.
     *
     * @param connection connection to use
     * @param id         primary key
     * @param price      new price (not negative)
     * @return {@code true} if a row matched
     */
    boolean updatePrice(Connection connection, long id, BigDecimal price);
}
