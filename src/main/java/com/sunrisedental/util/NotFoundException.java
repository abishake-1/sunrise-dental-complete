package com.sunrisedental.util;

/** Signals that a referenced record does not exist (maps to HTTP 404). */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param entity entity name, e.g. "Patient"
     * @param id     the identifier that was not found
     */
    public NotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
