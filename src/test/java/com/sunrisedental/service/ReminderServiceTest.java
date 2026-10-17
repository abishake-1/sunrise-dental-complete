package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dto.ReminderCandidate;
import com.sunrisedental.dto.ReminderResult;
import com.sunrisedental.pattern.NotificationException;
import com.sunrisedental.pattern.NotificationMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ReminderServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    private final List<String> log = new ArrayList<>();
    private final List<NotificationMessage> delivered = new ArrayList<>();
    private LocalDate askedFor;

    private static ReminderCandidate candidate(long id, String name, String email) {
        return new ReminderCandidate(id, "APT-000" + id, LocalDate.of(2026, 10, 13), LocalTime.of(9, 0), 100 + id,
                name, email, "077123456" + id, "Dr. Maria Santos", "Dental Filling");
    }

    private ReminderService service(List<ReminderCandidate> candidates) {
        AtomicLong ids = new AtomicLong();
        NotificationDao dao = Stubs.of(NotificationDao.class)
                .on("findReminderCandidates", args -> {
                    askedFor = (LocalDate) args[1];
                    return candidates;
                })
                .on("insertPending", args -> {
                    long id = ids.incrementAndGet();
                    log.add("pending:" + id + ":" + args[3] + ":" + args[4]);
                    return id;
                })
                .on("markSent", args -> {
                    log.add("sent:" + args[1]);
                    return true;
                })
                .on("markFailed", args -> {
                    log.add("failed:" + args[1] + ":" + args[2]);
                    return true;
                })
                .make();
        return new ReminderService(new TransactionManager(Stubs.fakeConnections()), dao, message -> {
            if ("Bad".equals(message.recipientName())) {
                throw new NotificationException("Provider rejected the message (code 3)");
            }
            if ("Boom".equals(message.recipientName())) {
                throw new IllegalStateException("kaput");
            }
            delivered.add(message);
            return "ok";
        }, CLOCK);
    }

    @Test
    void everyAttemptIsLoggedAndFailuresDoNotStopTheBatch() {
        ReminderService service = service(List.of(candidate(1, "Good Email", "a@example.com"),
                candidate(2, "Bad", null), candidate(3, "Boom", "c@example.com"), candidate(4, "Good Sms", null)));
        ReminderResult result = service.sendRemindersFor(LocalDate.of(2026, 10, 13));

        assertEquals(new ReminderResult(LocalDate.of(2026, 10, 13), 4, 2, 2), result);
        assertEquals(List.of("pending:1:EMAIL:REMINDER", "sent:1"), log.subList(0, 2));
        assertTrue(log.contains("failed:2:Provider rejected the message (code 3)"));
        assertTrue(log.contains("failed:3:kaput"));
        assertTrue(log.contains("sent:4"));
        assertEquals(4L, log.stream().filter(l -> l.startsWith("pending:")).count());
    }

    @Test
    void emailIsPreferredAndSmsIsTheFallback() {
        service(List.of(candidate(1, "Good Email", "a@example.com"), candidate(4, "Good Sms", null)))
                .sendRemindersFor(LocalDate.of(2026, 10, 13));
        assertEquals("EMAIL", delivered.get(0).channel());
        assertEquals("a@example.com", delivered.get(0).recipientAddress());
        assertEquals("SMS", delivered.get(1).channel());
        assertEquals("0771234564", delivered.get(1).recipientAddress());
        assertTrue(delivered.get(0).body().contains("APT-0001"));
        assertTrue(delivered.get(0).body().contains("2026-10-13"));
        assertTrue(delivered.get(0).subject().contains("APT-0001"));
    }

    @Test
    void withoutADateItRemindsForTomorrow() {
        ReminderResult result = service(List.of()).sendRemindersFor(null);
        assertEquals(LocalDate.of(2026, 10, 13), askedFor);
        assertEquals(new ReminderResult(LocalDate.of(2026, 10, 13), 0, 0, 0), result);
    }
}
