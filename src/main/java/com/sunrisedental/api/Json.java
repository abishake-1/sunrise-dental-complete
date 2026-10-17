package com.sunrisedental.api;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-written JSON reader and writer (RFC 8259) for the REST layer.
 *
 * <p>Reading yields {@code Map<String,Object>} (insertion ordered), {@code List<Object>}, {@code String},
 * {@code Long} (integers that fit), {@code BigDecimal} (everything else numeric), {@code Boolean} and
 * {@code null}. It is deliberately strict: duplicate object keys, trailing content, control characters in
 * strings and nesting deeper than 32 levels are rejected.
 *
 * <p>Writing supports maps, collections, arrays, strings, numbers, booleans, enums and {@code java.time} values.
 */
public final class Json {

    private static final int MAX_DEPTH = 32;
    private static final int MAX_NUMBER_LENGTH = 32;

    private Json() {
    }

    /**
     * Parses a JSON document.
     *
     * @param text the JSON text
     * @return the parsed value (see class comment for the mapping)
     * @throws JsonParseException if the text is not valid JSON
     */
    public static Object parse(String text) {
        return new Parser(text).parseDocument();
    }

    /**
     * Serialises a value to compact JSON.
     *
     * @param value a map, collection, array, string, number, boolean, enum, temporal or {@code null}
     * @return the JSON text
     * @throws IllegalArgumentException for unsupported types or non-finite numbers
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    /**
     * Builds an ordered object from alternating keys and values.
     *
     * @param keysAndValues key1, value1, key2, value2, ...
     * @return a mutable ordered map
     */
    public static Map<String, Object> object(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------ writer

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(out, s);
        } else if (value instanceof Boolean b) {
            out.append(b.booleanValue());
        } else if (value instanceof BigDecimal d) {
            out.append(d.toPlainString());
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON cannot represent " + d);
            }
            out.append(value);
        } else if (value instanceof Number n) {
            out.append(n);
        } else if (value instanceof Enum<?> e) {
            quote(out, e.name());
        } else if (value instanceof Temporal) {
            quote(out, value.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeMap(out, map);
        } else if (value instanceof Collection<?> collection) {
            writeIterable(out, collection);
        } else if (value instanceof Object[] array) {
            writeIterable(out, java.util.Arrays.asList(array));
        } else {
            throw new IllegalArgumentException("Cannot serialise " + value.getClass().getName());
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            quote(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeIterable(StringBuilder out, Iterable<?> items) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, item);
        }
        out.append(']');
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parseDocument() {
            skipWhitespace();
            Object value = parseValue(0);
            skipWhitespace();
            if (pos < text.length()) {
                throw error("unexpected content after the JSON value");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting is too deep");
            }
            if (pos >= text.length()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject(depth);
                case '[' -> parseArray(depth);
                case '"' -> parseString();
                case 't' -> {
                    expectLiteral("true");
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    expectLiteral("false");
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    expectLiteral("null");
                    yield null;
                }
                default -> {
                    if (c == '-' || isDigit(c)) {
                        yield parseNumber();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private Map<String, Object> parseObject(int depth) {
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (consumeIf('}')) {
                return map;
            }
            while (true) {
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != '"') {
                    throw error("expected a string key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue(depth + 1);
                if (map.containsKey(key)) {
                    throw error("duplicate key '" + key + "'");
                }
                map.put(key, value);
                skipWhitespace();
                if (consumeIf(',')) {
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> parseArray(int depth) {
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (consumeIf(']')) {
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(parseValue(depth + 1));
                skipWhitespace();
                if (consumeIf(',')) {
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String parseString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw error("control character in string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw error("unterminated escape");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(parseUnicodeEscape());
                    default -> throw error("invalid escape '\\" + e + "'");
                }
            }
        }

        private char parseUnicodeEscape() {
            if (pos + 4 > text.length()) {
                throw error("incomplete unicode escape");
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(pos + i), 16);
                if (digit < 0) {
                    throw error("invalid unicode escape");
                }
                code = code * 16 + digit;
            }
            pos += 4;
            return (char) code;
        }

        private Object parseNumber() {
            int start = pos;
            if (consumeIf('-') && pos >= text.length()) {
                throw error("invalid number");
            }
            if (pos < text.length() && text.charAt(pos) == '0') {
                pos++;
            } else if (pos < text.length() && isDigit(text.charAt(pos))) {
                while (pos < text.length() && isDigit(text.charAt(pos))) {
                    pos++;
                }
            } else {
                throw error("invalid number");
            }
            boolean integral = true;
            if (consumeIf('.')) {
                integral = false;
                requireDigits();
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                requireDigits();
            }
            String token = text.substring(start, pos);
            if (token.length() > MAX_NUMBER_LENGTH) {
                throw error("number is too long");
            }
            if (integral) {
                try {
                    return Long.parseLong(token);
                } catch (NumberFormatException e) {
                    return new BigDecimal(token);
                }
            }
            return new BigDecimal(token);
        }

        private void requireDigits() {
            int begin = pos;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos == begin) {
                throw error("invalid number");
            }
        }

        private void expectLiteral(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw error("unexpected token");
            }
            pos += literal.length();
        }

        private void expect(char c) {
            if (!consumeIf(c)) {
                throw error("expected '" + c + "'");
            }
        }

        private boolean consumeIf(char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private JsonParseException error(String message) {
            return new JsonParseException("Invalid JSON at position " + pos + ": " + message);
        }
    }
}
