package com.sunrisedental.pattern;

import com.sunrisedental.util.Validator;
import java.math.BigDecimal;

/** The insurer covers a percentage; the bill shows the patient's remaining share. */
public final class InsuranceBillingStrategy implements BillingStrategy {

    private final BigDecimal defaultCoveragePercent;
    private final BigDecimal taxPercent;

    /**
     * Creates the strategy.
     *
     * @param defaultCoveragePercent coverage used when the request gives none
     * @param taxPercent             tax percentage
     */
    public InsuranceBillingStrategy(BigDecimal defaultCoveragePercent, BigDecimal taxPercent) {
        this.defaultCoveragePercent = defaultCoveragePercent;
        this.taxPercent = taxPercent;
    }

    @Override
    public BillingTerms calculateTerms(BillingContext context) {
        BigDecimal coverage = context.insuranceCoveragePercent() != null
                ? context.insuranceCoveragePercent() : defaultCoveragePercent;
        Validator.requirePercent(coverage, "insuranceCoveragePercent");
        return new BillingTerms(coverage, taxPercent);
    }

    @Override
    public String name() {
        return "INSURANCE";
    }
}
