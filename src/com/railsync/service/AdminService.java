package com.railsync.service;

import com.railsync.exception.*;
import com.railsync.manager.WaitingListManager;
import com.railsync.model.*;
import com.railsync.repository.*;
import com.railsync.util.ValidationUtils;

import java.time.LocalTime;
import java.util.*;

/**
 * Administrator operations. Every method first checks the caller's role through
 * {@link AuthenticationService}, so a passenger can never reach them.
 */
public class AdminService {

    /** Coach layouts offered when an administrator adds a train. */
    public enum CoachPreset {
        SLEEPER_AND_3A("Sleeper + AC 3 Tier"),
        CHAIR_CAR("AC Chair Car"),
        PREMIUM("AC First + AC 2 Tier + AC 3 Tier");

        private final String label;
        CoachPreset(String label) { this.label = label; }

        void applyTo(Train t) {
            switch (this) {
                case CHAIR_CAR:
                    t.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, 50, 8, 15));
                    t.addCoach(new Coach("C2", SeatClass.CHAIR_CAR, 50, 8, 15));
                    break;
                case PREMIUM:
                    t.addCoach(new Coach("H1", SeatClass.FIRST_AC, 18, 4, 10));
                    t.addCoach(new Coach("A1", SeatClass.SECOND_AC, 36, 6, 15));
                    t.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
                    break;
                default:
                    t.addCoach(new Coach("S1", SeatClass.SLEEPER, 60, 12, 30));
                    t.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
            }
        }

        @Override
        public String toString() { return label; }
    }

    private final AuthenticationService auth;
    private final TrainRepository trains;
    private final StationRepository stations;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final WaitingListManager waitingList;
    private final SeatRepository seats;

    /** Station codes already registered (HashSet): rejects duplicates before touching the database. */
    private final Set<String> stationCodes = new HashSet<>();

    public AdminService(AuthenticationService auth, TrainRepository trains, StationRepository stations,
                        BookingRepository bookings, UserRepository users, WaitingListManager waitingList,
                        SeatRepository seats) {
        this.auth = auth;
        this.trains = trains;
        this.stations = stations;
        this.bookings = bookings;
        this.users = users;
        this.waitingList = waitingList;
        this.seats = seats;
    }

    // ---------------------------------------------------------------- trains

    /** Builds a train object from form values. @throws InvalidTrainException for bad values. */
    public Train buildTrain(String type, String number, String name, Station source, Station destination,
                            LocalTime departure, LocalTime arrival, double distanceKm, CoachPreset preset)
            throws InvalidTrainException {
        try {
            if (distanceKm <= 0) throw new IllegalArgumentException("Distance must be greater than zero.");
            if (departure == null || arrival == null) throw new IllegalArgumentException("Departure and arrival times are required.");
            Train t = TrainFactory.create(type, number, name, source, destination, departure, arrival, distanceKm);
            if (preset != null) preset.applyTo(t);
            return t;
        } catch (IllegalArgumentException e) {
            throw new InvalidTrainException(e.getMessage());
        }
    }

    public void addTrain(User actor, Train train) throws AccessDeniedException, InvalidTrainException {
        auth.requirePermission(actor, User.Permission.MANAGE_TRAINS);
        if (trains.findById(train.getTrainNumber()) != null) {
            throw new InvalidTrainException("Train " + train.getTrainNumber() + " already exists.");
        }
        if (train.getCoaches().isEmpty()) {
            throw new InvalidTrainException("A train needs at least one coach.");
        }
        try {
            trains.save(train);
        } catch (DatabaseException e) {
            throw new InvalidTrainException("Could not add train: " + e.getMessage());
        }
    }

    /** Updates name, timings, distance and fare rate of an existing train (route and coaches are kept). */
    public Train updateTrain(User actor, String trainNumber, String newName, LocalTime departure, LocalTime arrival,
                             double distanceKm, double baseFareRate)
            throws AccessDeniedException, TrainNotFoundException, InvalidTrainException {
        auth.requirePermission(actor, User.Permission.MANAGE_TRAINS);
        Train old = trains.findById(trainNumber);
        if (old == null) throw new TrainNotFoundException("Train " + trainNumber + " was not found.");
        if (baseFareRate <= 0) throw new InvalidTrainException("Fare rate must be greater than zero.");
        Train updated = buildTrain(old.getTrainType(), old.getTrainNumber(), newName, old.getSource(),
                old.getDestination(), departure, arrival, distanceKm, null);
        for (Station s : old.getIntermediateStations()) updated.addIntermediateStation(s);
        for (Coach c : old.getCoaches()) updated.addCoach(c);
        updated.setBaseFareRatePerKm(baseFareRate);
        trains.update(updated);
        return updated;
    }

    /**
     * Deletes a train only when no booking (of any status) refers to it.
     *
     * @throws InvalidTrainException when dependent bookings exist
     */
    public void deleteTrain(User actor, String trainNumber)
            throws AccessDeniedException, TrainNotFoundException, InvalidTrainException {
        auth.requirePermission(actor, User.Permission.MANAGE_TRAINS);
        if (trains.findById(trainNumber) == null) {
            throw new TrainNotFoundException("Train " + trainNumber + " was not found.");
        }
        int dependents = bookings.countByTrain(trainNumber);
        if (dependents > 0) {
            throw new InvalidTrainException("Train " + trainNumber + " cannot be deleted: it has " + dependents
                    + " booking(s). Cancel/remove them first.");
        }
        trains.delete(trainNumber);
    }

    public void addStation(User actor, Station station) throws AccessDeniedException, InvalidStationException {
        auth.requirePermission(actor, User.Permission.MANAGE_TRAINS);
        stationCodes.clear();                                   // reload so the set matches the database
        for (Station s : stations.findAll()) stationCodes.add(s.getCode());
        if (!stationCodes.add(station.getCode())) {
            throw new InvalidStationException("Station code " + station.getCode() + " already exists.");
        }
        try {
            stations.save(station);
        } catch (DatabaseException e) {
            stationCodes.remove(station.getCode());
            throw new InvalidStationException("Could not add station: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- views

    public List<Booking> getAllBookings(User actor) throws AccessDeniedException {
        auth.requirePermission(actor, User.Permission.VIEW_ALL_BOOKINGS);
        return bookings.findAll();
    }

    public List<User> getAllPassengers(User actor) throws AccessDeniedException {
        auth.requirePermission(actor, User.Permission.VIEW_PASSENGERS);
        return users.findPassengers();
    }

    public List<WaitingEntry> getWaitingLists(User actor) throws AccessDeniedException {
        auth.requirePermission(actor, User.Permission.VIEW_WAITING_LISTS);
        return waitingList.getAllActiveEntries();
    }

    // ---------------------------------------------------------------- reports

    public enum ReportType { SUMMARY, BOOKINGS, PASSENGERS, WAITING_LIST }

    public String generateReport(User actor, ReportType type) throws AccessDeniedException {
        auth.requirePermission(actor, User.Permission.GENERATE_REPORTS);
        switch (type) {
            case BOOKINGS:
                return ReportGenerator.generateBookingsReport(bookings.findAll());
            case PASSENGERS:
                return ReportGenerator.generatePassengerReport(users.findPassengers());
            case WAITING_LIST:
                return ReportGenerator.generateWaitingListReport(waitingList.getAllActiveEntries());
            default:
                return ReportGenerator.generateAdminReport(analytics());
        }
    }

    public AnalyticsService analytics() {
        return new AnalyticsService(trains.findAll(), bookings.findAll(), seats);
    }
}
