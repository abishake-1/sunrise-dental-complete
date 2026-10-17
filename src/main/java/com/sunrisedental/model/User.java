package com.sunrisedental.model;

/** A clinic staff member who can log in ({@code users} table). */
public final class User extends BaseEntity {

    private String username;
    private String password;
    private String fullName;
    private String email;
    private Role role;
    private boolean active = true;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** The password is intentionally left out. */
    @Override
    public String toString() {
        return "User{id=" + getId() + ", username='" + username + "', role=" + role + '}';
    }
}
