package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesAllValueTypes() {
        Object parsed = Json.parse(" {\"a\":1,\"b\":[true,false,null],\"c\":\"x\\ny\\u00e9\",\"d\":1.50,\"e\":-3e2} ");
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertEquals(1L, map.get("a"));
        assertEquals(List.of(true, false), ((List<?>) map.get("b")).subList(0, 2));
        assertEquals("x\n\u00e9y".replace("\u00e9y", "y\u00e9"), map.get("c"));
        assertEquals(new BigDecimal("1.50"), map.get("d"));
        assertEquals(0, new BigDecimal("-300").compareTo((BigDecimal) map.get("e")));
    }

    @Test
    void rejectsInvalidDocuments() {
        for (String bad : List.of("", "{", "{\"a\":1,}", "{\"a\":1} x", "[1 2]", "{'a':1}", "{\"a\":01}",
                "{\"a\":\"line\nbreak\"}", "{\"a\":1,\"a\":2}", "nul", "{\"a\":\"\\q\"}", "-", "1.")) {
            assertThrows(JsonParseException.class, () -> Json.parse(bad));
        }
        assertThrows(JsonParseException.class, () -> Json.parse("[".repeat(40) + "]".repeat(40)));
    }

    @Test
    void writesEscapedCompactJson() {
        Map<String, Object> value = Json.object("text", "quote\" backslash\\ tab\t ctrl\u0001", "n", 5, "d", new BigDecimal("2.50"),
                "list", List.of(1, "two"), "none", null, "date", LocalDate.of(2026, 10, 12), "time", LocalTime.of(9, 0));
        assertEquals("{\"text\":\"quote\\\" backslash\\\\ tab\\t ctrl\\u0001\",\"n\":5,\"d\":2.50,"
                + "\"list\":[1,\"two\"],\"none\":null,\"date\":\"2026-10-12\",\"time\":\"09:00\"}", Json.write(value));
    }

    @Test
    void roundTripsWhatItWrites() {
        Map<String, Object> value = Json.object("name", "Zo\u00eb \"Q\"", "values", List.of(1L, 2L), "ok", true);
        assertEquals(value, Json.parse(Json.write(value)));
    }

    @Test
    void refusesToWriteUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    }
}
