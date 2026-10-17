package com.sunrisedental.api;

/** Raised when a request body is not valid JSON (maps to HTTP 400 INVALID_JSON). */
public class JsonParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong and where
     */
    public JsonParseException(String message) {
        super(message);
    }
}
