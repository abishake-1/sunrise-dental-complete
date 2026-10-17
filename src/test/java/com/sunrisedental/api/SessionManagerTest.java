package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    private static User user() {
        User u = new User();
        u.setId(7L);
        u.setUsername("admin");
        u.setPassword("admin123");
        u.setFullName("Clinic Administrator");
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    void sessionLivesUntilLogoutAndNeverCarriesThePassword() {
        SessionManager sessions = new SessionManager();
        Session session = sessions.create(user());
        assertEquals(1, sessions.size());
        assertNull(session.user().getPassword());
        assertEquals("admin", session.user().getUsername());
        assertTrue(sessions.find(session.token()).isPresent());
        sessions.invalidate(session.token());
        assertFalse(sessions.find(session.token()).isPresent());
        assertEquals(0, sessions.size());
    }

    @Test
    void tokensAreRandomAndUnknownOnesAreRejected() {
        SessionManager sessions = new SessionManager();
        assertNotEquals(sessions.create(user()).token(), sessions.create(user()).token());
        assertFalse(sessions.find(null).isPresent());
        assertFalse(sessions.find("not-a-token").isPresent());
        sessions.invalidate(null);
    }
}
