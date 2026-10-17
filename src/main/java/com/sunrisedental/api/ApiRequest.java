package com.sunrisedental.api;

import com.sun.net.httpserver.HttpExchange;
import com.sunrisedental.model.User;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A parsed HTTP request: path parameters, query string, headers, authenticated user and JSON body. */
public final class ApiRequest {

    /** Exchange attribute holding the authenticated {@link User} (set by the AuthFilter). */
    public static final String USER_ATTRIBUTE = "sunrise.user";
    /** Exchange attribute holding the session token (set by the AuthFilter). */
    public static final String TOKEN_ATTRIBUTE = "sunrise.token";

    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int MAX_QUERY_PARAMETERS = 50;

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;

    ApiRequest(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = Map.copyOf(pathParams);
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    /** @return the HTTP method */
    public String method() {
        return exchange.getRequestMethod();
    }

    /** @return the raw request path */
    public String path() {
        return exchange.getRequestURI().getRawPath();
    }

    /**
     * @param name path variable name, e.g. {@code id}
     * @return its decoded value
     */
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /**
     * Reads a path variable that must be a positive whole number.
     *
     * @param name path variable name
     * @return the number
     * @throws ApiException 400 if it is not a positive whole number
     */
    public long pathId(String name) {
        String raw = pathParams.get(name);
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the error below
        }
        throw ApiException.badRequest("INVALID_PATH_PARAMETER",
                "Path parameter '" + name + "' must be a positive whole number");
    }

    /** @return the decoded query parameters (first occurrence wins) */
    public Map<String, String> query() {
        return query;
    }

    /**
     * @param name header name
     * @return the first value, or {@code null}
     */
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** @return the authenticated user, or {@code null} on public routes */
    public User user() {
        Object value = exchange.getAttribute(USER_ATTRIBUTE);
        return value instanceof User u ? u : null;
    }

    /**
     * @return the authenticated user
     * @throws ApiException 401 if there is none
     */
    public User requireUser() {
        User user = user();
        if (user == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return user;
    }

    /** @return the session token of this request, or {@code null} */
    public String token() {
        Object value = exchange.getAttribute(TOKEN_ATTRIBUTE);
        return value instanceof String s ? s : null;
    }

    /**
     * Reads the body as a JSON object. The Content-Type must be {@code application/json}, the body at most
     * 1 MB, and the top-level value an object.
     *
     * @return the parsed object
     * @throws ApiException         415, 413 or 400 for the respective problems
     * @throws JsonParseException   if the body is not valid JSON
     */
    public Map<String, Object> jsonObject() {
        return readJsonObject(true);
    }

    /**
     * Like {@link #jsonObject()}, but a request without any body yields an empty object.
     *
     * @return the parsed object, empty when there is no body
     */
    public Map<String, Object> optionalJsonObject() {
        return readJsonObject(false);
    }

    private Map<String, Object> readJsonObject(boolean required) {
        byte[] bytes;
        try {
            bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length > MAX_BODY_BYTES) {
            throw ApiException.payloadTooLarge();
        }
        if (bytes.length == 0) {
            if (required) {
                throw ApiException.badRequest("BODY_REQUIRED", "A JSON request body is required");
            }
            return new LinkedHashMap<>();
        }
        String contentType = header("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw ApiException.unsupportedMediaType();
        }
        Object parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> map)) {
            throw ApiException.badRequest("INVALID_JSON", "The request body must be a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = rawQuery.split("&");
        if (pairs.length > MAX_QUERY_PARAMETERS) {
            throw ApiException.badRequest("INVALID_QUERY", "Too many query parameters");
        }
        try {
            for (String pair : pairs) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                result.putIfAbsent(key, value);
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_QUERY", "The query string is not correctly URL-encoded");
        }
        return Collections.unmodifiableMap(result);
    }
}
