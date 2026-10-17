package com.sunrisedental.pattern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;

/** Mock provider that "sends" messages by appending one line each to an outbox file. */
public final class FileMessenger implements LegacyMessenger {

    private static final int REJECTED = 2;
    private static final int IO_ERROR = 5;

    private final Path file;
    private final Clock clock;

    /**
     * Creates the messenger.
     *
     * @param file  the outbox file (created on first use, parent folders included)
     * @param clock time source for the timestamps
     */
    public FileMessenger(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    @Override
    public synchronized int push(String destination, String text) {
        if (destination == null || destination.isBlank()) {
            return REJECTED;
        }
        String line = Instant.now(clock) + " | " + destination + " | " + text.replaceAll("\\s*[\\r\\n]+\\s*", " / ")
                + System.lineSeparator();
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return OK;
        } catch (IOException e) {
            return IO_ERROR;
        }
    }
}
