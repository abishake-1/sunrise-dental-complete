package com.sunrisedental.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An error that maps directly to an HTTP status, an error code and optional field details and headers.
 * Handlers and filters throw it; {@link HttpResponder} renders it in the common error format.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient List<FieldError> details;
    private final transient Map<String, String> headers;

    /**
     * Creates the exception.
     *
     * @param status  HTTP status
     * @param code    stable machine-readable code
     * @param message human-readable message
     * @param details field-level problems (may be empty)
     * @param headers extra response headers (may be empty)
     */
    public ApiException(int status, String code, String message, List<FieldError> details,
                        Map<String, String> headers) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
        this.headers = Map.copyOf(headers);
    }

    /** @return the HTTP status */
    public int status() {
        return status;
    }

    /** @return the machine-readable code */
    public String code() {
        return code;
    }

    /** @return field-level problems, possibly empty */
    public List<FieldError> details() {
        return details;
    }

    /** @return extra response headers, possibly empty */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * @param code    error code
     * @param message message
     * @return a 400 error
     */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message, List.of(), Map.of());
    }

    /**
     * @param message message
     * @return a 401 error with a Bearer challenge
     */
    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message, List.of(),
                Map.of("WWW-Authenticate", "Bearer realm=\"sunrise-dental\""));
    }

    /**
     * @param message message
     * @return a 403 error
     */
    public static ApiException forbidden(String message) {
        return new ApiException(403, "FORBIDDEN", message, List.of(), Map.of());
    }

    /**
     * @param message message
     * @return a 404 error
     */
    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message, List.of(), Map.of());
    }

    /**
     * @param allowed the methods the resource supports
     * @return a 405 error with an Allow header
     */
    public static ApiException methodNotAllowed(Collection<String> allowed) {
        return new ApiException(405, "METHOD_NOT_ALLOWED", "Method not allowed for this endpoint", List.of(),
                Map.of("Allow", String.join(", ", allowed)));
    }

    /** @return a 413 error */
    public static ApiException payloadTooLarge() {
        return new ApiException(413, "PAYLOAD_TOO_LARGE", "Request body must not exceed 1 MB", List.of(), Map.of());
    }

    /** @return a 415 error */
    public static ApiException unsupportedMediaType() {
        return new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", List.of(),
                Map.of());
    }
}
