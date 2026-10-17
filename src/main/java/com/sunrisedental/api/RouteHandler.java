package com.sunrisedental.api;

/** Handles one route. Exceptions are translated to error responses by the {@link Router}. */
@FunctionalInterface
public interface RouteHandler {

    /**
     * Handles a request.
     *
     * @param request the parsed request
     * @return the response
     * @throws Exception any failure; mapped to an HTTP error
     */
    ApiResponse handle(ApiRequest request) throws Exception;
}
