package com.sunrisedental.api;

import com.sunrisedental.dto.ClinicProfile;
import com.sunrisedental.dto.Receipt;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a {@link Receipt} as JSON, as fixed-width plain text for a receipt printer (42 columns, ASCII
 * layout) and as a self-contained, print-friendly HTML page (every dynamic value is HTML-escaped).
 */
public final class ReceiptRenderer {

    private static final int WIDTH = 42;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ReceiptRenderer() {
    }

    /**
     * Builds the JSON representation. Money values are exact decimals with two digits.
     *
     * @param r      the receipt
     * @param clinic the clinic details
     * @return an ordered map ready for {@link Json#write}
     */
    public static Map<String, Object> json(Receipt r, ClinicProfile clinic) {
        List<Object> items = new ArrayList<>();
        items.add(Json.object("description", r.treatmentName() + " (" + r.treatmentCode() + ")",
                "amount", r.treatmentPrice()));
        if (r.consultationFee().signum() > 0) {
            items.add(Json.object("description", "Consultation fee", "amount", r.consultationFee()));
        }
        return Json.object(
                "receiptNumber", r.receiptNumber(),
                "billId", r.billId(),
                "issuedAt", r.issuedAt(),
                "issuedBy", r.issuedBy(),
                "clinic", Json.object("name", clinic.name(), "address", clinic.address(), "phone", clinic.phone(),
                        "currency", clinic.currency()),
                "appointment", Json.object("number", r.appointmentNumber(), "date", r.appointmentDate(),
                        "time", r.appointmentTime()),
                "patient", Json.object("name", r.patientName(), "phone", r.patientPhone()),
                "dentist", Json.object("name", r.dentistName()),
                "billingType", r.billingType(),
                "lineItems", items,
                "subtotal", r.subtotal(),
                "discount", Json.object("percent", r.discountPercent(), "amount", r.discountAmount()),
                "tax", Json.object("percent", r.taxPercent(), "amount", r.taxAmount()),
                "total", r.total(),
                "payment", Json.object("status", r.paymentStatus(), "method", r.paymentMethod(),
                        "paidAt", r.paidAt()));
    }

    /**
     * Builds the plain-text receipt (lines end with LF).
     *
     * @param r      the receipt
     * @param clinic the clinic details
     * @return the text
     */
    public static String text(Receipt r, ClinicProfile clinic) {
        String rule = "=".repeat(WIDTH);
        String thin = "-".repeat(WIDTH);
        StringBuilder out = new StringBuilder();
        out.append(rule).append('\n').append(center(clinic.name())).append('\n');
        if (!clinic.address().isBlank()) {
            out.append(center(clinic.address())).append('\n');
        }
        if (!clinic.phone().isBlank()) {
            out.append(center("Tel: " + clinic.phone())).append('\n');
        }
        out.append(rule).append('\n');
        line(out, "Receipt", r.receiptNumber());
        line(out, "Issued", r.issuedAt().format(STAMP));
        line(out, "Appointment", r.appointmentNumber());
        line(out, "Date", r.appointmentDate() + " " + r.appointmentTime());
        line(out, "Patient", r.patientName());
        line(out, "Dentist", r.dentistName());
        out.append(thin).append('\n');
        line(out, r.treatmentName() + " (" + r.treatmentCode() + ")", money(r.treatmentPrice()));
        if (r.consultationFee().signum() > 0) {
            line(out, "Consultation fee", money(r.consultationFee()));
        }
        out.append(thin).append('\n');
        line(out, "Subtotal", money(r.subtotal()));
        line(out, "Discount (" + r.discountPercent().toPlainString() + "%)", "-" + money(r.discountAmount()));
        line(out, "Tax (" + r.taxPercent().toPlainString() + "%)", money(r.taxAmount()));
        out.append(rule).append('\n');
        line(out, "TOTAL " + clinic.currency(), money(r.total()));
        out.append(rule).append('\n');
        line(out, "Billing", r.billingType());
        line(out, "Status", r.paymentStatus());
        if (r.paymentMethod() != null) {
            line(out, "Paid with", r.paymentMethod());
        }
        if (r.paidAt() != null) {
            line(out, "Paid at", r.paidAt().format(STAMP));
        }
        line(out, "Issued by", r.issuedBy());
        out.append('\n').append(center("Thank you for choosing us!")).append('\n');
        return out.toString();
    }

