package com.sunrisedental.api;

import com.sunrisedental.model.User;

/**
 * A logged-in user.
 *
 * @param token the random token the client sends as {@code Authorization: Bearer <token>}
 * @param user  who is logged in (without the password)
 */
public record Session(String token, User user) {
}
