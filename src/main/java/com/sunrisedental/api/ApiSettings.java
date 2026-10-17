package com.sunrisedental.api;

import com.sunrisedental.dto.ClinicProfile;
import com.sunrisedental.util.AppConfig;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

/**
 * Settings of the REST layer. The clinic profile and the receipt formats come from {@code config.properties};
 * the receipts folder can be changed with the optional environment variable {@code SUNRISE_RECEIPTS_DIR}
 * (default {@code receipts}).
 *
 * @param receiptsDirectory folder where receipt files are written
 * @param receiptFormats    which receipt files are written (may be empty)
 * @param clinic            name, address, phone and currency printed on receipts
 */
public record ApiSettings(Path receiptsDirectory, Set<ReceiptStore.Format> receiptFormats, ClinicProfile clinic) {

    /** Makes the format set unmodifiable. */
    public ApiSettings {
        receiptFormats = Set.copyOf(receiptFormats);
    }

    /** @return defaults used by tests and tools */
    public static ApiSettings defaults() {
        return new ApiSettings(Path.of("receipts"), EnumSet.allOf(ReceiptStore.Format.class),
                ClinicProfile.defaults());
    }

    /**
     * Builds the settings.
     *
     * @param config the loaded {@code config.properties}
     * @return the settings
     * @throws IllegalStateException if a receipt format is not txt or html
     */
    public static ApiSettings fromEnvironment(AppConfig config) {
        Set<ReceiptStore.Format> formats = EnumSet.noneOf(ReceiptStore.Format.class);
        for (String name : config.receiptFormats()) {
            formats.add(ReceiptStore.Format.parse(name).orElseThrow(
                    () -> new IllegalStateException("receipts.formats may only contain txt and html: " + name)));
        }
        String dir = System.getenv("SUNRISE_RECEIPTS_DIR");
        return new ApiSettings(
                Path.of(dir == null || dir.isBlank() ? "receipts" : dir.trim()),
                formats,
                new ClinicProfile(config.clinicName(), config.clinicAddress(), config.clinicPhone(),
                        config.currency()));
    }
}
