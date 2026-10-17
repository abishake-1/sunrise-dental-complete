package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunrisedental.pattern.BillingTerms;
import com.sunrisedental.util.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillCalculatorTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void totalIsTreatmentPlusConsultationMinusDiscount() {
        BillAmounts a = BillCalculator.calculate(d("90.00"), d("25.00"), new BillingTerms(d("15"), d("0")));
        assertEquals("115.00", a.subtotal().toPlainString());
        assertEquals("17.25", a.discountAmount().toPlainString());
        assertEquals("0.00", a.taxAmount().toPlainString());
        assertEquals("97.75", a.total().toPlainString());
        assertEquals("15.00", a.discountPercent().toPlainString());
    }

    @Test
    void decimalFractionsAreExactUnlikeDouble() {
        BillAmounts a = BillCalculator.calculate(d("0.10"), d("0.20"), new BillingTerms(d("0"), d("0")));
        assertEquals("0.30", a.total().toPlainString());
        assertEquals(false, 0.1 + 0.2 == 0.3); // what BigDecimal protects us from
    }

    @Test
    void everyStepRoundsHalfUpSoLinesAddUpToTheTotal() {
        // 10.05 * 12.5% = 1.25625 -> 1.26 ; 8.79 * 8% = 0.7032 -> 0.70
        BillAmounts a = BillCalculator.calculate(d("10.05"), d("0"), new BillingTerms(d("12.5"), d("8")));
        assertEquals("1.26", a.discountAmount().toPlainString());
        assertEquals("0.70", a.taxAmount().toPlainString());
        assertEquals("9.49", a.total().toPlainString());
        assertEquals(0, a.subtotal().subtract(a.discountAmount()).add(a.taxAmount()).compareTo(a.total()));
    }

    @Test
    void taxIsChargedOnTheDiscountedAmount() {
        BillAmounts a = BillCalculator.calculate(d("100"), d("0"), new BillingTerms(d("10"), d("8")));
        assertEquals("10.00", a.discountAmount().toPlainString());
        assertEquals("7.20", a.taxAmount().toPlainString());
        assertEquals("97.20", a.total().toPlainString());
    }

    @Test
    void fullDiscountGivesZeroTotal() {
        assertEquals("0.00", BillCalculator.calculate(d("50"), d("25"), new BillingTerms(d("100"), d("0")))
                .total().toPlainString());
    }

    @Test
    void invalidInputsAreRejected() {
        BillingTerms none = new BillingTerms(d("0"), d("0"));
        assertThrows(ValidationException.class, () -> BillCalculator.calculate(d("-1"), d("0"), none));
        assertThrows(ValidationException.class, () -> BillCalculator.calculate(d("10"), d("-0.01"), none));
        assertThrows(ValidationException.class, () -> BillCalculator.calculate(d("10.005"), d("0"), none));
        assertThrows(ValidationException.class, () -> BillCalculator.calculate(null, d("0"), none));
        assertThrows(ValidationException.class, () -> BillCalculator.calculate(d("99999999.99"), d("1"), none));
        assertThrows(ValidationException.class, () -> new BillingTerms(d("100.01"), d("0")));
    }
}
