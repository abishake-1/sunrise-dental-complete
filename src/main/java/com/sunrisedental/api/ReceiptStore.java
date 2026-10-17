package com.sunrisedental.api;

import com.sunrisedental.dto.ClinicProfile;
import com.sunrisedental.dto.Receipt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Writes receipts to a folder as {@code RCP-000001.txt} and/or {@code .html}. File names are built only from the
 * receipt number (checked against a strict pattern), never from user text, so a path cannot escape the folder.
 * Files are written to a temporary file first and moved into place, so a reader never sees a half-written receipt.
 * Saving again (for example after payment) replaces the earlier file.
 */
public final class ReceiptStore {

    /** Receipt file formats. */
    public enum Format {
        TXT(".txt"), HTML(".html");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        /**
         * @param name "txt" or "html" (case-insensitive)
         * @return the matching format, if any
         */
        public static Optional<Format> parse(String name) {
            try {
                return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    private static final Pattern NUMBER = Pattern.compile("^RCP-\\d{6,}$");

    private final Path directory;
    private final Set<Format> formats;
    private final ClinicProfile clinic;

    /**
     * Creates the store.
     *
     * @param directory folder for the receipt files (created when needed)
     * @param formats   which files to write
     * @param clinic    clinic details printed on the receipts
     */
    public ReceiptStore(Path directory, Set<Format> formats, ClinicProfile clinic) {
        this.directory = directory;
        this.formats = Set.copyOf(formats);
        this.clinic = clinic;
    }

    /**
     * Writes the receipt files.
     *
     * @param receipt the receipt
     * @return the file names written (without folder)
     * @throws IOException if a file cannot be written
     */
    public List<String> save(Receipt receipt) throws IOException {
        if (!NUMBER.matcher(receipt.receiptNumber()).matches()) {
            throw new IllegalArgumentException("Unexpected receipt number: " + receipt.receiptNumber());
        }
        List<String> written = new ArrayList<>();
        if (formats.isEmpty()) {
            return written;
        }
        Files.createDirectories(directory);
        for (Format format : Format.values()) {
            if (!formats.contains(format)) {
                continue;
            }
            String content = format == Format.TXT ? ReceiptRenderer.text(receipt, clinic)
                    : ReceiptRenderer.html(receipt, clinic);
            String name = receipt.receiptNumber() + format.extension;
            Path temp = Files.createTempFile(directory, "receipt-", ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8);
                Files.move(temp, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            written.add(name);
        }
        return written;
    }
}
