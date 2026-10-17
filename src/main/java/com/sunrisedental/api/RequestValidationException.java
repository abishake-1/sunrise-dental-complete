package com.sunrisedental.api;

import com.sunrisedental.util.ValidationException;
import java.util.List;

/** A {@link ValidationException} that carries every field problem found in one request. */
public class RequestValidationException extends ValidationException {

    private static final long serialVersionUID = 1L;

    private final transient List<FieldError> details;

    /**
     * Creates the exception.
     *
     * @param details all problems found, never empty
     */
    public RequestValidationException(List<FieldError> details) {
        super("Validation failed: " + details.size() + " problem(s) found");
        this.details = List.copyOf(details);
    }

    /** @return all problems found */
    public List<FieldError> details() {
        return details;
    }
}
