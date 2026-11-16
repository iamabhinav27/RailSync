package com.railsync.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row of a train search: a train, the travel class, live seat availability and the fare
 * for the searched segment on the searched date.
 */
public class SearchResult {
    private final Train train;
    private final Station source;
    private final Station destination;
    private final LocalDate journeyDate;
    private final SeatClass seatClass;
    private final int availableSeats;
    private final int racAvailable;
    private final int waitingAvailable;
    private final double fare;

    public SearchResult(Train train, Station source, Station destination, LocalDate journeyDate,
                        SeatClass seatClass, int availableSeats, int racAvailable,
                        int waitingAvailable, double fare) {
        this.train = train;
        this.source = source;
        this.destination = destination;
        this.journeyDate = journeyDate;
        this.seatClass = seatClass;
        this.availableSeats = availableSeats;
        this.racAvailable = racAvailable;
        this.waitingAvailable = waitingAvailable;
        this.fare = fare;
    }

    public Train getTrain() { return train; }
    public Station getSource() { return source; }
    public Station getDestination() { return destination; }
    public LocalDate getJourneyDate() { return journeyDate; }
    public SeatClass getSeatClass() { return seatClass; }
    public int getAvailableSeats() { return availableSeats; }
    public int getRacAvailable() { return racAvailable; }
    public int getWaitingAvailable() { return waitingAvailable; }
    public double getFare() { return fare; }

    public LocalTime getDepartureTime() { return train.estimatedTimeAt(source); }
    public LocalTime getArrivalTime() { return train.estimatedTimeAt(destination); }

    /** Text for the availability column: free seats, or the queue that is still open. */
    public String getAvailabilityText() {
        if (availableSeats > 0) return String.valueOf(availableSeats);
        if (racAvailable > 0) return "RAC " + racAvailable;
        if (waitingAvailable > 0) return "WL " + waitingAvailable;
        return "REGRET";
    }

    @Override
    public String toString() {
        return train.getTrainName() + " " + source.getCode() + "->" + destination.getCode()
                + " " + seatClass.getCode() + " " + getAvailabilityText();
    }
}
