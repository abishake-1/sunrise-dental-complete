package com.sunrisedental.pattern;

import com.sunrisedental.util.ValidationException;
import java.math.BigDecimal;

/** Discount for patients at or above a minimum age. */
public final class SeniorDiscountBillingStrategy implements BillingStrategy {

    private final BigDecimal discountPercent;
    private final int minAge;
    private final BigDecimal taxPercent;

    /**
     * Creates the strategy.
     *
     * @param discountPercent discount for eligible patients
     * @param minAge          minimum age, inclusive
     * @param taxPercent      tax percentage
     */
    public SeniorDiscountBillingStrategy(BigDecimal discountPercent, int minAge, BigDecimal taxPercent) {
        this.discountPercent = discountPercent;
        this.minAge = minAge;
        this.taxPercent = taxPercent;
    }

    @Override
    public BillingTerms calculateTerms(BillingContext context) {
        if (context.patientAge() < minAge) {
            throw new ValidationException("The senior discount requires an age of at least " + minAge);
        }
        return new BillingTerms(discountPercent, taxPercent);
    }

    @Override
    public String name() {
        return "SENIOR";
    }
}
