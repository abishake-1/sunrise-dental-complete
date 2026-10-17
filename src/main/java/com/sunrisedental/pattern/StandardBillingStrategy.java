package com.sunrisedental.pattern;

import java.math.BigDecimal;

/** Full price: no discount, only the configured tax. */
public final class StandardBillingStrategy implements BillingStrategy {

    private final BigDecimal taxPercent;

    /**
     * Creates the strategy.
     *
     * @param taxPercent tax percentage from configuration
     */
    public StandardBillingStrategy(BigDecimal taxPercent) {
        this.taxPercent = taxPercent;
    }

    @Override
    public BillingTerms calculateTerms(BillingContext context) {
        return new BillingTerms(BigDecimal.ZERO.setScale(2), taxPercent);
    }

    @Override
    public String name() {
        return "STANDARD";
    }
}
