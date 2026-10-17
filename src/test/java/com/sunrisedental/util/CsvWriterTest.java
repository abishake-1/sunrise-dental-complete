package com.sunrisedental.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writesHeaderAndRowsWithCrlf() {
        String csv = CsvWriter.toCsv(List.of("name", "total", "day"),
                List.of(Arrays.asList("Dr. A", 3L, LocalDate.of(2026, 10, 12)),
                        Arrays.asList("Dr. B", new BigDecimal("97.50"), null)));
        assertEquals("name,total,day\r\nDr. A,3,2026-10-12\r\nDr. B,97.50,\r\n", csv);
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        String csv = CsvWriter.toCsv(List.of("v"), List.of(List.of("a,b"), List.of("say \"hi\""), List.of("two\nlines")));
        assertEquals("v\r\n\"a,b\"\r\n\"say \"\"hi\"\"\"\r\n\"two\nlines\"\r\n", csv);
    }

    @Test
    void neutralisesSpreadsheetFormulasInTextButNotInNumbers() {
        assertEquals("'=SUM(A1)", CsvWriter.cell("=SUM(A1)"));
        assertEquals("'+1", CsvWriter.cell("+1"));
        assertEquals("'-1", CsvWriter.cell("-1"));
        assertEquals("'@x", CsvWriter.cell("@x"));
        assertEquals("-5", CsvWriter.cell(-5L));
        assertEquals("-0.50", CsvWriter.cell(new BigDecimal("-0.50")));
        assertEquals("", CsvWriter.cell(null));
        assertEquals("", CsvWriter.cell(""));
    }

    @Test
    void bigDecimalsNeverUseScientificNotation() {
        assertEquals("100000000", CsvWriter.cell(new BigDecimal("1E+8")));
    }
}
