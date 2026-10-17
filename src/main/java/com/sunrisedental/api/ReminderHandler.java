package com.sunrisedental.api;

import com.sunrisedental.dto.ReminderResult;
import com.sunrisedental.model.Role;
import com.sunrisedental.service.ClinicFacade;
import java.time.LocalDate;

/** Lets an administrator trigger the reminder run by hand (the scheduler runs it daily by itself). */
public final class ReminderHandler {

    private final ClinicFacade facade;
    private final RequestValidators validators;

    /**
     * Creates the handler.
     *
     * @param facade     the application facade
     * @param validators request validators
     */
    public ReminderHandler(ClinicFacade facade, RequestValidators validators) {
        this.facade = facade;
        this.validators = validators;
    }

    /**
     * Registers {@code POST /api/admin/reminders/run} (ADMIN only).
     *
     * @param router the router
     */
    public void register(Router router) {
        router.add("POST", "/api/admin/reminders/run", this::run, Role.ADMIN);
    }

    private ApiResponse run(ApiRequest request) {
        LocalDate date = validators.reminderDate(request.optionalJsonObject());
        ReminderResult result = facade.sendReminders(date);
        return ApiResponse.ok(Json.object("date", result.date(), "candidates", result.candidates(),
                "sent", result.sent(), "failed", result.failed()));
    }
}
