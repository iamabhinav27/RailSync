package com.railsync.service;

import com.railsync.exception.AccessDeniedException;
import com.railsync.exception.DatabaseException;
import com.railsync.exception.InvalidLoginException;
import com.railsync.model.PassengerAccount;
import com.railsync.model.User;
import com.railsync.repository.UserRepository;
import com.railsync.util.PasswordHasher;
import com.railsync.util.ValidationUtils;

/**
 * Registration, login and role checks. Passwords are stored only as salted PBKDF2 hashes and all
 * queries go through prepared statements in {@link UserRepository}.
 */
public class AuthenticationService {

    /** Used so a wrong username costs the same time as a wrong password. */
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password-1");

    private final UserRepository users;

    public AuthenticationService(UserRepository users) {
        this.users = users;
    }

    /** Creates a PASSENGER account. */
    public PassengerAccount register(String username, String fullName, String email, String phone, String password)
            throws InvalidLoginException {
        ValidationUtils.validateRegistration(username, fullName, email, phone, password);
        String name = username.trim().toLowerCase();
        if (users.existsByUsername(name)) {
            throw new InvalidLoginException("Username '" + name + "' is already taken.");
        }
        PassengerAccount account = new PassengerAccount("0", name, PasswordHasher.hash(password),
                fullName.trim(), email.trim(), phone == null ? "" : phone.trim());
        try {
            users.save(account);
        } catch (DatabaseException e) {
            throw new InvalidLoginException("Could not create the account (username may already exist).");
        }
        return (PassengerAccount) users.findById(name);
    }

    /** Verifies credentials and returns the user (an {@code Admin} or {@code PassengerAccount}). */
    public User login(String username, String password) throws InvalidLoginException {
        ValidationUtils.validateLoginInput(username, password);
        User user = users.findById(username.trim());
        String stored = user != null ? user.getPasswordHash() : DUMMY_HASH;
        boolean ok = PasswordHasher.verify(password, stored);
        if (user == null || !ok) {
            throw new InvalidLoginException("Invalid username or password.");
        }
        return user;
    }

    public void requirePermission(User actor, User.Permission permission) throws AccessDeniedException {
        if (actor == null || !actor.hasPermission(permission)) {
            throw new AccessDeniedException("Access denied: "
                    + (actor == null ? "not logged in" : actor.getRole() + " accounts cannot perform " + permission)
                    + ".");
        }
    }

    public void requireAdmin(User actor) throws AccessDeniedException {
        if (actor == null || !actor.isAdmin()) {
            throw new AccessDeniedException("Access denied: administrator privileges are required.");
        }
    }
}
