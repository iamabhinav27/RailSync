package com.railsync.manager;

import com.railsync.exception.*;
import com.railsync.model.*;
import com.railsync.repository.*;
import com.railsync.service.FareCalculator;
import com.railsync.thread.SeatAllocationService;
import com.railsync.util.ValidationUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Passenger-facing reservation operations on top of the database. It validates requests, calculates
 * fares, delegates seat changes to {@link SeatAllocationService} (synchronized + transactional) and
 * writes receipt files afterwards. It holds no booking state of its own: the database is the source of truth.
 */
public class ReservationManager {

    private final TrainRepository trains;
    private final StationRepository stations;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final WaitingListManager waitingListManager;
    private final FareCalculator fareCalculator;
    private final SeatAllocationService allocationService;
    private boolean receiptsEnabled = true;

    public ReservationManager(TrainRepository trains, StationRepository stations, BookingRepository bookings,
                              SeatRepository seats, WaitingListManager waitingListManager,
                              FareCalculator fareCalculator, SeatAllocationService allocationService) {
        this.trains = trains;
        this.stations = stations;
        this.bookings = bookings;
        this.seats = seats;
        this.waitingListManager = waitingListManager;
        this.fareCalculator = fareCalculator;
        this.allocationService = allocationService;
    }

    /** Turn receipt-file writing off (used by the concurrency simulation so it leaves no files behind). */
    public void setReceiptsEnabled(boolean enabled) {
        this.receiptsEnabled = enabled;
    }

    // ================= lookups =================

    public Collection<Station> getAllStations() { return stations.findAll(); }

    public Station getStation(String code) { return stations.findById(code); }

    public Collection<Train> getAllTrains() { return trains.findAll(); }

    public Train getTrain(String trainNumber) { return trains.findById(trainNumber); }

    public WaitingListManager getWaitingListManager() { return waitingListManager; }

    public FareCalculator getFareCalculator() { return fareCalculator; }

    /** Snapshot of every booking indexed by PNR (HashMap). */
    public Map<String, Booking> getBookingMap() { return bookings.findAllAsMap(); }

    public List<Booking> getBookingsForUser(String username) { return bookings.findByUser(username); }

    /** Free confirmed seats for a train, date and class. */
    public int getAvailableSeats(Train train, LocalDate date, SeatClass cls) {
        long id = trains.findDbId(train.getTrainNumber());
        return seats.countAvailable(id, date, cls, train.getConfirmedCapacity(cls));
    }

    /**
     * Looks a booking up by PNR in the database.
     *
     * @throws InvalidPNRException when the PNR is blank, malformed or unknown
     */
    public Booking findBooking(String pnr) throws InvalidPNRException {
        if (pnr == null || pnr.isBlank()) {
            throw new InvalidPNRException("Please enter a PNR.");
        }
        if (!ValidationUtils.isValidPnr(pnr)) {
            throw new InvalidPNRException("'" + pnr.trim() + "' is not a valid PNR (expected e.g. RS123456).");
        }
        Booking booking = bookings.findById(pnr);
        if (booking == null) {
            throw new InvalidPNRException("No booking found with PNR: " + pnr.trim().toUpperCase());
        }
        return booking;
    }

    // ================= booking =================

    /** Books on the train's full route. */
    public Booking bookTicket(String trainNumber, LocalDate journeyDate, SeatClass seatClass,
                              List<Passenger> passengers, String userId)
            throws TrainNotFoundException, InvalidBookingException, InvalidPassengerException,
            SeatUnavailableException, InvalidStationException {
        return bookTicket(trainNumber, null, null, journeyDate, seatClass, passengers, userId);
    }

