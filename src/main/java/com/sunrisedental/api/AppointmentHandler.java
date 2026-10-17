package com.sunrisedental.api;

import com.sunrisedental.dto.AppointmentFilter;
import com.sunrisedental.dto.AppointmentUpdateRequest;
import com.sunrisedental.dto.BookAppointmentRequest;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;
import com.sunrisedental.service.ClinicFacade;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Appointment endpoints. Both roles may book, read and update; cancelling is allowed for administrators and,
 * for receptionists, only for appointments they created themselves.
 */
public final class AppointmentHandler {

    private static final Pattern NUMBER = Pattern.compile("^APT-\\d{4,9}$");

    private final ClinicFacade facade;
    private final RequestValidators validators;

    /**
     * Creates the handler.
     *
     * @param facade     the application facade
     * @param validators request validators
     */
    public AppointmentHandler(ClinicFacade facade, RequestValidators validators) {
        this.facade = facade;
        this.validators = validators;
    }

    /**
     * Registers the routes.
     *
     * @param router the router
     */
    public void register(Router router) {
        Role[] staff = Role.values();
        router.add("POST", "/api/appointments", this::book, staff);
        router.add("GET", "/api/appointments", this::list, staff);
        router.add("GET", "/api/appointments/{number}", this::getByNumber, staff);
        router.add("PUT", "/api/appointments/{id}", this::update, staff);
        router.add("DELETE", "/api/appointments/{id}", this::cancel, staff);
    }

    private ApiResponse book(ApiRequest request) {
        BookAppointmentRequest booking = validators.bookAppointment(request.jsonObject());
        Appointment created = facade.bookAppointment(booking, request.requireUser());
        return ApiResponse.created(ApiMapper.appointment(created),
                "/api/appointments/" + created.getAppointmentNumber());
    }

    private ApiResponse list(ApiRequest request) {
        AppointmentFilter filter = validators.appointmentFilter(request.query());
        List<Map<String, Object>> items = facade.appointmentsOn(filter.date(), filter.dentistId()).stream()
                .map(ApiMapper::appointment).toList();
        return ApiResponse.ok(ApiMapper.list(items));
    }

    private ApiResponse getByNumber(ApiRequest request) {
        String number = request.pathParam("number").toUpperCase(Locale.ROOT);
        if (!NUMBER.matcher(number).matches()) {
            throw ApiException.badRequest("INVALID_PATH_PARAMETER",
                    "Appointment number must look like APT-0001");
        }
        return ApiResponse.ok(ApiMapper.appointment(facade.getAppointmentByNumber(number)));
    }

    private ApiResponse update(ApiRequest request) {
        long id = request.pathId("id");
        AppointmentUpdateRequest update = validators.appointmentUpdate(request.jsonObject());
        User actor = request.requireUser();
        Appointment result = update.status() != null
                ? facade.changeAppointmentStatus(id, update.status(), actor)
                : facade.rescheduleAppointment(id, update.date(), update.time(), update.notes(), actor);
        return ApiResponse.ok(ApiMapper.appointment(result));
    }

    private ApiResponse cancel(ApiRequest request) {
        long id = request.pathId("id");
        User actor = request.requireUser();
        if (actor.getRole() != Role.ADMIN) {
            Appointment existing = facade.getAppointment(id);
            if (!actor.getId().equals(existing.getCreatedBy())) {
                throw ApiException.forbidden("Receptionists can only cancel appointments they created");
            }
        }
        return ApiResponse.ok(ApiMapper.appointment(facade.cancelAppointment(id, actor)));
    }
}
