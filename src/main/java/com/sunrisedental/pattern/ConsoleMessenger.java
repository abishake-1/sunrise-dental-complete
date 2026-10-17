package com.sunrisedental.pattern;

import java.io.PrintStream;

/** Mock provider that "sends" messages by printing them to a stream (the console by default). */
public final class ConsoleMessenger implements LegacyMessenger {

    private static final int REJECTED = 2;

    private final PrintStream out;

    /** Creates a messenger that prints to {@code System.out}. */
    public ConsoleMessenger() {
        this(System.out);
    }

    /**
     * Creates a messenger that prints to the given stream.
     *
     * @param out target stream
     */
    public ConsoleMessenger(PrintStream out) {
        this.out = out;
    }

    @Override
    public int push(String destination, String text) {
        if (destination == null || destination.isBlank()) {
            return REJECTED;
        }
        out.println("[NOTIFY] to " + destination + ": " + text);
        return OK;
    }
}
