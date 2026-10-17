package com.sunrisedental.pattern;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.dao.TransactionManager;

/** Observer that queues a booking confirmation in the {@code notifications} outbox. */
public final class NotificationListener implements EventListener<AppointmentCreatedEvent> {

    private static final int MAX_MESSAGE = 500;

    private final TransactionManager transactions;
    private final NotificationDao notificationDao;

    /**
     * Creates the listener.
     *
     * @param transactions  runs the insert in its own transaction
     * @param notificationDao outbox access
     */
    public NotificationListener(TransactionManager transactions, NotificationDao notificationDao) {
        this.transactions = transactions;
        this.notificationDao = notificationDao;
    }

    @Override
    public void onEvent(AppointmentCreatedEvent event) {
        String channel = event.patientEmail() != null ? "EMAIL" : "SMS";
        String message = String.format("Dear %s, your appointment %s is booked for %s at %s with %s (%s).",
                event.patientName(), event.appointmentNumber(), event.date(), event.time(),
                event.dentistName(), event.treatmentName());
        String bounded = message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
        transactions.runInTransaction(connection -> notificationDao.insertPending(connection, event.patientId(),
                event.appointmentId(), channel, "BOOKING_CONFIRMATION", bounded));
    }
}
