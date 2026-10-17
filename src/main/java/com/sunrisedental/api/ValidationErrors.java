package com.sunrisedental.api;

import java.util.ArrayList;
import java.util.List;

/** Collects field problems so a request can report all of them at once instead of only the first. */
public final class ValidationErrors {

    private final List<FieldError> errors = new ArrayList<>();

    /**
     * Records a problem.
     *
     * @param field   field or query parameter name
     * @param message what is wrong
     */
    public void add(String field, String message) {
        errors.add(new FieldError(field, message));
    }

    /** @return {@code true} if nothing has been recorded */
    public boolean isEmpty() {
        return errors.isEmpty();
    }

    /** @return the recorded problems */
    public List<FieldError> all() {
        return List.copyOf(errors);
    }

    /**
     * Throws if any problem was recorded.
     *
     * @throws RequestValidationException carrying all problems
     */
    public void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }
}
