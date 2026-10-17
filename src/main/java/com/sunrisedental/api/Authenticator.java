package com.sunrisedental.api;

import com.sunrisedental.model.User;
import java.util.Optional;

/** Checks credentials; implemented by {@code ClinicFacade::login}. */
@FunctionalInterface
public interface Authenticator {

    /**
     * Verifies a username and password.
     *
     * @param username login name
     * @param password the password as typed
     * @return the active user when the credentials are valid, otherwise empty
     */
    Optional<User> authenticate(String username, String password);
}
