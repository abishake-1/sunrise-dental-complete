package com.sunrisedental.util;

/** Signals a state conflict such as a double booking or a duplicate record (maps to HTTP 409). */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message human-readable explanation, safe to show to the user
     */
    public ConflictException(String message) {
        super(message);
    }
}
