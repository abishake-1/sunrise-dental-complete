package com.sunrisedental.api;

import com.sunrisedental.dto.Receipt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Shared sample receipt for the rendering and HTTP tests. */
final class SampleData {

    private SampleData() {
    }

    static Receipt receipt(long billId, String patientName, String status) {
        boolean paid = "PAID".equals(status);
        return new Receipt(Receipt.numberFor(billId), billId, LocalDateTime.of(2026, 10, 12, 14, 5), "Front Desk",
                "APT-0010", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0), patientName, "0771234567",
                "Dr. Maria Santos", "FIL", "Dental Filling", new BigDecimal("90.00"), new BigDecimal("25.00"),
                new BigDecimal("115.00"), "SENIOR", new BigDecimal("15.00"), new BigDecimal("17.25"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("97.75"), status,
                paid ? "CASH" : null, paid ? LocalDateTime.of(2026, 10, 12, 14, 30) : null);
    }
}
