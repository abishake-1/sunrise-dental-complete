package com.sunrisedental.server;

import com.sunrisedental.api.ApiServer;
import com.sunrisedental.api.ApiSettings;
import com.sunrisedental.service.ClinicFacade;
import com.sunrisedental.service.ReminderScheduler;
import com.sunrisedental.util.AppConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.Logger;

/**
 * Entry point of the Sunrise Dental REST server (tier 2).
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code SUNRISE_DB_URL}, {@code SUNRISE_DB_USER}, {@code SUNRISE_DB_PASSWORD} - database (see Step 3)</li>
 *   <li>{@code SUNRISE_SERVER_PORT} - optional, default 8080</li>
 *   <li>{@code SUNRISE_BIND_ADDRESS} - optional, default 127.0.0.1 (use 0.0.0.0 to listen on all interfaces)</li>
 *   <li>{@code SUNRISE_RECEIPTS_DIR} - optional, where receipt files are saved, default {@code ./receipts}</li>
 *   <li>{@code SUNRISE_FRONTEND_DIR} - optional folder of the browser client, default {@code ./frontend}
 *       (served at {@code /}, so open {@code http://localhost:8080})</li>
 *   <li>{@code SUNRISE_NOTIFY_MODE} ({@code console} or {@code file}) and {@code SUNRISE_NOTIFY_FILE} - where
 *       reminders are "sent"</li>
 * </ul>
 * The daily reminder job is configured in {@code config.properties} ({@code reminders.*}).
 */
public final class ServerApplication {

    private static final Logger LOG = Logger.getLogger(ServerApplication.class.getName());
    private static final int DEFAULT_PORT = 8080;

    private ServerApplication() {
    }

    /**
     * Starts the server and registers a shutdown hook.
     *
     * @param args ignored
     * @throws IOException if the port cannot be bound
     */
    public static void main(String[] args) throws IOException {
        // Slow-client protection built into the JDK server (seconds): drop stalled requests/responses.
        System.setProperty("sun.net.httpserver.maxReqTime", "30");
        System.setProperty("sun.net.httpserver.maxRspTime", "60");

        int port = resolvePort(System.getenv("SUNRISE_SERVER_PORT"));
        String bind = System.getenv("SUNRISE_BIND_ADDRESS");
        InetAddress address = InetAddress.getByName(bind == null || bind.isBlank() ? "127.0.0.1" : bind.trim());

        Clock clock = Clock.systemDefaultZone();
        AppConfig config = AppConfig.load();
        ClinicFacade facade = ClinicFacade.createDefault(config);
        Path webRoot = resolveWebRoot(System.getenv("SUNRISE_FRONTEND_DIR"));
        ApiServer server = new ApiServer(facade, ApiSettings.fromEnvironment(config), clock, webRoot);
        server.start(new InetSocketAddress(address, port));
        LOG.info(() -> "Web client folder: " + webRoot.toAbsolutePath().normalize());

        ReminderScheduler scheduler = null;
        if (config.remindersEnabled()) {
            scheduler = new ReminderScheduler(() -> facade.sendReminders(null), clock, config.reminderRunTime());
            scheduler.start();
        }
        ReminderScheduler runningScheduler = scheduler;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (runningScheduler != null) {
                runningScheduler.close();
            }
            server.stop();
        }, "server-shutdown"));
        LOG.info(() -> "Sunrise Dental REST API listening on " + address.getHostAddress() + ":" + port);
    }

    /**
     * Chooses the folder of the browser client.
     *
     * @param raw raw environment value, may be {@code null} or blank
     * @return the configured folder, or {@code frontend} in the working directory
     */
    static Path resolveWebRoot(String raw) {
        return Path.of(raw == null || raw.isBlank() ? "frontend" : raw.trim());
    }

    /**
     * Parses and validates the configured port.
     *
     * @param raw raw environment value, may be {@code null} or blank
     * @return a port in 1..65535
     * @throws IllegalArgumentException if the value is not a valid port
     */
    static int resolvePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("SUNRISE_SERVER_PORT out of range: " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SUNRISE_SERVER_PORT is not a number: " + raw, e);
        }
    }
}
