package com.sunrisedental.pattern;

import com.sunrisedental.model.BillingType;
import com.sunrisedental.util.AppConfig;
import java.util.Objects;

/** Factory: turns a {@link BillingType} (or its text form) into a configured {@link BillingStrategy}. */
public final class BillingStrategyFactory {

    private final AppConfig config;

    /**
     * Creates the factory.
     *
     * @param config source of rates and thresholds
     */
    public BillingStrategyFactory(AppConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Creates the strategy for a type.
     *
     * @param type the billing type
     * @return a configured strategy
     */
    public BillingStrategy create(BillingType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case STANDARD -> new StandardBillingStrategy(config.taxPercent());
            case SENIOR -> new SeniorDiscountBillingStrategy(
                    config.seniorDiscountPercent(), config.seniorMinAge(), config.taxPercent());
            case INSURANCE -> new InsuranceBillingStrategy(
                    config.defaultInsuranceCoveragePercent(), config.taxPercent());
        };
    }

    /**
     * Creates the strategy for a client-supplied name.
     *
     * @param name STANDARD, SENIOR or INSURANCE (case-insensitive); blank means STANDARD
     * @return a configured strategy
     * @throws com.sunrisedental.util.ValidationException for unknown names
     */
    public BillingStrategy create(String name) {
        return create(BillingType.parse(name));
    }
}
