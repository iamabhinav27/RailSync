package com.railsync.thread;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.*;
import com.railsync.manager.WaitingListManager;
import com.railsync.model.*;
import com.railsync.model.WaitingEntry.QueueType;
import com.railsync.repository.*;
import com.railsync.service.FareCalculator;
import com.railsync.util.PNRGenerator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The only place that changes seat inventory. Every booking and cancellation passes through here
 * and is protected on <b>two levels</b>, so the available-seat count can never become inconsistent:
 *
 * <ol>
 *   <li><b>JVM level</b> - a {@code synchronized} block on a lock object that belongs to one
 *       (train, journey date, class) inventory. Threads booking the same inventory queue up;
 *       threads booking different inventories do not block each other.</li>
 *   <li><b>Database level</b> - one JDBC transaction ({@code commit} / {@code rollback}) that starts
 *       with {@code BEGIN IMMEDIATE} (see {@link DatabaseConnection}), a conditional
 *       {@code UPDATE ... WHERE status='AVAILABLE'} to claim a seat, and a {@code UNIQUE} constraint on
 *       {@code booking_passengers.seat_id}. These still hold if a second process uses the same file.</li>
 * </ol>
 *
 * Nothing here depends on {@code Thread.sleep()} for correctness.
 */
public class SeatAllocationService {

    /** Everything needed to create one booking. */
    public static class Request {
        public final Train train;
        public final Station source;
        public final Station destination;
        public final LocalDate journeyDate;
        public final SeatClass seatClass;
        public final List<Passenger> passengers;
        public final List<Double> fares;
        public final String username;

        public Request(Train train, Station source, Station destination, LocalDate journeyDate,
                       SeatClass seatClass, List<Passenger> passengers, List<Double> fares, String username) {
            this.train = train;
            this.source = source;
            this.destination = destination;
            this.journeyDate = journeyDate;
            this.seatClass = seatClass;
            this.passengers = passengers;
            this.fares = fares;
            this.username = username;
        }
    }

    /** Outcome of a cancellation: one refund receipt per cancelled ticket plus the refreshed booking. */
    public static class CancellationResult {
        public final List<RefundReceipt> receipts;
        public final Booking booking;

        CancellationResult(List<RefundReceipt> receipts, Booking booking) {
            this.receipts = receipts;
            this.booking = booking;
        }
    }

    private final ConcurrentMap<String, Object> inventoryLocks = new ConcurrentHashMap<>();

    private final TrainRepository trains;
    private final StationRepository stations;
    private final UserRepository users;
    private final PassengerRepository passengers;
    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final WaitingListManager waitingList;
    private final FareCalculator fareCalculator;

    public SeatAllocationService(TrainRepository trains, StationRepository stations, UserRepository users,
                                 PassengerRepository passengers, SeatRepository seats, BookingRepository bookings,
                                 WaitingListManager waitingList, FareCalculator fareCalculator) {
        this.trains = trains;
        this.stations = stations;
        this.users = users;
        this.passengers = passengers;
        this.seats = seats;
        this.bookings = bookings;
        this.waitingList = waitingList;
        this.fareCalculator = fareCalculator;
    }

    private Object lockFor(String trainNumber, LocalDate date, SeatClass cls) {
        return inventoryLocks.computeIfAbsent(trainNumber + "|" + date + "|" + cls.name(), k -> new Object());
    }

    // ====================================================================== booking

