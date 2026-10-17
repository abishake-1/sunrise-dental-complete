package com.sunrisedental.util;

import java.math.BigDecimal;
import java.util.List;

/**
 * Writes RFC 4180 CSV with plain Java: comma separated, CRLF line ends, fields containing a comma, quote or
 * line break wrapped in quotes with embedded quotes doubled. Text cells that start with {@code = + - @}, tab or
 * carriage return get a leading apostrophe so a spreadsheet cannot execute them as formulas (CSV injection).
 */
public final class CsvWriter {

    private static final String EOL = "\r\n";

    private CsvWriter() {
    }

    /**
     * Renders a table.
     *
     * @param columns header names
     * @param rows    rows of values (null, numbers, temporals and strings are supported)
     * @return the CSV text, including the header line
     */
    public static String toCsv(List<String> columns, List<List<Object>> rows) {
        StringBuilder out = new StringBuilder();
        appendLine(out, columns.stream().map(c -> (Object) c).toList());
        for (List<Object> row : rows) {
            appendLine(out, row);
        }
        return out.toString();
    }

    private static void appendLine(StringBuilder out, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append(EOL);
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        if (value instanceof String && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
