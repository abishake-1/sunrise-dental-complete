package com.sunrisedental.api;

import com.sunrisedental.model.User;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the logged-in users in memory. A session lasts until the user logs out or the server is restarted.
 */
public final class SessionManager {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Starts a session for a user.
     *
     * @param user the user who just logged in
     * @return the new session with a random token
     */
    public Session create(User user) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, publicCopy(user));
        sessions.put(token, session);
        return session;
    }

    /**
     * Looks a session up.
     *
     * @param token the token from the request (may be null)
     * @return the session, or empty if the token is unknown
     */
    public Optional<Session> find(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sessions.get(token));
    }

    /**
     * Ends a session (logout).
     *
     * @param token the token to forget (may be null)
     */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** @return how many users are logged in */
    public int size() {
        return sessions.size();
    }

    /** A copy that does not carry the password around. */
    private static User publicCopy(User source) {
        User copy = new User();
        copy.setId(source.getId());
        copy.setUsername(source.getUsername());
        copy.setFullName(source.getFullName());
        copy.setEmail(source.getEmail());
        copy.setRole(source.getRole());
        copy.setActive(source.isActive());
        return copy;
    }
}
