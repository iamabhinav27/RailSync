package com.railsync.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a train ticket booking with passenger tickets and journey details.
 */
public class Booking implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String bookingId;
    private final String pnr;
    private final String trainNumber;
    private final String trainName;
    private final Station source;
    private final Station destination;
    private final LocalDate journeyDate;
    private final LocalDateTime bookingTimestamp;
    private long dbId;                  // database id (0 until saved)
    private final SeatClass seatClass;
    private final List<Ticket> tickets;
    private double totalFare;
    private boolean cancelled;
    private final String bookedByUserId;

    public Booking(String bookingId, String pnr, String trainNumber, String trainName,
                   Station source, Station destination, LocalDate journeyDate,
                   SeatClass seatClass, String bookedByUserId) {
        this(bookingId, pnr, trainNumber, trainName, source, destination, journeyDate,
                seatClass, bookedByUserId, LocalDateTime.now());
    }

    /** Used when re-creating a booking from the database with its original booking time. */
    public Booking(String bookingId, String pnr, String trainNumber, String trainName,
                   Station source, Station destination, LocalDate journeyDate,
                   SeatClass seatClass, String bookedByUserId, LocalDateTime bookingTimestamp) {
        this.bookingId = Objects.requireNonNull(bookingId, "Booking ID cannot be null");
        this.pnr = Objects.requireNonNull(pnr, "PNR cannot be null");
        this.trainNumber = Objects.requireNonNull(trainNumber, "Train number cannot be null");
        this.trainName = Objects.requireNonNull(trainName, "Train name cannot be null");
        this.source = Objects.requireNonNull(source, "Source station cannot be null");
        this.destination = Objects.requireNonNull(destination, "Destination station cannot be null");
        this.journeyDate = Objects.requireNonNull(journeyDate, "Journey date cannot be null");
        this.seatClass = Objects.requireNonNull(seatClass, "SeatClass cannot be null");
        this.bookedByUserId = bookedByUserId != null ? bookedByUserId : "GUEST";
        this.bookingTimestamp = bookingTimestamp != null ? bookingTimestamp : LocalDateTime.now();
        this.tickets = new ArrayList<>();
        this.totalFare = 0.0;
        this.cancelled = false;
    }

    public void addTicket(Ticket ticket) {
        if (ticket != null) {
            tickets.add(ticket);
            recalculateTotalFare();
        }
    }

    public void recalculateTotalFare() {
        double sum = 0.0;
        for (Ticket t : tickets) {
            sum += t.getIndividualFare();
        }
        this.totalFare = sum;
    }

    public long getDbId() {
        return dbId;
    }

    public void setDbId(long dbId) {
        this.dbId = dbId;
    }

    /**
     * Overall booking status derived from its tickets:
     * CANCELLED when every ticket is cancelled, CONFIRMED when at least one live ticket is
     * confirmed, otherwise RAC / WAITING for the best queue position held.
     */
    public BookingStatus getStatus() {
        if (isCancelled()) return BookingStatus.CANCELLED;
        if (getConfirmedCount() > 0) return BookingStatus.CONFIRMED;
        if (getRacCount() > 0) return BookingStatus.RAC;
        return BookingStatus.WAITING;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getPnr() {
        return pnr;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public Station getSource() {
        return source;
    }

    public Station getDestination() {
        return destination;
    }

    public LocalDate getJourneyDate() {
        return journeyDate;
    }

    public LocalDateTime getBookingTimestamp() {
        return bookingTimestamp;
    }

    public SeatClass getSeatClass() {
        return seatClass;
    }

    public List<Ticket> getTickets() {
        return Collections.unmodifiableList(tickets);
    }

    public double getTotalFare() {
        return totalFare;
    }

    public boolean isCancelled() {
        if (cancelled) return true;
        // If all tickets in booking are cancelled, the whole booking is cancelled
        if (tickets.isEmpty()) return false;
        for (Ticket t : tickets) {
            if (!t.isCancelled()) return false;
        }
        return true;
    }

    public boolean isAllCancelled() {
        return isCancelled();
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        if (cancelled) {
            for (Ticket t : tickets) {
                t.setCancelled(true);
            }
        }
    }

    public String getBookedByUserId() {
        return bookedByUserId;
    }

    public int getPassengerCount() {
        return tickets.size();
    }

    public int getConfirmedCount() {
        int c = 0;
        for (Ticket t : tickets) {
            if (!t.isCancelled() && t.getStatus() == BookingStatus.CONFIRMED) c++;
        }
        return c;
    }

    public int getRacCount() {
        int c = 0;
        for (Ticket t : tickets) {
            if (!t.isCancelled() && t.getStatus() == BookingStatus.RAC) c++;
        }
        return c;
    }

    public int getWaitingListCount() {
        int c = 0;
        for (Ticket t : tickets) {
            if (!t.isCancelled() && t.getStatus() == BookingStatus.WAITING) c++;
        }
        return c;
    }

    public String getFormattedBookingTime() {
        return bookingTimestamp.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));
    }

    public String getFormattedJourneyDate() {
        return journeyDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy (EEE)"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Booking booking = (Booking) o;
        return Objects.equals(pnr, booking.pnr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pnr);
    }

    @Override
    public String toString() {
        return "Booking[PNR=" + pnr + ", Train=" + trainNumber + ", Passengers=" + tickets.size() +
                ", Date=" + journeyDate + ", Total=₹" + String.format("%.2f", totalFare) + "]";
    }
}
