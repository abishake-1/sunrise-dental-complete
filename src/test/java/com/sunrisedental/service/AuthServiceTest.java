package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dao.UserDao;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static User user(String name, String password, boolean active) {
        User u = new User();
        u.setId(1L);
        u.setUsername(name);
        u.setPassword(password);
        u.setFullName(name);
        u.setRole(Role.ADMIN);
        u.setActive(active);
        return u;
    }

    private static AuthService service(User... users) {
        UserDao dao = Stubs.of(UserDao.class).on("findByUsername", args -> {
            for (User u : users) {
                if (u.getUsername().equals(args[1])) {
                    return Optional.of(u);
                }
            }
            return Optional.empty();
        }).make();
        return new AuthService(new TransactionManager(Stubs.fakeConnections()), dao);
    }

    @Test
    void plainTextPasswordMatches() {
        AuthService auth = service(user("admin", "admin123", true));
        assertTrue(auth.authenticate("admin", "admin123").isPresent());
        assertEquals("admin", auth.authenticate(" admin ", "admin123").orElseThrow().getUsername());
    }

    @Test
    void wrongPasswordUnknownUserAndBlankInputAreRejected() {
        AuthService auth = service(user("admin", "admin123", true));
        assertFalse(auth.authenticate("admin", "Admin123").isPresent());   // case-sensitive
        assertFalse(auth.authenticate("admin", "admin1234").isPresent());
        assertFalse(auth.authenticate("ghost", "admin123").isPresent());
        assertFalse(auth.authenticate("", "admin123").isPresent());
        assertFalse(auth.authenticate("admin", "").isPresent());
        assertFalse(auth.authenticate(null, null).isPresent());
        assertFalse(auth.authenticate("admin", "x".repeat(200)).isPresent());
    }

    @Test
    void inactiveAccountsCannotLogIn() {
        assertFalse(service(user("old", "secret1", false)).authenticate("old", "secret1").isPresent());
    }
}
