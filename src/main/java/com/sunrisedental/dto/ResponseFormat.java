package com.sunrisedental.dto;

import java.util.Locale;
import java.util.Optional;

/** Output formats selectable with {@code ?format=}. */
public enum ResponseFormat {
    JSON, CSV, TEXT, HTML;

    /**
     * Parses a query value case-insensitively.
     *
     * @param value text such as "csv"
     * @return the format, or empty if unknown
     */
    public static Optional<ResponseFormat> parse(String value) {
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
