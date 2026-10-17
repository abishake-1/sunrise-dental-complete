package com.sunrisedental.api;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Protects every {@code /api} route except login. The client sends {@code Authorization: Bearer <token>}; the
 * token is looked up in the {@link SessionManager}. Role checks happen later in the {@link Router}.
 */
public final class AuthFilter extends Filter {

    private static final Set<String> PUBLIC_ENDPOINTS = Set.of("POST /api/auth/login");
    private static final String BEARER_PREFIX = "bearer ";

    private final SessionManager sessions;

    /**
     * Creates the filter.
     *
     * @param sessions where sessions are looked up
     */
    public AuthFilter(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (PUBLIC_ENDPOINTS.contains(exchange.getRequestMethod() + " " + path)) {
            chain.doFilter(exchange);
            return;
        }
        String token = extractToken(exchange);
        Optional<Session> session = sessions.find(token);
        if (session.isEmpty()) {
            HttpResponder.sendError(exchange, ApiException.unauthorized(
                    token == null ? "Please log in" : "Your session has ended, please log in again"));
            return;
        }
        exchange.setAttribute(ApiRequest.USER_ATTRIBUTE, session.get().user());
        exchange.setAttribute(ApiRequest.TOKEN_ATTRIBUTE, token);
        chain.doFilter(exchange);
    }

    private static String extractToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    public String description() {
        return "Authentication";
    }
}
