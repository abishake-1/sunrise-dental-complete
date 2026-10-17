package com.sunrisedental.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Small router: matches the request path against patterns such as {@code /api/appointments/{id}},
 * dispatches on the HTTP method, enforces the roles allowed on a route and renders every outcome
 * (success or error) through {@link HttpResponder}. 404 is returned for unknown paths and 405 (with an
 * {@code Allow} header) when the path exists but not for that method.
 */
public final class Router implements HttpHandler {

    private final List<Route> routes = new ArrayList<>();

    /**
     * Registers a route.
     *
     * @param method  HTTP method, e.g. GET
     * @param pattern path pattern starting with '/', with {@code {name}} variables
     * @param handler the handler
     * @param roles   roles allowed to call it; none means the route is not role-restricted
     *                (the AuthFilter still decides whether authentication is needed)
     * @return this router
     */
    public Router add(String method, String pattern, RouteHandler handler, Role... roles) {
        if (!pattern.startsWith("/")) {
            throw new IllegalArgumentException("Pattern must start with '/': " + pattern);
        }
        Set<Role> allowed = roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(List.of(roles));
        routes.add(new Route(method, pattern.substring(1).split("/"), allowed, handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            HttpResponder.send(exchange, dispatch(exchange));
        } catch (Exception e) {
            HttpResponder.sendError(exchange, e);
        } finally {
            exchange.close();
        }
    }

    private ApiResponse dispatch(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String[] segments = splitPath(exchange.getRequestURI().getRawPath());
        Set<String> allowedMethods = new TreeSet<>();
        for (Route route : routes) {
            Map<String, String> params = route.match(segments);
            if (params == null) {
                continue;
            }
            if (!route.method().equals(method)) {
                allowedMethods.add(route.method());
                continue;
            }
            authorize(route, exchange);
            return route.handler().handle(new ApiRequest(exchange, params));
        }
        if (!allowedMethods.isEmpty()) {
            throw ApiException.methodNotAllowed(allowedMethods);
        }
        throw ApiException.notFound("No such endpoint");
    }

    private static void authorize(Route route, HttpExchange exchange) {
        if (route.roles().isEmpty()) {
            return;
        }
        Object attribute = exchange.getAttribute(ApiRequest.USER_ATTRIBUTE);
        if (!(attribute instanceof User user)) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (!route.roles().contains(user.getRole())) {
            throw ApiException.forbidden("Your role does not allow this operation");
        }
    }

    private static String[] splitPath(String rawPath) {
        String path = rawPath;
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return new String[0];
        }
        String[] raw = path.split("/", -1);
        String[] decoded = new String[raw.length];
        try {
            for (int i = 0; i < raw.length; i++) {
                // '+' is a literal plus inside a path (only query strings treat it as a space)
                decoded[i] = URLDecoder.decode(raw[i].replace("+", "%2B"), StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_PATH", "The path is not correctly URL-encoded");
        }
        return decoded;
    }

    private record Route(String method, String[] pattern, Set<Role> roles, RouteHandler handler) {

        Map<String, String> match(String[] segments) {
            if (segments.length != pattern.length) {
                return null;
            }
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < pattern.length; i++) {
                String expected = pattern[i];
                if (expected.startsWith("{") && expected.endsWith("}")) {
                    if (segments[i].isEmpty()) {
                        return null;
                    }
                    params.put(expected.substring(1, expected.length() - 1), segments[i]);
                } else if (!expected.equals(segments[i])) {
                    return null;
                }
            }
            return params;
        }
    }
}
