package com.sunrisedental.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What a route handler returns: a status, an optional JSON body and optional headers. */
public final class ApiResponse {

    private final int status;
    private final Object body;
    private final String rawBody;
    private final String contentType;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    private ApiResponse(int status, Object body, String rawBody, String contentType) {
        this.status = status;
        this.body = body;
        this.rawBody = rawBody;
        this.contentType = contentType;
    }

    private ApiResponse(int status, Object body) {
        this(status, body, null, null);
    }

    /**
     * A response with a ready-made text body (plain text, HTML, CSV).
     *
     * @param status      HTTP status
     * @param contentType full Content-Type value, e.g. {@code text/plain; charset=utf-8}
     * @param body        the text, sent as UTF-8
     * @return the response
     */
    public static ApiResponse text(int status, String contentType, String body) {
        return new ApiResponse(status, null, body, contentType);
    }

    /**
     * @param body JSON-serialisable body
     * @return a 200 response
     */
    public static ApiResponse ok(Object body) {
        return new ApiResponse(200, body);
    }

    /**
     * @param body     JSON-serialisable body
     * @param location value of the Location header
     * @return a 201 response
     */
    public static ApiResponse created(Object body, String location) {
        return new ApiResponse(201, body).header("Location", location);
    }

    /** @return a 204 response without body */
    public static ApiResponse noContent() {
        return new ApiResponse(204, null);
    }

    /**
     * Adds a response header.
     *
     * @param name  header name
     * @param value header value
     * @return this response
     */
    public ApiResponse header(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        return this;
    }

    /** @return the HTTP status */
    public int status() {
        return status;
    }

    /** @return the text body of a {@link #text} response, or {@code null} */
    public String rawBody() {
        return rawBody;
    }

    /** @return the Content-Type of a {@link #text} response, or {@code null} */
    public String contentType() {
        return contentType;
    }

    /** @return the body, or {@code null} */
    public Object body() {
        return body;
    }

    /** @return the extra headers */
    public Map<String, List<String>> headers() {
        return headers;
    }
}
