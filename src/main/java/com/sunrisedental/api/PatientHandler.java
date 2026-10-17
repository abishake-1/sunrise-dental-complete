package com.sunrisedental.api;

import com.sunrisedental.model.Patient;
import com.sunrisedental.model.Role;
import com.sunrisedental.service.ClinicFacade;

/** Patient endpoints needed to book appointments: register, read and search. */
public final class PatientHandler {

    private final ClinicFacade facade;
    private final RequestValidators validators;

    /**
     * Creates the handler.
     *
     * @param facade     the application facade
     * @param validators request validators
     */
    public PatientHandler(ClinicFacade facade, RequestValidators validators) {
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
        router.add("POST", "/api/patients", this::register, staff);
        router.add("GET", "/api/patients/{id}", request ->
                ApiResponse.ok(ApiMapper.patient(facade.getPatient(request.pathId("id")))), staff);
        router.add("GET", "/api/patients", request -> ApiResponse.ok(ApiMapper.list(
                facade.searchPatients(validators.searchTerm(request.query())).stream()
                        .map(ApiMapper::patient).toList())), staff);
    }

    private ApiResponse register(ApiRequest request) {
        Patient patient = validators.patient(request.jsonObject());
        Patient saved = facade.registerPatient(patient);
        return ApiResponse.created(ApiMapper.patient(saved), "/api/patients/" + saved.getId());
    }
}
