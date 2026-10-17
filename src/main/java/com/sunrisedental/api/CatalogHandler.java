package com.sunrisedental.api;

import com.sunrisedental.model.Role;
import com.sunrisedental.service.ClinicFacade;

/** Read-only reference data: dentists and treatments. */
public final class CatalogHandler {

    private final ClinicFacade facade;

    /**
     * Creates the handler.
     *
     * @param facade the application facade
     */
    public CatalogHandler(ClinicFacade facade) {
        this.facade = facade;
    }

    /**
     * Registers the routes.
     *
     * @param router the router
     */
    public void register(Router router) {
        router.add("GET", "/api/dentists", request -> ApiResponse.ok(
                ApiMapper.list(facade.listDentists().stream().map(ApiMapper::dentist).toList())), Role.values());
        router.add("GET", "/api/treatments", request -> ApiResponse.ok(
                ApiMapper.list(facade.listTreatments().stream().map(ApiMapper::treatment).toList())), Role.values());
    }
}
