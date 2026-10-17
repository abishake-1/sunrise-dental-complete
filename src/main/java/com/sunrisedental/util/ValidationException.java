package com.sunrisedental.util;

/** Signals that user-supplied input or a business rule check failed (maps to HTTP 400). */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message human-readable explanation, safe to show to the user
     */
    public ValidationException(String message) {
        super(message);
    }
}
