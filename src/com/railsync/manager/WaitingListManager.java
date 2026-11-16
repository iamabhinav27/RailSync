package com.railsync.manager;

import com.railsync.model.BookingStatus;
import com.railsync.model.Seat;
import com.railsync.model.SeatClass;
import com.railsync.model.WaitingEntry;
import com.railsync.model.WaitingEntry.QueueType;
import com.railsync.repository.BookingRepository;
import com.railsync.repository.SeatRepository;
import com.railsync.repository.WaitingListRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Manages the FIFO RAC and Waiting-List queues and promotes passengers when a ticket is cancelled.
 *
 * <p>Queue rows live in the {@code waiting_list} table. While a cancellation is processed each queue
 * is loaded into a {@link LinkedList} (head = earliest passenger), the head is polled and promoted,
 * and the positions of everyone behind it are renumbered. All methods that take a
 * {@link Connection} run inside the caller's transaction.</p>
 */
public class WaitingListManager {

    private final WaitingListRepository queues;
    private final BookingRepository bookings;
    private final SeatRepository seats;

    public WaitingListManager(WaitingListRepository queues, BookingRepository bookings, SeatRepository seats) {
        this.queues = queues;
        this.bookings = bookings;
        this.seats = seats;
    }

    public int queueSize(Connection c, long trainId, LocalDate date, SeatClass cls, QueueType type)
            throws SQLException {
        return queues.count(c, trainId, date, cls, type);
    }

    /** Position (1-based) a new passenger would receive at the back of the queue. */
    public int nextPosition(Connection c, long trainId, LocalDate date, SeatClass cls, QueueType type)
            throws SQLException {
        return queueSize(c, trainId, date, cls, type) + 1;
    }

    /** Marks a cancelled ticket's queue row as cancelled (no-op if the ticket was not queued). */
    public void cancelQueueRow(Connection c, long bookingPassengerId) throws SQLException {
        queues.cancelForTicket(c, bookingPassengerId);
    }

    /** Adds a ticket to the back of a queue and returns its position. */
    public int enqueue(Connection c, long bookingDbId, long bookingPassengerId, long trainId, LocalDate date,
                       SeatClass cls, QueueType type) throws SQLException {
        int position = nextPosition(c, trainId, date, cls, type);
        queues.insert(c, bookingDbId, bookingPassengerId, trainId, date, cls, type, position);
        return position;
    }

    /**
     * Handles everything that follows a cancellation. The cancelled ticket must already be marked
     * CANCELLED (and its queue row cancelled / its seat link removed) before this is called.
     *
     * <ul>
     *   <li>cancelled CONFIRMED: seat goes to the RAC head (RAC head to CONFIRMED, WL head to RAC);
     *       when RAC is empty the earliest waiting-list passenger takes the seat; with nobody queued
     *       the seat is released;</li>
     *   <li>cancelled RAC: the WL head moves up to RAC;</li>
     *   <li>cancelled WL: positions behind it move up.</li>
     * </ul>
     *
     * @return human-readable summary of the promotions that happened
     */
    public String handleCancellation(Connection c, long trainId, LocalDate date, SeatClass cls,
                                     BookingStatus originalStatus, Long freedSeatId) throws SQLException {
        StringBuilder report = new StringBuilder();
        LinkedList<WaitingEntry> racQueue = queues.loadQueue(c, trainId, date, cls, QueueType.RAC);
        LinkedList<WaitingEntry> wlQueue = queues.loadQueue(c, trainId, date, cls, QueueType.WL);

        if (originalStatus == BookingStatus.CONFIRMED && freedSeatId != null) {
            Seat seat = seats.findById(c, freedSeatId);
            String seatLabel = seat != null ? seat.getFormattedSeatCode() : "the freed seat";

            if (!racQueue.isEmpty()) {
                WaitingEntry head = racQueue.poll();
                promoteToConfirmed(c, head, freedSeatId);
                report.append(String.format("RAC passenger '%s' (PNR: %s) PROMOTED to CONFIRMED (Assigned %s). ",
                        head.getPassengerName(), head.getPnr(), seatLabel));
                reindex(c, racQueue, QueueType.RAC);
                promoteWaitingHeadToRac(c, racQueue, wlQueue, report);
            } else if (!wlQueue.isEmpty()) {
                WaitingEntry head = wlQueue.poll();
                promoteToConfirmed(c, head, freedSeatId);
                report.append(String.format("Waiting List passenger '%s' (PNR: %s) PROMOTED to CONFIRMED (Assigned %s). ",
                        head.getPassengerName(), head.getPnr(), seatLabel));
                reindex(c, wlQueue, QueueType.WL);
            } else {
                seats.release(c, freedSeatId);
                report.append("Seat released back to general availability inventory. ");
            }
        } else if (originalStatus == BookingStatus.RAC) {
            reindex(c, racQueue, QueueType.RAC);
            promoteWaitingHeadToRac(c, racQueue, wlQueue, report);
        } else if (originalStatus == BookingStatus.WAITING) {
            reindex(c, wlQueue, QueueType.WL);
            report.append("Passenger removed from Waiting List queue; subsequent positions updated. ");
        }
        return report.toString().trim();
    }

    private void promoteToConfirmed(Connection c, WaitingEntry entry, long seatId) throws SQLException {
        bookings.setTicketStatus(c, entry.getBookingPassengerId(), BookingStatus.CONFIRMED, seatId);
        queues.setStatus(c, entry.getWaitingId(), "PROMOTED");
        bookings.refreshBookingStatus(c, entry.getBookingDbId());
    }

    private void promoteWaitingHeadToRac(Connection c, LinkedList<WaitingEntry> racQueue,
                                         LinkedList<WaitingEntry> wlQueue, StringBuilder report) throws SQLException {
        if (wlQueue.isEmpty()) return;
        WaitingEntry head = wlQueue.poll();
        int position = racQueue.size() + 1;
        head.setQueueType(QueueType.RAC);
        head.setPosition(position);
        queues.setPositionAndType(c, head.getWaitingId(), QueueType.RAC, position);
        bookings.setTicketStatus(c, head.getBookingPassengerId(), BookingStatus.RAC, null);
        bookings.refreshBookingStatus(c, head.getBookingDbId());
        racQueue.add(head);
        report.append(String.format("Waiting List passenger '%s' (PNR: %s) PROMOTED to RAC %d. ",
                head.getPassengerName(), head.getPnr(), position));
        reindex(c, wlQueue, QueueType.WL);
    }

    /** Renumbers a queue 1..n in its current order (persisting only the rows that changed). */
    private void reindex(Connection c, LinkedList<WaitingEntry> queue, QueueType type) throws SQLException {
        int position = 1;
        for (Iterator<WaitingEntry> it = queue.iterator(); it.hasNext(); position++) {
            WaitingEntry entry = it.next();
            if (entry.getPosition() != position) {
                entry.setPosition(position);
                queues.setPositionAndType(c, entry.getWaitingId(), type, position);
            }
        }
    }

    // ------------------------------------------------------------ read-only views

    /** Every active queue entry across all trains (administrator view). */
    public List<WaitingEntry> getAllActiveEntries() {
        return queues.findAllActive();
    }

    /** Active entries of one train, date and class, RAC first, in queue order. */
    public List<WaitingEntry> getQueue(String trainNumber, LocalDate date, SeatClass cls) {
        List<WaitingEntry> result = new ArrayList<>();
        for (WaitingEntry e : queues.findAllActive()) {
            if (e.getTrainNumber().equals(trainNumber) && e.getJourneyDate().equals(date) && e.getSeatClass() == cls) {
                result.add(e);
            }
        }
        return result;
    }
}
