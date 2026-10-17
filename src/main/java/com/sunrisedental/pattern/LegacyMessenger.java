package com.sunrisedental.pattern;

/**
 * Adapter pattern, <em>adaptee</em>: stands in for a third-party messaging SDK whose API does not match ours
 * (plain strings in, an integer status code out, no exceptions). {@link MessengerGatewayAdapter} adapts it to
 * {@link NotificationGateway}.
 */
public interface LegacyMessenger {

    /** Status code meaning "accepted". */
    int OK = 0;

    /**
     * Pushes a text to a destination.
     *
     * @param destination phone number (international format) or e-mail address
     * @param text        the text
     * @return {@link #OK} or a provider-specific error code
     */
    int push(String destination, String text);
}
