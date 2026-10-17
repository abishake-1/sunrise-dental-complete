package com.sunrisedental.service;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dto.ReminderCandidate;
import com.sunrisedental.dto.ReminderResult;
import com.sunrisedental.pattern.NotificationException;
import com.sunrisedental.pattern.NotificationGateway;
import com.sunrisedental.pattern.NotificationMessage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends appointment reminders and logs every attempt in the {@code notifications} table:
 * a PENDING row is written first, then the gateway is called, then the row becomes SENT or FAILED (with the
 * reason). Appointments that already have a SENT reminder are skipped, so running the job twice never
 * reminds a patient twice, while FAILED reminders are retried by the next run. Delivery is at-least-once:
 * if the process dies after the gateway accepted a message but before the row is marked SENT, that patient
 * may be reminded again.
 */
public final class ReminderService {

    private static final Logger LOG = Logger.getLogger(ReminderService.class.getName());
    private static final int MAX_MESSAGE = 500;

    private final TransactionManager transactions;
    private final NotificationDao notificationDao;
    private final NotificationGateway gateway;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param transactions    connection handling
     * @param notificationDao notification log access
     * @param gateway         where messages are delivered
     * @param clock           source of "today"
     */
    public ReminderService(TransactionManager transactions, NotificationDao notificationDao,
                           NotificationGateway gateway, Clock clock) {
        this.transactions = transactions;
        this.notificationDao = notificationDao;
        this.gateway = gateway;
        this.clock = clock;
    }

    /**
     * Sends the reminders of one day.
     *
     * @param date the appointment day, {@code null} means tomorrow
     * @return how many reminders were needed, delivered and failed
     */
    public ReminderResult sendRemindersFor(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock).plusDays(1);
        List<ReminderCandidate> candidates = transactions.read(c -> notificationDao.findReminderCandidates(c, day));
        int sent = 0;
        int failed = 0;
        for (ReminderCandidate candidate : candidates) {
            if (deliver(candidate)) {
                sent++;
            } else {
                failed++;
            }
        }
        LOG.info(() -> "Reminders for " + day + ": " + candidates.size() + " due");
        return new ReminderResult(day, candidates.size(), sent, failed);
    }

    private boolean deliver(ReminderCandidate c) {
        boolean byEmail = c.patientEmail() != null;
        String channel = byEmail ? "EMAIL" : "SMS";
        String address = byEmail ? c.patientEmail() : c.patientPhone();
        String subject = "Appointment reminder: " + c.appointmentNumber() + " on " + c.date();
        String body = String.format("Dear %s, this is a reminder of your appointment %s on %s at %s with %s (%s). "
                        + "Please call us if you need to reschedule.", c.patientName(), c.appointmentNumber(),
                c.date(), c.time(), c.dentistName(), c.treatmentName());
        String bounded = body.length() > MAX_MESSAGE ? body.substring(0, MAX_MESSAGE) : body;

        long id;
        try {
            id = transactions.inTransaction(conn -> notificationDao.insertPending(conn, c.patientId(),
                    c.appointmentId(), channel, "REMINDER", bounded));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not log reminder for " + c.appointmentNumber(), e);
            return false;
        }
        try {
            gateway.send(new NotificationMessage(id, channel, c.patientName(), address, subject, bounded));
        } catch (NotificationException | RuntimeException e) {
            markFailed(id, e.getMessage());
            return false;
        }
        try {
            transactions.runInTransaction(conn -> notificationDao.markSent(conn, id));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Reminder " + id + " was delivered but could not be marked SENT", e);
        }
        return true;
    }

    private void markFailed(long id, String reason) {
        try {
            transactions.runInTransaction(conn -> notificationDao.markFailed(conn, id, reason));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not mark reminder " + id + " as FAILED", e);
        }
    }
}