    /**
     * Allocates a confirmed seat, a RAC place or a waiting-list place to every passenger of the
     * request in a single transaction. If any passenger cannot be placed the whole booking is
     * rolled back and {@link SeatUnavailableException} is thrown.
     */
    public Booking allocate(Request request) throws SeatUnavailableException, TrainNotFoundException {
        Object lock = lockFor(request.train.getTrainNumber(), request.journeyDate, request.seatClass);
        synchronized (lock) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    Booking booking = allocateInTransaction(conn, request);
                    conn.commit();
                    return booking;
                } catch (SeatUnavailableException | TrainNotFoundException | RuntimeException e) {
                    rollbackQuietly(conn);
                    throw e;
                } catch (SQLException e) {
                    rollbackQuietly(conn);
                    throw new DatabaseException("Booking failed and was rolled back: " + e.getMessage(), e);
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // connection is closed right after this block anyway
                    }
                }
            } catch (SQLException e) {
                throw new DatabaseException("Could not open a database connection: " + e.getMessage(), e);
            }
        }
    }

    private Booking allocateInTransaction(Connection c, Request r)
            throws SQLException, SeatUnavailableException, TrainNotFoundException {
        Train train = r.train;
        SeatClass cls = r.seatClass;
        long trainId = trains.findDbId(c, train.getTrainNumber());
        if (trainId < 0) {
            throw new TrainNotFoundException("Train '" + train.getTrainNumber() + "' no longer exists.");
        }
        long srcId = stations.findDbId(c, r.source.getCode());
        long dstId = stations.findDbId(c, r.destination.getCode());
        if (srcId < 0 || dstId < 0) {
            throw new SQLException("Unknown station on booking request");
        }
        Long userId = users.findDbId(c, r.username);

        seats.ensureSeats(c, train, trainId, r.journeyDate);

        String pnr = PNRGenerator.generateUniquePNR(candidate -> bookings.pnrExists(c, candidate));
        LocalDateTime bookedAt = LocalDateTime.now();
        long bookingDbId = bookings.insertBooking(c, pnr, userId, trainId, srcId, dstId, cls,
                r.journeyDate, bookedAt, BookingStatus.WAITING);

        Booking booking = new Booking("BK" + (10000 + bookingDbId), pnr, train.getTrainNumber(), train.getTrainName(),
                r.source, r.destination, r.journeyDate, cls, r.username, bookedAt);
        booking.setDbId(bookingDbId);

        int racCapacity = train.getRacCapacity(cls);
        int wlCapacity = train.getWaitingListCapacity(cls);

        int index = 1;
        for (int i = 0; i < r.passengers.size(); i++) {
            Passenger passenger = r.passengers.get(i);
            double fare = r.fares.get(i);
            long passengerId = passengers.findOrCreate(c, passenger, userId);
            String ticketId = "TK-" + pnr + "-" + (index++);

            // 1. Confirmed seat
            Seat seat = seats.claimNextSeat(c, trainId, r.journeyDate, cls, passenger.getBerthPreference());
            if (seat != null) {
                long bpId = bookings.insertTicket(c, bookingDbId, passengerId, seat.getSeatId(), ticketId,
                        BookingStatus.CONFIRMED, fare);
                booking.addTicket(withDbId(new Ticket(ticketId, pnr, passenger, cls, BookingStatus.CONFIRMED,
                        seat, 0, 0, fare), bpId));
                continue;
            }
            // 2. RAC
            if (waitingList.queueSize(c, trainId, r.journeyDate, cls, QueueType.RAC) < racCapacity) {
                long bpId = bookings.insertTicket(c, bookingDbId, passengerId, null, ticketId, BookingStatus.RAC, fare);
                int pos = waitingList.enqueue(c, bookingDbId, bpId, trainId, r.journeyDate, cls, QueueType.RAC);
                booking.addTicket(withDbId(new Ticket(ticketId, pnr, passenger, cls, BookingStatus.RAC,
                        null, pos, 0, fare), bpId));
                continue;
            }
            // 3. Waiting list
            if (waitingList.queueSize(c, trainId, r.journeyDate, cls, QueueType.WL) < wlCapacity) {
                long bpId = bookings.insertTicket(c, bookingDbId, passengerId, null, ticketId, BookingStatus.WAITING, fare);
                int pos = waitingList.enqueue(c, bookingDbId, bpId, trainId, r.journeyDate, cls, QueueType.WL);
                booking.addTicket(withDbId(new Ticket(ticketId, pnr, passenger, cls, BookingStatus.WAITING,
                        null, 0, pos, fare), bpId));
                continue;
            }
            // 4. Nothing left: the exception makes the caller roll the whole booking back.
            throw new SeatUnavailableException("Booking failed: all Confirmed, RAC and Waiting List places are "
                    + "exhausted for " + cls.getDisplayName() + " on train " + train.getTrainNumber()
                    + " (" + r.journeyDate + ").");
        }
        bookings.refreshBookingStatus(c, bookingDbId);
        return booking;
    }

    private static Ticket withDbId(Ticket t, long dbId) {
        t.setDbId(dbId);
        return t;
    }

    // ==================================================================== cancellation

    /**
     * Cancels tickets of a booking inside one transaction, calculates refunds, frees seats and
     * promotes waiting passengers.
     *
     * @param ticketIds ticket ids to cancel, or {@code null} to cancel every live ticket
     * @param now       cancellation time (a parameter so tests can control it)
     */
    public CancellationResult cancel(String pnr, Set<String> ticketIds, LocalDateTime now)
            throws InvalidPNRException, CancellationNotAllowedException, TrainNotFoundException {
        Booking preview = bookings.findById(pnr);
        if (preview == null) {
            throw new InvalidPNRException("No booking found with PNR: " + pnr);
        }
        Train train = trains.findById(preview.getTrainNumber());
        if (train == null) {
            throw new TrainNotFoundException("Train not found for booking: " + preview.getTrainNumber());
        }

        Object lock = lockFor(preview.getTrainNumber(), preview.getJourneyDate(), preview.getSeatClass());
        synchronized (lock) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    List<RefundReceipt> receipts = cancelInTransaction(conn, preview.getPnr(), ticketIds, now, train);
                    conn.commit();
                    return new CancellationResult(receipts, bookings.findById(preview.getPnr()));
                } catch (CancellationNotAllowedException | RuntimeException e) {
                    rollbackQuietly(conn);
                    throw e;
                } catch (SQLException e) {
                    rollbackQuietly(conn);
                    throw new DatabaseException("Cancellation failed and was rolled back: " + e.getMessage(), e);
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // connection is closed right after this block anyway
                    }
                }
            } catch (SQLException e) {
                throw new DatabaseException("Could not open a database connection: " + e.getMessage(), e);
            }
        }
    }

    private List<RefundReceipt> cancelInTransaction(Connection c, String pnr, Set<String> ticketIds,
                                                    LocalDateTime now, Train train)
            throws SQLException, CancellationNotAllowedException {
        // Re-read inside the lock + transaction: another cancellation may have promoted tickets meanwhile.
        List<Booking> fresh = bookings.load(c, "b.pnr = ?", pnr);
        if (fresh.isEmpty()) {
            throw new CancellationNotAllowedException("Booking " + pnr + " no longer exists.");
        }
        Booking booking = fresh.get(0);

        List<Ticket> targets = new ArrayList<>();
        if (ticketIds == null) {
            for (Ticket t : booking.getTickets()) if (!t.isCancelled()) targets.add(t);
        } else {
            for (String id : ticketIds) {
                Ticket found = null;
                for (Ticket t : booking.getTickets()) {
                    if (t.getTicketId().equalsIgnoreCase(id)) found = t;
                }
                if (found == null) {
                    throw new CancellationNotAllowedException("Ticket ID '" + id + "' not found under PNR " + pnr);
                }
                if (found.isCancelled()) {
                    throw new CancellationNotAllowedException("Ticket '" + id + "' is already cancelled.");
                }
                targets.add(found);
            }
        }
        if (targets.isEmpty()) {
            throw new CancellationNotAllowedException("Booking " + pnr + " is already cancelled.");
        }

        LocalDateTime departure = LocalDateTime.of(booking.getJourneyDate(), train.getDepartureTime());
        if (!departure.isAfter(now)) {
            throw new CancellationNotAllowedException("Cancellation is not allowed: train " + train.getTrainNumber()
                    + " has already departed (" + departure.toLocalDate() + " " + departure.toLocalTime() + ").");
        }

        // Queue tickets first (WL, then RAC), confirmed last, so a promotion can never land on a
        // ticket of this same booking that is about to be cancelled too.
        targets.sort(Comparator.comparingInt(t -> cancellationOrder(t.getStatus())));

        long trainId = trains.findDbId(c, train.getTrainNumber());
        List<RefundReceipt> receipts = new ArrayList<>();
        for (Ticket ticket : targets) {
            BookingStatus original = ticket.getStatus();
            boolean queued = original == BookingStatus.RAC || original == BookingStatus.WAITING;
            double[] refund = fareCalculator.calculateRefund(ticket.getIndividualFare(), departure, now, queued);
            Long seatId = ticket.getSeat() != null && ticket.getSeat().getSeatId() > 0
                    ? ticket.getSeat().getSeatId() : null;

            // UPDATE order matters: queue row and seat link are released before anyone is promoted.
            waitingList.cancelQueueRow(c, ticket.getDbId());
            bookings.markTicketCancelled(c, ticket.getDbId(), refund[0]);
            String log = waitingList.handleCancellation(c, trainId, booking.getJourneyDate(),
                    booking.getSeatClass(), original, seatId);

            receipts.add(new RefundReceipt(pnr, ticket.getPassenger().getName(), train.getTrainNumber(),
                    ticket.getIndividualFare(), refund[1], refund[0],
                    log.isEmpty() ? "Cancellation processed successfully." : log));
        }
        bookings.refreshBookingStatus(c, booking.getDbId());
        return receipts;
    }

    private static int cancellationOrder(BookingStatus s) {
        switch (s) {
            case WAITING: return 0;
            case RAC: return 1;
            default: return 2;
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ex) {
            System.err.println("Rollback failed: " + ex.getMessage());
        }
    }
}
