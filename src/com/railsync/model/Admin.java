package com.railsync.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** The ADMIN role: manages trains and can see every booking, passenger, waiting list and report. */
public class Admin extends User {
    private static final long serialVersionUID = 1L;

    private static final Set<Permission> PERMISSIONS =
            Collections.unmodifiableSet(EnumSet.allOf(Permission.class));

    public Admin(String userId, String username, String passwordHash,
                 String fullName, String email, String phone) {
        super(userId, username, passwordHash, fullName, email, phone, Role.ADMIN);
    }

    @Override
    public Set<Permission> getPermissions() {
        return PERMISSIONS;
    }

    @Override
    public String getDashboardTitle() {
        return "Administrator Console";
    }
}
