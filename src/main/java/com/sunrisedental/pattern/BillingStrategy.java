package com.sunrisedental.pattern;

/** Strategy: decides the discount and tax percentages of a bill. */
public interface BillingStrategy {

    /**
     * Computes the billing terms.
     *
     * @param context patient and request facts
     * @return discount and tax percentages
     * @throws com.sunrisedental.util.ValidationException if the scheme does not apply to the context
     */
    BillingTerms calculateTerms(BillingContext context);

    /** @return the strategy's display name */
    String name();
}
