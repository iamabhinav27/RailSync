package com.railsync.model;

import java.util.Collections;
import java.util.Set;

/**
 * The PASSENGER role: may search, book, view and cancel their own tickets.
 *
 * <p>Note: {@link Passenger} is the <em>traveller</em> on a ticket (name, age, concession, berth
 * preference) and is already used throughout the booking logic, so the login role is a separate
 * subclass of {@link User}.</p>
 */
public class PassengerAccount extends User {
    private static final long serialVersionUID = 1L;

    private static final Set<Permission> PERMISSIONS = Collections.unmodifiableSet(setOf(
            Permission.SEARCH_TRAINS, Permission.BOOK_TICKET,
            Permission.VIEW_OWN_BOOKINGS, Permission.CANCEL_OWN_BOOKING));

    public PassengerAccount(String userId, String username, String passwordHash,
                            String fullName, String email, String phone) {
        super(userId, username, passwordHash, fullName, email, phone, Role.PASSENGER);
    }

    @Override
    public Set<Permission> getPermissions() {
        return PERMISSIONS;
    }

    @Override
    public String getDashboardTitle() {
        return "Passenger Dashboard";
    }
}
