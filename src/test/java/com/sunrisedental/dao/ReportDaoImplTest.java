package com.sunrisedental.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReportDaoImplTest {

    @Test
    void columnLabelsBecomeCamelCase() {
        assertEquals("dentistName", ReportDaoImpl.toCamelCase("dentist_name"));
        assertEquals("cancellationRatePercent", ReportDaoImpl.toCamelCase("cancellation_rate_percent"));
        assertEquals("total", ReportDaoImpl.toCamelCase("total"));
    }
}
