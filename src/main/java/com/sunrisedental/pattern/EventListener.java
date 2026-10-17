package com.sunrisedental.pattern;

/**
 * Observer of one kind of event.
 *
 * @param <E> the event type handled
 */
@FunctionalInterface
public interface EventListener<E extends DomainEvent> {

    /**
     * Handles an event.
     *
     * @param event the published event
     */
    void onEvent(E event);
}
