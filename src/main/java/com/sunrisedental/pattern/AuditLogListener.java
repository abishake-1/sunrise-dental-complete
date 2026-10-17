package com.sunrisedental.pattern;

import com.sunrisedental.dao.AuditDao;
import com.sunrisedental.dao.TransactionManager;

/**
 * Observer that records the business event in {@code audit_log}. It complements the database trigger,
 * which stores the row snapshot: this entry states who booked what, as an application-level event.
 */
public final class AuditLogListener implements EventListener<AppointmentCreatedEvent> {

    private static final String SOURCE = "appointment_events";

    private final TransactionManager transactions;
    private final AuditDao auditDao;

    /**
     * Creates the listener.
     *
     * @param transactions runs the insert in its own transaction
     * @param auditDao     audit log access
     */
    public AuditLogListener(TransactionManager transactions, AuditDao auditDao) {
        this.transactions = transactions;
        this.auditDao = auditDao;
    }

    @Override
    public void onEvent(AppointmentCreatedEvent event) {
        String detail = String.format("%s booked for patient %d with %s on %s %s",
                event.appointmentNumber(), event.patientId(), event.dentistName(), event.date(), event.time());
        transactions.runInTransaction(connection -> auditDao.insertEvent(connection, SOURCE,
                event.appointmentId(), "INSERT", "AppointmentCreated", detail, event.performedBy()));
    }
}
