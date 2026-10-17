package com.sunrisedental.api;

import com.sunrisedental.dto.LoginRequest;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Login, logout and "who am I" endpoints. */
public final class AuthHandler {

    private final Authenticator authenticator;
    private final SessionManager sessions;
    private final RequestValidators validators;

    /**
     * Creates the handler.
     *
     * @param authenticator credential check
     * @param sessions      session store
     * @param validators    request validators
     */
    public AuthHandler(Authenticator authenticator, SessionManager sessions, RequestValidators validators) {
        this.authenticator = authenticator;
        this.sessions = sessions;
        this.validators = validators;
    }

    /**
     * Registers the routes.
     *
     * @param router the router
     */
    public void register(Router router) {
        router.add("POST", "/api/auth/login", this::login);
        router.add("POST", "/api/auth/logout", this::logout, Role.values());
        router.add("GET", "/api/auth/me", this::me, Role.values());
    }

    private ApiResponse login(ApiRequest request) {
        LoginRequest login = validators.login(request.jsonObject());
        Optional<User> user = authenticator.authenticate(login.username(), login.password());
        if (user.isEmpty()) {
            // One generic message: do not reveal whether the username exists.
            throw new ApiException(401, "INVALID_CREDENTIALS", "Invalid username or password", List.of(), Map.of());
        }
        Session session = sessions.create(user.get());
        return ApiResponse.ok(Json.object(
                "token", session.token(),
                "tokenType", "Bearer",
                "user", ApiMapper.user(session.user())));
    }

    private ApiResponse logout(ApiRequest request) {
        sessions.invalidate(request.token());
        return ApiResponse.noContent();
    }

    private ApiResponse me(ApiRequest request) {
        return ApiResponse.ok(ApiMapper.user(request.requireUser()));
    }
}
