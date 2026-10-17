package com.sunrisedental.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A report result: column names plus rows of values (BigDecimal, Long, String, LocalDate, ... or null).
 * The same structure feeds the JSON and the CSV output.
 *
 * @param report     report slug
 * @param title      human-readable title
 * @param parameters the parameters the report was run with
 * @param columns    column names (camelCase)
 * @param rows       the data rows
 */
public record ReportTable(String report, String title, Map<String, Object> parameters, List<String> columns,
                          List<List<Object>> rows) {

    /**
     * @param newParameters the parameters to record
     * @return a copy that remembers how the report was run
     */
    public ReportTable withParameters(Map<String, Object> newParameters) {
        return new ReportTable(report, title, new LinkedHashMap<>(newParameters), columns, rows);
    }
}
