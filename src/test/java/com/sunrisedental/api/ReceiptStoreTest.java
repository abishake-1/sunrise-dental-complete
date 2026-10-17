package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dto.ClinicProfile;
import com.sunrisedental.dto.Receipt;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReceiptStoreTest {

    private static void deleteAll(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                Files.delete(p);
            }
        }
        Files.delete(dir);
    }

    @Test
    void writesBothFormatsAndReplacesThemOnResave() throws Exception {
        Path dir = Files.createTempDirectory("rcp-test").resolve("nested");
        try {
            ReceiptStore store = new ReceiptStore(dir, EnumSet.allOf(ReceiptStore.Format.class), ClinicProfile.defaults());
            List<String> names = store.save(SampleData.receipt(7, "Nimal Perera", "UNPAID"));
            assertEquals(List.of("RCP-000007.txt", "RCP-000007.html"), names);
            assertTrue(Files.readString(dir.resolve("RCP-000007.txt")).contains("UNPAID"));

            store.save(SampleData.receipt(7, "Nimal Perera", "PAID"));
            String txt = Files.readString(dir.resolve("RCP-000007.txt"), StandardCharsets.UTF_8);
            assertTrue(txt.contains("PAID") && !txt.contains("UNPAID"));
            try (var files = Files.list(dir)) {
                assertEquals(2L, files.count(), "no temp files may be left behind");
            }
        } finally {
            deleteAll(dir);
            Files.delete(dir.getParent());
        }
    }

    @Test
    void honoursTheConfiguredFormats() throws Exception {
        Path dir = Files.createTempDirectory("rcp-test");
        try {
            ReceiptStore store = new ReceiptStore(dir, Set.of(ReceiptStore.Format.TXT), ClinicProfile.defaults());
            assertEquals(List.of("RCP-000008.txt"), store.save(SampleData.receipt(8, "A B", "UNPAID")));
            assertEquals(List.of(), new ReceiptStore(dir, Set.of(), ClinicProfile.defaults())
                    .save(SampleData.receipt(9, "A B", "UNPAID")));
        } finally {
            deleteAll(dir);
        }
    }

    @Test
    void refusesReceiptNumbersThatCouldEscapeTheFolder() throws Exception {
        Path dir = Files.createTempDirectory("rcp-test");
        try {
            ReceiptStore store = new ReceiptStore(dir, Set.of(ReceiptStore.Format.TXT), ClinicProfile.defaults());
            Receipt base = SampleData.receipt(7, "A B", "UNPAID");
            Receipt evil = new Receipt("../../etc/passwd", 7, base.issuedAt(), base.issuedBy(), base.appointmentNumber(),
                    base.appointmentDate(), base.appointmentTime(), base.patientName(), base.patientPhone(),
                    base.dentistName(), base.treatmentCode(), base.treatmentName(), base.treatmentPrice(),
                    base.consultationFee(), base.subtotal(), base.billingType(), base.discountPercent(),
                    base.discountAmount(), base.taxPercent(), base.taxAmount(), base.total(), base.paymentStatus(),
                    null, null);
            assertThrows(IllegalArgumentException.class, () -> store.save(evil));
        } finally {
            deleteAll(dir);
        }
    }
}
