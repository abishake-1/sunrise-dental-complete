package com.sunrisedental.dao;

import com.sunrisedental.model.User;
import java.sql.Connection;
import java.util.Optional;

/** Data access for {@code users}. */
public interface UserDao {

    /**
     * Finds a user by login name (case-insensitive, per the column collation).
     *
     * @param connection connection to use
     * @param username   login name
     * @return the user, if present
     */
    Optional<User> findByUsername(Connection connection, String username);

    /**
     * Finds a user by id.
     *
     * @param connection connection to use
     * @param id         primary key
     * @return the user, if present
     */
    Optional<User> findById(Connection connection, long id);
}
