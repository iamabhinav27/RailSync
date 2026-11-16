package com.railsync.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * A physical seat/berth for one train on one journey date. Seat rows are created per
 * (train, journey date) in the database; the {@code booked} flag mirrors the stored status.
 */
public class Seat implements Serializable {
    private static final long serialVersionUID = 2L;

    private long seatId;                 // database id (0 for a template seat)
    private final int seatNumber;
    private final String coachId;
    private final SeatClass seatClass;
    private final BerthType berthType;
    private boolean booked;

    public Seat(int seatNumber, String coachId, SeatClass seatClass, BerthType berthType) {
        this.seatNumber = seatNumber;
        this.coachId = coachId;
        this.seatClass = seatClass;
        this.berthType = berthType;
    }

    public long getSeatId() { return seatId; }
    public void setSeatId(long seatId) { this.seatId = seatId; }
    public int getSeatNumber() { return seatNumber; }
    public String getCoachId() { return coachId; }
    public SeatClass getSeatClass() { return seatClass; }
    public BerthType getBerthType() { return berthType; }
    public boolean isBooked() { return booked; }
    public void setBooked(boolean booked) { this.booked = booked; }

    public String getFormattedSeatCode() {
        return coachId + "-" + seatNumber + " [" + berthType + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seat seat = (Seat) o;
        return seatNumber == seat.seatNumber && Objects.equals(coachId, seat.coachId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coachId, seatNumber);
    }

    @Override
    public String toString() {
        return getFormattedSeatCode() + (booked ? " (Booked)" : " (Available)");
    }
}