    /**
     * Books tickets for one or more passengers (max 6) between two stations on the train's route.
     * The result tells each passenger whether they are CONFIRMED, RAC or WAITING.
     *
     * @throws SeatUnavailableException when confirmed, RAC and waiting-list places are all exhausted
     */
    public Booking bookTicket(String trainNumber, Station from, Station to, LocalDate journeyDate,
                              SeatClass seatClass, List<Passenger> passengers, String userId)
            throws TrainNotFoundException, InvalidBookingException, InvalidPassengerException,
            SeatUnavailableException, InvalidStationException {

        Train train = trains.findById(trainNumber);
        if (train == null) {
            throw new TrainNotFoundException("Train with number '" + trainNumber + "' was not found.");
        }
        Station source = from != null ? from : train.getSource();
        Station destination = to != null ? to : train.getDestination();
        ValidationUtils.validateStations(source, destination);
        if (!train.servesRoute(source, destination)) {
            throw new InvalidBookingException("Train " + train.getTrainNumber() + " does not run from "
                    + source.getName() + " to " + destination.getName() + ".");
        }
        if (seatClass == null || !train.getAvailableClasses().contains(seatClass)) {
            throw new InvalidBookingException("Train " + train.getTrainNumber() + " has no "
                    + (seatClass == null ? "selected" : seatClass.getDisplayName()) + " coaches.");
        }
        try {
            ValidationUtils.validateJourneyDate(journeyDate);
        } catch (IllegalArgumentException e) {
            throw new InvalidBookingException(e.getMessage());
        }
        if (passengers == null || passengers.isEmpty()) {
            throw new InvalidBookingException("A booking must contain at least one passenger.");
        }
        if (passengers.size() > 6) {
            throw new InvalidBookingException("Maximum 6 passengers allowed per booking transaction.");
        }
        for (Passenger p : passengers) {
            ValidationUtils.validatePassenger(p.getName(), p.getAge(), p.getPhone());
        }

        List<Double> fares = new ArrayList<>();
        for (Passenger p : passengers) {
            fares.add(fareCalculator.calculateFare(train, seatClass, p, source, destination));
        }

        Booking booking = allocationService.allocate(new SeatAllocationService.Request(
                train, source, destination, journeyDate, seatClass, passengers, fares, userId));
        saveReceiptQuietly(booking);
        return booking;
    }

    // ================= cancellation =================

    /** Cancels one ticket (no ownership check; used by internal callers and tests). */
    public RefundReceipt cancelTicket(String pnr, String ticketId)
            throws InvalidPNRException, CancellationNotAllowedException, TrainNotFoundException {
        List<RefundReceipt> receipts = cancel(pnr, Collections.singleton(ticketId), null);
        return receipts.get(0);
    }

    /** Cancels one ticket; a passenger may only cancel their own booking, an admin any. */
    public RefundReceipt cancelTicket(User requester, String pnr, String ticketId)
            throws InvalidPNRException, CancellationNotAllowedException, TrainNotFoundException {
        return cancel(pnr, Collections.singleton(ticketId), requester).get(0);
    }

    /** Cancels every live ticket of a booking; the booking status becomes CANCELLED. */
    public List<RefundReceipt> cancelBooking(User requester, String pnr)
            throws InvalidPNRException, CancellationNotAllowedException, TrainNotFoundException {
        return cancel(pnr, null, requester);
    }

    private List<RefundReceipt> cancel(String pnr, Set<String> ticketIds, User requester)
            throws InvalidPNRException, CancellationNotAllowedException, TrainNotFoundException {
        Booking existing = findBooking(pnr);
        if (requester != null && !requester.isAdmin()
                && !requester.getUsername().equalsIgnoreCase(existing.getBookedByUserId())) {
            throw new CancellationNotAllowedException("You can only cancel your own bookings.");
        }
        SeatAllocationService.CancellationResult result =
                allocationService.cancel(existing.getPnr(), ticketIds, LocalDateTime.now());
        if (receiptsEnabled) {
            try {
                FileManager.saveReceipt(result.booking);                  // receipt now shows CANCELLED
                for (RefundReceipt r : result.receipts) FileManager.saveRefundReceipt(r);
            } catch (IOException e) {
                System.err.println("Warning: could not update receipt files: " + e.getMessage());
            }
        }
        return result.receipts;
    }

    private void saveReceiptQuietly(Booking booking) {
        if (!receiptsEnabled) return;
        try {
            FileManager.saveReceipt(booking);
        } catch (IOException e) {
            // The booking is already committed; a missing receipt file must not undo it.
            System.err.println("Warning: booking " + booking.getPnr() + " saved but receipt file failed: " + e.getMessage());
        }
    }
}
