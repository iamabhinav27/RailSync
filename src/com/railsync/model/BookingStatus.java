package com.railsync.model;

import java.io.Serializable;

/**
 * Statuses used for tickets and bookings in the reservation lifecycle.
 * The database stores {@link #name()} (CONFIRMED, RAC, WAITING, CANCELLED).
 */
public enum BookingStatus implements Serializable {
    CONFIRMED("Confirmed (CNF)"),
    RAC("Reservation Against Cancellation (RAC)"),
    WAITING("Waiting List (WL)"),
    CANCELLED("Cancelled (CAN)");

    private final String displayStatus;

    BookingStatus(String displayStatus) {
        this.displayStatus = displayStatus;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    /** Parses the value stored in the database. */
    public static BookingStatus fromDb(String value) {
        return BookingStatus.valueOf(value.trim().toUpperCase());
    }

    @Override
    public String toString() {
        return displayStatus;
    }
}
