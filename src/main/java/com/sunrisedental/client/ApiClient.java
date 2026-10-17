package com.sunrisedental.client;

import com.sunrisedental.api.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for the Sunrise Dental REST API (tier 1 talking to tier 2) built on
 * {@link java.net.http.HttpClient}. It keeps the Bearer token after login and turns error responses into
 * {@link ApiClientException}. It never touches the database.
 */
public final class ApiClient {

    /** A raw response. */
    public record Reply(int status, byte[] body) {
        /** @return the body decoded as UTF-8 text */
        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private String token;
    private String role;
    private String fullName;

    /**
     * Creates a client.
     *
     * @param baseUrl server address such as {@code http://localhost:8080}
     */
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** @return the server address in use */
    public String baseUrl() {
        return baseUrl;
    }

    /** @return the logged-in user's role (ADMIN or RECEPTIONIST), or null before login */
    public String role() {
        return role;
    }

    /** @return the logged-in user's display name, or null before login */
    public String fullName() {
        return fullName;
    }

    /** @return true when the user is an administrator */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /**
     * Logs in and remembers the session token.
     *
     * @param username login name
     * @param password clear-text password (wiped by the caller)
     * @throws ApiClientException on wrong credentials or connection problems
     */
    public void login(String username, char[] password) {
        Map<String, Object> body = Json.object("username", username, "password", new String(password));
        Map<String, Object> reply = object(send("POST", "/api/auth/login", Json.write(body)));
        this.token = String.valueOf(reply.get("token"));
        Object user = reply.get("user");
        if (user instanceof Map<?, ?> map) {
            this.role = String.valueOf(map.get("role"));
            this.fullName = String.valueOf(map.get("fullName"));
        }
    }

    /** Ends the server session (errors are ignored: the token is dropped either way). */
    public void logout() {
        try {
            send("POST", "/api/auth/logout", null);
        } catch (ApiClientException ignored) {
            // the session may already have expired
        } finally {
            token = null;
            role = null;
            fullName = null;
        }
    }

    /**
     * Sends a GET and parses a JSON object.
     *
     * @param path  path such as {@code /api/dentists}
     * @param query optional query parameters (null values are skipped)
     * @return the parsed object
     */
    public Map<String, Object> getObject(String path, Map<String, String> query) {
        return object(send("GET", path + queryString(query), null));
    }

    /**
     * Sends a GET and returns the raw reply (text receipts, CSV files).
     *
     * @param path  path
     * @param query optional query parameters
     * @return the reply
     */
    public Reply getRaw(String path, Map<String, String> query) {
        return send("GET", path + queryString(query), null);
    }

    /**
     * Sends a JSON POST.
     *
     * @param path path
     * @param body request body (may be null for an empty body)
     * @return the parsed JSON object answer
     */
    public Map<String, Object> post(String path, Map<String, Object> body) {
        return object(send("POST", path, body == null ? null : Json.write(body)));
    }

    /**
     * Sends a JSON PUT.
     *
     * @param path path
     * @param body request body
     * @return the parsed JSON object answer
     */
    public Map<String, Object> put(String path, Map<String, Object> body) {
        return object(send("PUT", path, Json.write(body)));
    }

    /**
     * Sends a DELETE.
     *
     * @param path path
     * @return the parsed JSON object answer
     */
    public Map<String, Object> delete(String path) {
        return object(send("DELETE", path, null));
    }

    /**
     * Reads the {@code items} array of a list envelope.
     *
     * @param envelope {@code {"count":n,"items":[...]}}
     * @return the items as maps
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> items(Map<String, Object> envelope) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object items = envelope.get("items");
        if (items instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Reply reply) {
        String text = reply.text().strip();
        if (text.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = Json.parse(text);
        return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            sb.append(sb.length() == 0 ? '?' : '&')
                    .append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(e.getValue().trim(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private Reply send(String method, String path, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, text/plain, text/csv");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json; charset=utf-8");
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<byte[]> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ApiClientException(0, "UNREACHABLE",
                    "Cannot reach the server at " + baseUrl + ". Is it running?", List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiClientException(0, "INTERRUPTED", "The request was interrupted", List.of());
        }
        Reply reply = new Reply(response.statusCode(), response.body());
        if (response.statusCode() >= 400) {
            throw toException(reply);
        }
        return reply;
    }

    private static ApiClientException toException(Reply reply) {
        String fallback = "Server returned HTTP " + reply.status();
        try {
            Object parsed = Json.parse(reply.text());
            if (parsed instanceof Map<?, ?> top && top.get("error") instanceof Map<?, ?> error) {
                List<String> details = new ArrayList<>();
                if (error.get("details") instanceof List<?> list) {
                    for (Object d : list) {
                        if (d instanceof Map<?, ?> m) {
                            details.add(m.get("field") + ": " + m.get("message"));
                        }
                    }
                }
                String message = error.get("message") == null ? fallback : String.valueOf(error.get("message"));
                String code = error.get("code") == null ? "" : String.valueOf(error.get("code"));
                return new ApiClientException(reply.status(), code, message, details);
            }
        } catch (RuntimeException ignored) {
            // not JSON: use the fallback
        }
        return new ApiClientException(reply.status(), "", fallback, List.of());
    }
}
