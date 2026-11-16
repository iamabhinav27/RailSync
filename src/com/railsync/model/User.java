package com.railsync.model;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for an authenticated account. Role-specific behaviour lives in the subclasses
 * {@link PassengerAccount} and {@link Admin}, which override {@link #getPermissions()}.
 *
 * <p>Only the salted password <em>hash</em> is held here; plaintext passwords are never stored.</p>
 */
public abstract class User implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Role {
        PASSENGER,
        ADMIN
    }

    /** Operations that can be guarded by role. */
    public enum Permission {
        SEARCH_TRAINS, BOOK_TICKET, VIEW_OWN_BOOKINGS, CANCEL_OWN_BOOKING,
        MANAGE_TRAINS, VIEW_ALL_BOOKINGS, VIEW_PASSENGERS, VIEW_WAITING_LISTS, GENERATE_REPORTS
    }

    private final String userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final String email;
    private final String phone;
    private final Role role;

    protected User(String userId, String username, String passwordHash, String fullName,
                   String email, String phone, Role role) {
        this.userId = Objects.requireNonNull(userId);
        this.username = Objects.requireNonNull(username).toLowerCase().trim();
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = fullName != null && !fullName.isBlank() ? fullName.trim() : username;
        this.email = email != null ? email.trim() : "";
        this.phone = phone != null ? phone.trim() : "";
        this.role = Objects.requireNonNull(role);
    }

    /** Permissions granted to this kind of user (overridden by each role). */
    public abstract Set<Permission> getPermissions();

    /** Title shown on the role's home screen (overridden by each role). */
    public abstract String getDashboardTitle();

    public boolean hasPermission(Permission permission) {
        return getPermissions().contains(permission);
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }

    /** Creates the right subclass for a stored role. */
    public static User create(String userId, String username, String passwordHash, String fullName,
                              String email, String phone, Role role) {
        return role == Role.ADMIN
                ? new Admin(userId, username, passwordHash, fullName, email, phone)
                : new PassengerAccount(userId, username, passwordHash, fullName, email, phone);
    }

    protected static Set<Permission> setOf(Permission first, Permission... rest) {
        return EnumSet.of(first, rest);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(username, ((User) o).username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return fullName + " (" + role + " - " + username + ")";
    }
}
