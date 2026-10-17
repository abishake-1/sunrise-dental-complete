package com.sunrisedental.dto;

/**
 * Validated login details.
 *
 * @param username login name
 * @param password the password as typed
 */
public record LoginRequest(String username, String password) {

    /** The password is never printed. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
