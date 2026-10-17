package com.sunrisedental.pattern;

import com.sunrisedental.util.Validator;
import java.math.BigDecimal;

/**
 * Result of a {@link BillingStrategy}: the percentages handed to {@code sp_calculate_bill}.
 *
 * @param discountPercent discount on the treatment price, 0..100
 * @param taxPercent      tax on the discounted amount, 0..100
 */
public record BillingTerms(BigDecimal discountPercent, BigDecimal taxPercent) {

    /** Validates both percentages. */
    public BillingTerms {
        Validator.requirePercent(discountPercent, "discountPercent");
        Validator.requirePercent(taxPercent, "taxPercent");
    }
}
