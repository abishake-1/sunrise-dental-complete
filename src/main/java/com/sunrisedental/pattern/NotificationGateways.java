package com.sunrisedental.pattern;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

/**
 * Factory for the configured {@link NotificationGateway}. Environment variables:
 * {@code SUNRISE_NOTIFY_MODE} = {@code console} (default) or {@code file}, and for file mode
 * {@code SUNRISE_NOTIFY_FILE} (default {@code notifications-outbox.log}).
 */
public final class NotificationGateways {

    private NotificationGateways() {
    }

    /**
     * Builds the gateway from the environment.
     *
     * @param clock time source for file timestamps
     * @return the gateway
     * @throws IllegalStateException for an unknown mode
     */
    public static NotificationGateway fromEnvironment(Clock clock) {
        String mode = System.getenv("SUNRISE_NOTIFY_MODE");
        String normalized = mode == null || mode.isBlank() ? "console" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "console" -> new MessengerGatewayAdapter(new ConsoleMessenger());
            case "file" -> {
                String file = System.getenv("SUNRISE_NOTIFY_FILE");
                yield new MessengerGatewayAdapter(new FileMessenger(
                        Path.of(file == null || file.isBlank() ? "notifications-outbox.log" : file.trim()), clock));
            }
            default -> throw new IllegalStateException("SUNRISE_NOTIFY_MODE must be 'console' or 'file'");
        };
    }
}
