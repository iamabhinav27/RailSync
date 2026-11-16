package com.railsync.model;

import java.time.LocalDate;

/** One passenger waiting in a RAC or Waiting-List queue for a train, date and class. */
public class WaitingEntry {
    public enum QueueType { RAC, WL }

    private final long waitingId;
    private final long bookingPassengerId;
    private final long bookingDbId;
    private final String pnr;
    private final String ticketId;
    private final String passengerName;
    private final String trainNumber;
    private final LocalDate journeyDate;
    private final SeatClass seatClass;
    private QueueType queueType;
    private int position;

    public WaitingEntry(long waitingId, long bookingPassengerId, long bookingDbId, String pnr, String ticketId,
                        String passengerName, String trainNumber, LocalDate journeyDate, SeatClass seatClass,
                        QueueType queueType, int position) {
        this.waitingId = waitingId;
        this.bookingPassengerId = bookingPassengerId;
        this.bookingDbId = bookingDbId;
        this.pnr = pnr;
        this.ticketId = ticketId;
        this.passengerName = passengerName;
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        this.seatClass = seatClass;
        this.queueType = queueType;
        this.position = position;
    }

    public long getWaitingId() { return waitingId; }
    public long getBookingPassengerId() { return bookingPassengerId; }
    public long getBookingDbId() { return bookingDbId; }
    public String getPnr() { return pnr; }
    public String getTicketId() { return ticketId; }
    public String getPassengerName() { return passengerName; }
    public String getTrainNumber() { return trainNumber; }
    public LocalDate getJourneyDate() { return journeyDate; }
    public SeatClass getSeatClass() { return seatClass; }
    public QueueType getQueueType() { return queueType; }
    public void setQueueType(QueueType queueType) { this.queueType = queueType; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getLabel() {
        return queueType + " " + position;
    }

    @Override
    public String toString() {
        return getLabel() + " - " + passengerName + " (" + pnr + ")";
    }
}
