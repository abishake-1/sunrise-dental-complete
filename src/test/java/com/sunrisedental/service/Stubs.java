package com.sunrisedental.service;

import com.sunrisedental.dao.ConnectionProvider;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Tiny stand-ins built with {@link Proxy}: stub any interface method by name, everything else fails loudly. */
final class Stubs {

    private Stubs() {
    }

    static <T> Builder<T> of(Class<T> type) {
        return new Builder<>(type);
    }

    static final class Builder<T> {
        private final Class<T> type;
        private final Map<String, Function<Object[], Object>> handlers = new HashMap<>();

        private Builder(Class<T> type) {
            this.type = type;
        }

        Builder<T> on(String method, Function<Object[], Object> handler) {
            handlers.put(method, handler);
            return this;
        }

        T make() {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> type.getSimpleName() + " stub";
                    };
                }
                Function<Object[], Object> handler = handlers.get(method.getName());
                if (handler == null) {
                    throw new UnsupportedOperationException("Not stubbed: " + method.getName());
                }
                return handler.apply(args == null ? new Object[0] : args);
            }));
        }
    }

    /** A connection provider whose connections accept every call and do nothing. */
    static ConnectionProvider fakeConnections() {
        return () -> (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) {
                        return false;
                    }
                    if (r == int.class) {
                        return 0;
                    }
                    if (r == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
