package com.sunrisedental.service;

import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dao.UserDao;
import com.sunrisedental.model.User;
import java.util.Optional;

/** Checks a username and password against the {@code users} table (passwords are stored as plain text). */
public final class AuthService {

    private static final int MAX_LENGTH = 128;

    private final TransactionManager transactions;
    private final UserDao userDao;

    /**
     * Creates the service.
     *
     * @param transactions connection handling
     * @param userDao      user access
     */
    public AuthService(TransactionManager transactions, UserDao userDao) {
        this.transactions = transactions;
        this.userDao = userDao;
    }

    /**
     * Checks a username and password.
     *
     * @param username login name
     * @param password the password as typed
     * @return the user when the password matches and the account is active, otherwise empty
     */
    public Optional<User> authenticate(String username, String password) {
        if (username == null || username.isBlank() || username.length() > MAX_LENGTH
                || password == null || password.isEmpty() || password.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        return transactions.read(c -> userDao.findByUsername(c, username.trim()))
                .filter(User::isActive)
                .filter(user -> password.equals(user.getPassword()));
    }
}
