package com.sunrisedental.service;

import com.sunrisedental.pattern.BillingTerms;
import com.sunrisedental.util.ValidationException;
import com.sunrisedental.util.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Bill arithmetic. All money is {@link BigDecimal}, never {@code double} or {@code float}:
 * <ul>
 *   <li>binary floating point cannot represent most decimal fractions (0.1 + 0.2 is 0.30000000000000004), so
 *       totals would drift by fractions of a cent and fail equality checks;</li>
 *   <li>{@code BigDecimal} stores an exact decimal value with an explicit scale and forces us to choose the
 *       rounding rule (here HALF_UP, the usual commercial rounding) wherever precision is lost;</li>
 *   <li>it matches the database type {@code DECIMAL(10,2)}, so what we compute is what is stored.</li>
 * </ul>
 * Each amount is rounded to cents <em>before</em> the next step, so the printed lines always add up exactly
 * to the total and the database CHECK {@code total = subtotal - discount + tax} holds.
 *
 * <pre>
 * subtotal = treatmentPrice + consultationFee
 * discount = round(subtotal * discountPercent / 100)
 * tax      = round((subtotal - discount) * taxPercent / 100)
 * total    = subtotal - discount + tax
 * </pre>
 * Two caveats when using BigDecimal: build values from strings (never {@code new BigDecimal(0.1)}), and compare
 * with {@code compareTo}, because {@code equals} treats 2.0 and 2.00 as different.
 */
public final class BillCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99"); // DECIMAL(10,2)

    private BillCalculator() {
    }

    /**
     * Calculates a bill.
     *
     * @param treatmentPrice  price of the treatment (not negative, at most 2 decimals)
     * @param consultationFee consultation fee (not negative, at most 2 decimals)
     * @param terms           discount and tax percentages chosen by the billing strategy
     * @return the exact amounts
     * @throws ValidationException if an input is invalid or the result does not fit the database column
     */
    public static BillAmounts calculate(BigDecimal treatmentPrice, BigDecimal consultationFee, BillingTerms terms) {
        BigDecimal price = money(treatmentPrice, "treatmentPrice");
        BigDecimal fee = money(consultationFee, "consultationFee");
        Validator.requireNonNull(terms, "terms");

        BigDecimal subtotal = price.add(fee);
        BigDecimal discount = percentOf(subtotal, terms.discountPercent());
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = percentOf(taxable, terms.taxPercent());
        BigDecimal total = taxable.add(tax);
        if (subtotal.compareTo(MAX_AMOUNT) > 0 || total.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("The bill amount is too large");
        }
        return new BillAmounts(price, fee, subtotal,
                terms.discountPercent().setScale(2, RoundingMode.HALF_UP), discount,
                terms.taxPercent().setScale(2, RoundingMode.HALF_UP), tax, total);
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value, String field) {
        Validator.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new ValidationException(field + " must not be negative");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(field + " must have at most 2 decimals");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
