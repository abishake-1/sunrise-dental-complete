package com.sunrisedental.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sunrisedental.util.ConflictException;
import com.sunrisedental.util.NotFoundException;
import com.sunrisedental.util.ValidationException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes success and error responses in the API's consistent JSON format.
 *
 * <p>Error format:
 * <pre>
 * {"error":{"status":400,"code":"VALIDATION_FAILED","message":"...",
 *           "details":[{"field":"phone","message":"..."}],"path":"/api/...","timestamp":"..."}}
 * </pre>
 */
public final class HttpResponder {

    private static final Logger LOG = Logger.getLogger(HttpResponder.class.getName());
    private static final String JSON_TYPE = "application/json; charset=utf-8";

    private HttpResponder() {
    }

    /**
     * Writes a handler's response.
     *
     * @param exchange the exchange
     * @param response what to send
     * @throws IOException if the client connection fails
     */
    public static void send(HttpExchange exchange, ApiResponse response) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        response.headers().forEach((name, values) -> {
            if (!"Set-Cookie".equalsIgnoreCase(name)) {
                headers.remove(name); // a handler's value replaces a filter default (e.g. Content-Security-Policy)
            }
            values.forEach(value -> headers.add(name, value));
        });
        if (response.rawBody() != null) {
            byte[] bytes = response.rawBody().getBytes(StandardCharsets.UTF_8);
            headers.set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            return;
        }
        if (response.body() == null) {
            exchange.sendResponseHeaders(response.status(), -1);
            return;
        }
        byte[] bytes = Json.write(response.body()).getBytes(StandardCharsets.UTF_8);
        headers.set("Content-Type", JSON_TYPE);
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Maps any exception to the error format and sends it. Never throws; the exchange is closed afterwards.
     *
     * @param exchange the exchange
     * @param failure  what went wrong
     */
    public static void sendError(HttpExchange exchange, Throwable failure) {
        ApiException error = map(failure);
        try {
            Map<String, Object> body = Json.object(
                    "status", error.status(),
                    "code", error.code(),
                    "message", error.getMessage());
            if (!error.details().isEmpty()) {
                List<Object> details = new ArrayList<>();
                for (FieldError detail : error.details()) {
                    details.add(Json.object("field", detail.field(), "message", detail.message()));
                }
                body.put("details", details);
            }
            body.put("path", exchange.getRequestURI().getRawPath());
            body.put("timestamp", Instant.now());

            byte[] bytes = Json.write(Json.object("error", body)).getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            error.headers().forEach(headers::set);
            headers.set("Content-Type", JSON_TYPE);
            exchange.sendResponseHeaders(error.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Could not write error response", e);
        } finally {
            exchange.close();
        }
    }

    /**
     * Translates exceptions of every layer to an HTTP error. Unknown failures become a generic 500 whose
     * message carries only a reference id; details go to the log, never to the client.
     *
     * @param failure the exception
     * @return the API error to send
     */
    static ApiException map(Throwable failure) {
        if (failure instanceof ApiException api) {
            return api;
        }
        if (failure instanceof RequestValidationException validation) {
            return new ApiException(400, "VALIDATION_FAILED", validation.getMessage(), validation.details(), Map.of());
        }
        if (failure instanceof ValidationException validation) {
            return new ApiException(400, "VALIDATION_FAILED", validation.getMessage(), List.of(), Map.of());
        }
        if (failure instanceof JsonParseException json) {
            return ApiException.badRequest("INVALID_JSON", json.getMessage());
        }
        if (failure instanceof NotFoundException notFound) {
            return new ApiException(404, "NOT_FOUND", notFound.getMessage(), List.of(), Map.of());
        }
        if (failure instanceof ConflictException conflict) {
            return new ApiException(409, "CONFLICT", conflict.getMessage(), List.of(), Map.of());
        }
        String reference = UUID.randomUUID().toString().substring(0, 8);
        LOG.log(Level.SEVERE, "Unhandled error, reference " + reference, failure);
        return new ApiException(500, "INTERNAL_ERROR", "An unexpected error occurred (reference " + reference + ")",
                List.of(), Map.of());
    }
}
