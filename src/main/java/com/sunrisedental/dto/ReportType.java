package com.sunrisedental.dto;

import java.util.Arrays;
import java.util.Optional;

/**
 * The available reports and the stored procedure behind each. Procedure names come only from this fixed
 * list, never from user input, so building the {@code CALL} text is safe.
 */
public enum ReportType {
    DAILY_APPOINTMENTS("daily-appointments", "Daily appointments per dentist", "sp_report_daily_by_dentist", 2),
    MONTHLY_REVENUE("monthly-revenue", "Monthly revenue by treatment", "sp_report_monthly_revenue", 2),
    TOP_TREATMENTS("top-treatments", "Top treatments", "sp_report_top_treatments", 3),
    DENTIST_UTILIZATION("dentist-utilization", "Dentist utilization", "sp_report_dentist_utilization", 3),
    CANCELLATION_RATE("cancellation-rate", "Cancellation rate", "sp_report_cancellation_rate", 2);

    private final String slug;
    private final String title;
    private final String procedure;
    private final int parameterCount;

    ReportType(String slug, String title, String procedure, int parameterCount) {
        this.slug = slug;
        this.title = title;
        this.procedure = procedure;
        this.parameterCount = parameterCount;
    }

    /** @return the URL segment, e.g. {@code top-treatments} */
    public String slug() {
        return slug;
    }

    /** @return the human-readable title */
    public String title() {
        return title;
    }

    /** @return the stored procedure name */
    public String procedure() {
        return procedure;
    }

    /** @return the number of procedure parameters */
    public int parameterCount() {
        return parameterCount;
    }

    /**
     * @param slug URL segment
     * @return the matching report, if any
     */
    public static Optional<ReportType> fromSlug(String slug) {
        return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst();
    }
}
