package com.sunrisedental.pattern;

/** A notification could not be delivered. */
public class NotificationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message why delivery failed (stored in {@code notifications.failure_reason})
     */
    public NotificationException(String message) {
        super(message);
    }
}
