package com.sunrisedental.pattern;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Observer pattern: the subject. Listeners subscribe per event type; {@link #publish} delivers
 * synchronously on the caller's thread. A failing listener is logged and never prevents the remaining
 * listeners from running or affects the publisher's caller.
 */
public final class EventPublisher {

    private static final Logger LOG = Logger.getLogger(EventPublisher.class.getName());

    private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener.
     *
     * @param type     the event type (subtypes are delivered too)
     * @param listener the listener
     * @param <E>      the event type
     */
    public <E extends DomainEvent> void subscribe(Class<E> type, EventListener<E> listener) {
        subscriptions.add(new Subscription<>(Objects.requireNonNull(type), Objects.requireNonNull(listener)));
    }

    /**
     * Delivers an event to every matching listener.
     *
     * @param event the event
     */
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        for (Subscription<?> subscription : subscriptions) {
            try {
                subscription.deliver(event);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Listener failed for " + event.getClass().getSimpleName(), e);
            }
        }
    }

    private record Subscription<E extends DomainEvent>(Class<E> type, EventListener<E> listener) {

        void deliver(DomainEvent event) {
            if (type.isInstance(event)) {
                listener.onEvent(type.cast(event));
            }
        }
    }
}
