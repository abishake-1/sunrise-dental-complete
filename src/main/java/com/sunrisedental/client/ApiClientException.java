package com.sunrisedental.client;

import java.util.List;

/** An error answered by the REST server (or a connection problem), already phrased for a dialog box. */
public final class ApiClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient List<String> details;

    /**
     * Creates the exception.
     *
     * @param status  HTTP status, or 0 when the server could not be reached
     * @param code    machine-readable error code (may be empty)
     * @param message human-readable summary
     * @param details one line per field error
     */
    public ApiClientException(int status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    /** @return the HTTP status, 0 if there was no response */
    public int status() {
        return status;
    }

    /** @return the server's error code */
    public String code() {
        return code;
    }

    /** @return the message plus every field error, one per line */
    public String fullMessage() {
        if (details.isEmpty()) {
            return getMessage();
        }
        return getMessage() + "\n\n- " + String.join("\n- ", details);
    }
}
