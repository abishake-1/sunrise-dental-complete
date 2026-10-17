package com.sunrisedental.api;

import com.sunrisedental.dto.ClinicProfile;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Starts the real ApiServer on a free port with a fake facade, and offers a tiny HTTP client. */
final class TestApi implements AutoCloseable {

    record Reply(int status, String body, HttpHeaders headers) {

        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return (Map<String, Object>) Json.parse(body);
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> error() {
            return (Map<String, Object>) json().get("error");
        }
    }

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-11T08:00:00Z"));
    final FakeFacade facade = new FakeFacade(clock);
    final Path receiptsDir;
    private final ApiServer server;
    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    private TestApi(Path receiptsDir) throws IOException {
        this.receiptsDir = receiptsDir;
        ApiSettings settings = new ApiSettings(receiptsDir, EnumSet.allOf(ReceiptStore.Format.class),
                new ClinicProfile("Sunrise Dental Clinic", "12 Main Street", "0112345678", "LKR"));
        server = new ApiServer(facade, settings, clock);
        server.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        base = "http://localhost:" + server.port();
    }

    static TestApi start() throws IOException {
        return new TestApi(Files.createTempDirectory("sunrise-receipts").resolve("receipts"));
    }

    Reply send(String method, String path, String body, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        Map<String, String> custom = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i += 2) {
            custom.put(headers[i], headers[i + 1]);
        }
        if (body != null && !custom.containsKey("Content-Type")) {
            builder.header("Content-Type", "application/json");
        }
        custom.forEach(builder::header);
        var response = http.send(builder.build(), BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body(), response.headers());
    }

    String login(String user, String password) throws Exception {
        Reply reply = send("POST", "/api/auth/login",
                "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}");
        if (reply.status() != 200) {
            throw new IllegalStateException("login failed: " + reply.status() + " " + reply.body());
        }
        return (String) reply.json().get("token");
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    @Override
    public void close() {
        server.stop();
        try {
            if (Files.exists(receiptsDir)) {
                try (var files = Files.list(receiptsDir)) {
                    for (Path file : files.toList()) {
                        Files.delete(file);
                    }
                }
                Files.delete(receiptsDir);
            }
            Files.deleteIfExists(receiptsDir.getParent());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
