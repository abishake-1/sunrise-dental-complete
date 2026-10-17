package com.sunrisedental.service;

import com.sunrisedental.dto.ReminderResult;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a reminder job once a day at a fixed local time on a {@link ScheduledExecutorService}.
 * After every run it computes the next occurrence of the time of day and schedules it again, instead of using a
 * fixed 24-hour rate, so the job keeps its clock time across daylight-saving changes. Exceptions never stop the
 * schedule. The worker thread is a daemon, so it never keeps the JVM alive.
 */
public final class ReminderScheduler implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ReminderScheduler.class.getName());

    private final Supplier<ReminderResult> job;
    private final Clock clock;
    private final LocalTime runAt;
    private ScheduledExecutorService executor;
    private LocalDate lastRunDate;

    /**
     * Creates the scheduler (not started yet).
     *
     * @param job   the work to run, usually {@code () -> facade.sendReminders(null)}
     * @param clock time source and time zone
     * @param runAt local time of day of the daily run
     */
    public ReminderScheduler(Supplier<ReminderResult> job, Clock clock, LocalTime runAt) {
        this.job = job;
        this.clock = clock;
        this.runAt = runAt;
    }

    /** Starts the daily schedule. */
    public synchronized void start() {
        if (executor != null) {
            throw new IllegalStateException("Already started");
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reminder-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduleNext();
    }

    /**
     * Computes how long to wait for the next run.
     *
     * @param clock time source and time zone
     * @param runAt local time of day
     * @return the time until the next occurrence of {@code runAt} (a full day if it is exactly now)
     */
    static Duration delayUntilNext(Clock clock, LocalTime runAt) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime next = now.toLocalDate().atTime(runAt).atZone(now.getZone());
        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(runAt).atZone(now.getZone());
        }
        return Duration.between(now.toInstant(), next.toInstant());
    }

    private synchronized void scheduleNext() {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        Duration delay = delayUntilNext(clock, runAt);
        ZonedDateTime due = ZonedDateTime.now(clock).plus(delay);
        if (due.toLocalDate().equals(lastRunDate)) {
            // a timer that fired a few milliseconds early must not trigger a second run the same day
            delay = delay.plusDays(1);
        }
        Duration wait = delay;
        LOG.info(() -> "Next reminder run in " + Duration.ofSeconds(wait.toSeconds()));
        executor.schedule(this::runAndReschedule, wait.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void runAndReschedule() {
        try {
            ReminderResult result = job.get();
            LOG.info(() -> "Reminder run finished: " + result);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Reminder run failed", t);
        } finally {
            synchronized (this) {
                lastRunDate = LocalDate.now(clock);
            }
            scheduleNext();
        }
    }

    /** Stops the schedule and waits briefly for a running job. */
    @Override
    public synchronized void close() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
