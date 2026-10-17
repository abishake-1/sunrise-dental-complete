package com.sunrisedental.dao;

import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** JDBC implementation of {@link UserDao}. */
public final class UserDaoImpl implements UserDao {

    private static final String SELECT = "SELECT user_id, username, password, full_name, email, role, "
            + "is_active, created_at, updated_at FROM users";

    @Override
    public Optional<User> findByUsername(Connection connection, String username) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE username = ?",
                ps -> ps.setString(1, username), UserDaoImpl::map);
    }

    @Override
    public Optional<User> findById(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE user_id = ?",
                ps -> ps.setLong(1, id), UserDaoImpl::map);
    }

    private static User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setRole(Role.valueOf(rs.getString("role")));
        user.setActive(rs.getBoolean("is_active"));
        user.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        user.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return user;
    }
}
