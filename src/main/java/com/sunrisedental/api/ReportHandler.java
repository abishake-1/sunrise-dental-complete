package com.sunrisedental.api;

import com.sunrisedental.dto.ReportQuery;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.dto.ReportType;
import com.sunrisedental.dto.ResponseFormat;
import com.sunrisedental.model.Role;
import com.sunrisedental.service.ClinicFacade;
import com.sunrisedental.util.CsvWriter;
import java.util.stream.Collectors;

/**
 * Report endpoints, ADMIN only. Every report is available as JSON (default) or as a CSV download
 * ({@code ?format=csv}, UTF-8 with a byte-order mark so Excel opens it correctly).
 */
public final class ReportHandler {

    private final ClinicFacade facade;
    private final RequestValidators validators;

    /**
     * Creates the handler.
     *
     * @param facade     the application facade
     * @param validators request validators
     */
    public ReportHandler(ClinicFacade facade, RequestValidators validators) {
        this.facade = facade;
        this.validators = validators;
    }

    /**
     * Registers {@code GET /api/reports/<slug>} for every report.
     *
     * @param router the router
     */
    public void register(Router router) {
        for (ReportType type : ReportType.values()) {
            router.add("GET", "/api/reports/" + type.slug(), request -> handle(type, request), Role.ADMIN);
        }
    }

    private ApiResponse handle(ReportType type, ApiRequest request) {
        ReportQuery q = validators.reportQuery(type, request.query());
        ReportTable table = switch (type) {
            case DAILY_APPOINTMENTS -> facade.dailyAppointmentsPerDentist(q.from(), q.to());
            case MONTHLY_REVENUE -> facade.monthlyRevenueByTreatment(q.year(), q.month());
            case TOP_TREATMENTS -> facade.topTreatments(q.from(), q.to(), q.limit());
            case DENTIST_UTILIZATION -> facade.dentistUtilization(q.from(), q.to());
            case CANCELLATION_RATE -> facade.cancellationRate(q.from(), q.to());
        };
        if (q.format() == ResponseFormat.CSV) {
            String csv = "\uFEFF" + CsvWriter.toCsv(table.columns(), table.rows());
            return ApiResponse.text(200, "text/csv; charset=utf-8", csv)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName(table) + "\"");
        }
        return ApiResponse.ok(ApiMapper.reportTable(table));
    }

    /** Safe download name such as {@code top-treatments_2026-10-01_2026-10-12_5.csv}. */
    static String fileName(ReportTable table) {
        String parts = table.parameters().values().stream().map(String::valueOf).collect(Collectors.joining("_"));
        String name = table.report() + (parts.isEmpty() ? "" : "_" + parts);
        return name.replaceAll("[^A-Za-z0-9._-]", "_") + ".csv";
    }
}