    /**
     * Builds the HTML receipt (no scripts, inline CSS only).
     *
     * @param r      the receipt
     * @param clinic the clinic details
     * @return a complete HTML document
     */
    public static String html(Receipt r, ClinicProfile clinic) {
        StringBuilder out = new StringBuilder(2048);
        out.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Receipt ").append(esc(r.receiptNumber())).append("</title><style>")
                .append("body{font-family:Arial,Helvetica,sans-serif;max-width:420px;margin:24px auto;color:#111}")
                .append("h1{font-size:20px;margin:0;text-align:center}.c{text-align:center;color:#444;margin:2px 0}")
                .append("table{width:100%;border-collapse:collapse;margin:12px 0}td{padding:3px 0;vertical-align:top}")
                .append("td.r{text-align:right;white-space:nowrap}tr.t td{border-top:2px solid #111;font-weight:bold;")
                .append("padding-top:6px}tr.s td{border-top:1px solid #999}.st{margin-top:10px;font-size:13px}")
                .append("@media print{body{margin:0}}</style></head><body>");
        out.append("<h1>").append(esc(clinic.name())).append("</h1>");
        if (!clinic.address().isBlank()) {
            out.append("<p class=\"c\">").append(esc(clinic.address())).append("</p>");
        }
        if (!clinic.phone().isBlank()) {
            out.append("<p class=\"c\">Tel: ").append(esc(clinic.phone())).append("</p>");
        }
        out.append("<table>");
        row(out, "Receipt", r.receiptNumber());
        row(out, "Issued", r.issuedAt().format(STAMP));
        row(out, "Appointment", r.appointmentNumber() + " on " + r.appointmentDate() + " " + r.appointmentTime());
        row(out, "Patient", r.patientName());
        row(out, "Dentist", r.dentistName());
        out.append("</table><table>");
        out.append("<tr class=\"s\"><td>").append(esc(r.treatmentName() + " (" + r.treatmentCode() + ")"))
                .append("</td><td class=\"r\">").append(money(r.treatmentPrice())).append("</td></tr>");
        if (r.consultationFee().signum() > 0) {
            row(out, "Consultation fee", money(r.consultationFee()));
        }
        out.append("<tr class=\"s\"><td>Subtotal</td><td class=\"r\">").append(money(r.subtotal())).append("</td></tr>");
        row(out, "Discount (" + r.discountPercent().toPlainString() + "%)", "-" + money(r.discountAmount()));
        row(out, "Tax (" + r.taxPercent().toPlainString() + "%)", money(r.taxAmount()));
        out.append("<tr class=\"t\"><td>TOTAL ").append(esc(clinic.currency())).append("</td><td class=\"r\">")
                .append(money(r.total())).append("</td></tr></table>");
        out.append("<div class=\"st\">Billing: ").append(esc(r.billingType())).append(" &middot; Status: ")
                .append(esc(r.paymentStatus()));
        if (r.paymentMethod() != null) {
            out.append(" (").append(esc(r.paymentMethod())).append(")");
        }
        out.append("<br>Issued by: ").append(esc(r.issuedBy())).append("</div>");
        out.append("<p class=\"c\">Thank you for choosing us!</p></body></html>\n");
        return out.toString();
    }

    /**
     * Formats an amount with thousands separators and two decimals, e.g. {@code 1,234.50}.
     *
     * @param value the amount
     * @return the text
     */
    static String money(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
    }

    /**
     * Escapes text for safe use inside HTML.
     *
     * @param value raw text
     * @return the escaped text
     */
    static String escapeHtml(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String esc(String value) {
        return escapeHtml(value);
    }

    private static void row(StringBuilder out, String label, String value) {
        out.append("<tr><td>").append(esc(label)).append("</td><td class=\"r\">").append(esc(value)).append("</td></tr>");
    }

    /** Writes "label ..... value" on one line; the longer side is shortened with "..." when it does not fit. */
    private static void line(StringBuilder out, String left, String right) {
        String label = left;
        String value = right;
        if (label.length() + 1 + value.length() > WIDTH) {
            if (value.length() > label.length()) {
                value = ellipsize(value, Math.max(4, WIDTH - label.length() - 1));
                label = ellipsize(label, WIDTH - value.length() - 1);
            } else {
                label = ellipsize(label, WIDTH - value.length() - 1);
            }
        }
        out.append(label).append(" ".repeat(Math.max(1, WIDTH - label.length() - value.length())))
                .append(value).append('\n');
    }

    private static String ellipsize(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return max <= 3 ? text.substring(0, Math.max(0, max)) : text.substring(0, max - 3) + "...";
    }

    private static String center(String text) {
        String shown = text.length() > WIDTH ? text.substring(0, WIDTH) : text;
        return " ".repeat((WIDTH - shown.length()) / 2) + shown;
    }
}
