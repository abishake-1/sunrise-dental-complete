package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dto.ClinicProfile;
import com.sunrisedental.dto.Receipt;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReceiptRendererTest {

    private static final ClinicProfile CLINIC = new ClinicProfile("Sunrise Dental Clinic", "12 Main Street",
            "0112345678", "LKR");

    @Test
    void textReceiptIsFixedWidthWithRightAlignedAmounts() {
        String text = ReceiptRenderer.text(SampleData.receipt(7, "Nimal Perera", "UNPAID"), CLINIC);
        for (String line : text.split("\n")) {
            assertTrue(line.length() <= 42, "too wide: [" + line + "]");
        }
        assertTrue(text.contains("Sunrise Dental Clinic"));
        assertTrue(text.contains("RCP-000007"));
        assertTrue(text.contains("Consultation fee"));
        assertTrue(text.contains("Discount (15.00%)"));
        String total = text.lines().filter(l -> l.startsWith("TOTAL LKR")).findFirst().orElseThrow();
        assertEquals(42, total.length());
        assertTrue(total.endsWith("97.75"));
        assertTrue(text.lines().anyMatch(l -> l.startsWith("Discount") && l.endsWith("-17.25")));
        assertFalse(text.contains("Paid with"));
    }

    @Test
    void paidReceiptShowsPaymentDetails() {
        String text = ReceiptRenderer.text(SampleData.receipt(7, "Nimal Perera", "PAID"), CLINIC);
        assertTrue(text.contains("Paid with"));
        assertTrue(text.lines().anyMatch(l -> l.startsWith("Status") && l.endsWith("PAID")));
    }

    @Test
    void overlongTextIsTruncatedInsteadOfBreakingTheLayout() {
        String text = ReceiptRenderer.text(SampleData.receipt(7, "N".repeat(80), "UNPAID"), CLINIC);
        for (String line : text.split("\n")) {
            assertTrue(line.length() <= 42);
        }
        assertTrue(text.contains("..."));
    }

    @Test
    void largeAmountsGetThousandsSeparators() {
        assertEquals("1,234,567.80", ReceiptRenderer.money(new BigDecimal("1234567.80")));
        assertEquals("0.00", ReceiptRenderer.money(new BigDecimal("0.00")));
    }

    @Test
    void htmlEscapesEverythingDynamic() {
        String html = ReceiptRenderer.html(SampleData.receipt(7, "<script>alert(1)</script> & \"Bob\"", "UNPAID"), CLINIC);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Bob&quot;"));
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("97.75"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonListsLineItemsAndKeepsAmountsExact() {
        Map<String, Object> json = ReceiptRenderer.json(SampleData.receipt(7, "Nimal Perera", "UNPAID"), CLINIC);
        List<Object> items = (List<Object>) json.get("lineItems");
        assertEquals(2, items.size());
        String text = Json.write(json);
        assertTrue(text.contains("\"total\":97.75"));
        assertTrue(text.contains("\"subtotal\":115.00"));
        assertTrue(text.contains("\"receiptNumber\":\"RCP-000007\""));
        assertTrue(text.contains("\"method\":null"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void waivedConsultationFeeHasNoLineItem() {
        Receipt base = SampleData.receipt(7, "Nimal Perera", "UNPAID");
        Receipt waived = new Receipt(base.receiptNumber(), 7, base.issuedAt(), base.issuedBy(), base.appointmentNumber(),
                base.appointmentDate(), base.appointmentTime(), base.patientName(), base.patientPhone(),
                base.dentistName(), "CHK", "Check-up", new BigDecimal("25.00"), new BigDecimal("0.00"),
                new BigDecimal("25.00"), "STANDARD", new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("25.00"), "UNPAID", null, null);
        assertEquals(1, ((List<Object>) ReceiptRenderer.json(waived, CLINIC).get("lineItems")).size());
        assertFalse(ReceiptRenderer.text(waived, CLINIC).contains("Consultation fee"));
    }
}
