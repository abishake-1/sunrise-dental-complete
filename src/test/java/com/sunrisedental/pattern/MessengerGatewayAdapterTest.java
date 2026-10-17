package com.sunrisedental.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessengerGatewayAdapterTest {

    private static NotificationMessage message(String channel, String address) {
        return new NotificationMessage(42, channel, "Nimal", address, "Reminder", "See you tomorrow\nat 09:00");
    }

    @Test
    void smsNumbersAreConvertedToInternationalFormat() throws Exception {
        List<String> pushed = new ArrayList<>();
        MessengerGatewayAdapter adapter = new MessengerGatewayAdapter((to, text) -> {
            pushed.add(to + "|" + text);
            return LegacyMessenger.OK;
        });
        assertEquals("MOCK-42", adapter.send(message("SMS", "0771234567")));
        adapter.send(message("SMS", "+94771234567"));
        assertEquals("+94771234567|See you tomorrow\nat 09:00", pushed.get(0));
        assertEquals("+94771234567|See you tomorrow\nat 09:00", pushed.get(1));
    }

    @Test
    void emailPutsTheSubjectInFrontOfTheText() throws Exception {
        List<String> pushed = new ArrayList<>();
        new MessengerGatewayAdapter((to, text) -> {
            pushed.add(to + "|" + text);
            return 0;
        }).send(message("EMAIL", "n@example.com"));
        assertTrue(pushed.get(0).startsWith("n@example.com|Subject: Reminder | See you"));
    }

    @Test
    void providerErrorCodesBecomeExceptions() {
        MessengerGatewayAdapter adapter = new MessengerGatewayAdapter((to, text) -> 3);
        NotificationException e = assertThrows(NotificationException.class, () -> adapter.send(message("SMS", "0771234567")));
        assertEquals("Provider rejected the message (code 3)", e.getMessage());
    }

    @Test
    void consoleMessengerPrintsAndRejectsEmptyDestinations() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleMessenger console = new ConsoleMessenger(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(LegacyMessenger.OK, console.push("+94771234567", "hello"));
        assertEquals(2, console.push(" ", "hello"));
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("[NOTIFY] to +94771234567: hello"));
    }

    @Test
    void fileMessengerAppendsOneLinePerMessage() throws Exception {
        Path dir = Files.createTempDirectory("outbox-test");
        Path file = dir.resolve("sub").resolve("outbox.log");
        try {
            FileMessenger messenger = new FileMessenger(file, Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC));
            assertEquals(LegacyMessenger.OK, messenger.push("+94771234567", "line one\nline two"));
            assertEquals(LegacyMessenger.OK, messenger.push("a@example.com", "second"));
            List<String> lines = Files.readAllLines(file);
            assertEquals(2, lines.size());
            assertEquals("2026-10-12T10:00:00Z | +94771234567 | line one / line two", lines.get(0));
        } finally {
            Files.delete(file);
            Files.delete(file.getParent());
            Files.delete(dir);
        }
    }

    @Test
    void incompleteMessagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationMessage(1, "SMS", "x", " ", "s", "b"));
    }
}
