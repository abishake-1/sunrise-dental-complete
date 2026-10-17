package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReminderSchedulerTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void waitsUntilTheNextOccurrenceOfTheTimeOfDay() {
        LocalTime six = LocalTime.of(18, 0);
        assertEquals(Duration.ofHours(6).plusMinutes(30), ReminderScheduler.delayUntilNext(at("2026-10-12T11:30:00Z"), six));
        assertEquals(Duration.ofHours(24), ReminderScheduler.delayUntilNext(at("2026-10-12T18:00:00Z"), six));
        assertEquals(Duration.ofHours(24).minusSeconds(1), ReminderScheduler.delayUntilNext(at("2026-10-12T18:00:01Z"), six));
        assertEquals(Duration.ofMinutes(1), ReminderScheduler.delayUntilNext(at("2026-10-12T23:59:00Z"), LocalTime.MIDNIGHT));
    }

    @Test
    void runsTheJobAtTheScheduledTimeSurvivesFailuresAndStopsOnClose() throws Exception {
        CountDownLatch firstRun = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        LocalTime soon = LocalTime.now().plusSeconds(2).withNano(0);
        ReminderScheduler scheduler = new ReminderScheduler(() -> {
            runs.incrementAndGet();
            firstRun.countDown();
            throw new IllegalStateException("job failure must not kill the schedule");
        }, Clock.systemDefaultZone(), soon);
        try {
            scheduler.start();
            assertThrows(IllegalStateException.class, scheduler::start);
            assertTrue(firstRun.await(10, TimeUnit.SECONDS), "job did not run");
        } finally {
            scheduler.close();
        }
        Thread.sleep(300);
        assertEquals(1, runs.get(), "the next run is a day away");
    }
}
