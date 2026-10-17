package com.sunrisedental.api;

/**
 * One validation problem.
 *
 * @param field   the offending field (or query parameter)
 * @param message what is wrong
 */
public record FieldError(String field, String message) {
}
