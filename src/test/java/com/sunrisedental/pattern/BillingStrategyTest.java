package com.sunrisedental.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunrisedental.model.BillingType;
import com.sunrisedental.util.AppConfig;
import com.sunrisedental.util.ValidationException;
import java.math.BigDecimal;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class BillingStrategyTest {

    private final BillingStrategyFactory factory = new BillingStrategyFactory(config());

    private static AppConfig config() {
        Properties p = new Properties();
        p.setProperty("clinic.consultation.fee", "25.00");
        p.setProperty("clinic.consultation.treatment.code", "CHK");
        p.setProperty("billing.tax.percent", "5");
        p.setProperty("billing.senior.discount.percent", "15");
        p.setProperty("billing.senior.min.age", "60");
        p.setProperty("billing.insurance.default.coverage.percent", "70");
        return new AppConfig(p);
    }

    @Test
    void standardHasNoDiscountButConfiguredTax() {
        BillingTerms terms = factory.create("standard").calculateTerms(new BillingContext(30, null));
        assertEquals(0, terms.discountPercent().compareTo(BigDecimal.ZERO));
        assertEquals(0, terms.taxPercent().compareTo(new BigDecimal("5")));
    }

    @Test
    void seniorDiscountAppliesFromMinimumAge() {
        BillingStrategy senior = factory.create(BillingType.SENIOR);
        assertEquals(0, senior.calculateTerms(new BillingContext(60, null))
                .discountPercent().compareTo(new BigDecimal("15")));
        assertThrows(ValidationException.class, () -> senior.calculateTerms(new BillingContext(59, null)));
    }

    @Test
    void insuranceUsesRequestedOrDefaultCoverage() {
        BillingStrategy insurance = factory.create("INSURANCE");
        assertEquals(0, insurance.calculateTerms(new BillingContext(40, null))
                .discountPercent().compareTo(new BigDecimal("70")));
        assertEquals(0, insurance.calculateTerms(new BillingContext(40, new BigDecimal("50")))
                .discountPercent().compareTo(new BigDecimal("50")));
        assertThrows(ValidationException.class,
                () -> insurance.calculateTerms(new BillingContext(40, new BigDecimal("101"))));
    }

    @Test
    void factoryRejectsUnknownTypeAndDefaultsToStandard() {
        assertThrows(ValidationException.class, () -> factory.create("GOLD"));
        assertInstanceOf(StandardBillingStrategy.class, factory.create((String) null));
    }
}
