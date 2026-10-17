package com.sunrisedental.api;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sunrisedental.model.User;
import java.io.IOException;
import java.util.logging.Logger;

/** Logs one line per request (method, path without query, status, duration, user). Bodies are never logged. */
public final class AccessLogFilter extends Filter {

    private static final Logger LOG = Logger.getLogger("sunrise.access");

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(exchange);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            Object user = exchange.getAttribute(ApiRequest.USER_ATTRIBUTE);
            String who = user instanceof User u ? u.getUsername() : "-";
            LOG.info(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath()
                    + " -> " + exchange.getResponseCode() + " (" + millis + " ms) user=" + who);
        }
    }

    @Override
    public String description() {
        return "Access log";
    }
}
