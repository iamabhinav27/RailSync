package com.railsync.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an individual ticket allocated to a passenger within a booking.
 * Holds seat allocation, queue positions, and booking status.
 */
public class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String ticketId;
    private final String pnr;
    private final Passenger passenger;
    private final SeatClass seatClass;
    private Seat seat;                  // Populated if CONFIRMED
    private BookingStatus status;
    private int racPosition;           // 0 if not RAC, >0 if RAC (e.g. RAC 2)
    private int wlPosition;            // 0 if not WL, >0 if WL (e.g. WL 5)
    private double individualFare;
    private boolean cancelled;
    private long dbId;                  // booking_passengers id (0 until saved)
    private double refundAmount;

    public Ticket(String ticketId, String pnr, Passenger passenger, SeatClass seatClass,
                  BookingStatus status, Seat seat, int racPosition, int wlPosition, double individualFare) {
        this.ticketId = Objects.requireNonNull(ticketId, "Ticket ID cannot be null");
        this.pnr = Objects.requireNonNull(pnr, "PNR cannot be null");
        this.passenger = Objects.requireNonNull(passenger, "Passenger cannot be null");
        this.seatClass = Objects.requireNonNull(seatClass, "SeatClass cannot be null");
        this.status = status;
        this.seat = seat;
        this.racPosition = racPosition;
        this.wlPosition = wlPosition;
        this.individualFare = individualFare;
        this.cancelled = (status == BookingStatus.CANCELLED);
    }

    public long getDbId() {
        return dbId;
    }

    public void setDbId(long dbId) {
        this.dbId = dbId;
    }

    public double getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(double refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getPnr() {
        return pnr;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public SeatClass getSeatClass() {
        return seatClass;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
        if (status == BookingStatus.CANCELLED) {
            this.cancelled = true;
        }
    }

    public int getRacPosition() {
        return racPosition;
    }

    public void setRacPosition(int racPosition) {
        this.racPosition = racPosition;
    }

    public int getWlPosition() {
        return wlPosition;
    }

    public void setWlPosition(int wlPosition) {
        this.wlPosition = wlPosition;
    }

    public double getIndividualFare() {
        return individualFare;
    }

    public void setIndividualFare(double individualFare) {
        this.individualFare = individualFare;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        if (cancelled) {
            this.status = BookingStatus.CANCELLED;
        }
    }

    /** Short status text: CONFIRMED, RAC 2, WAITING 3 or CANCELLED. */
    public String getStatusDescription() {
        if (cancelled || status == BookingStatus.CANCELLED) {
            return "CANCELLED";
        }
        switch (status) {
            case CONFIRMED:
                return "CONFIRMED";
            case RAC:
                return "RAC " + racPosition;
            case WAITING:
                return "WAITING " + wlPosition;
            default:
                return status.name();
        }
    }

    /** Coach, seat number and berth type, or a dash when no seat is allocated (queued / cancelled). */
    public String getSeatDescription() {
        if (seat == null) return "-";
        return seat.getCoachId() + "-" + seat.getSeatNumber() + " " + seat.getBerthType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return Objects.equals(ticketId, ticket.ticketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId);
    }

    @Override
    public String toString() {
        return passenger.getName() + " -> " + getStatusDescription() + " [₹" + String.format("%.2f", individualFare) + "]";
    }
}
