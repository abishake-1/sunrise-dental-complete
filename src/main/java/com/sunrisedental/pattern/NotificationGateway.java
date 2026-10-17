package com.sunrisedental.pattern;

/**
 * Adapter pattern, <em>target</em> interface: the only thing the application knows about message delivery.
 * Providers (SMS gateway, mail server, console, file) are plugged in behind it.
 */
public interface NotificationGateway {

    /**
     * Delivers a message.
     *
     * @param message what to send
     * @return a provider reference for the delivery
     * @throws NotificationException if the provider rejected or could not deliver the message
     */
    String send(NotificationMessage message) throws NotificationException;
}
