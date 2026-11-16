package com.railsync.service;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.exception.InvalidStationException;
import com.railsync.model.*;
import com.railsync.model.WaitingEntry.QueueType;
import com.railsync.repository.*;
import com.railsync.util.ValidationUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Train search with live availability, sorting, and booking look-ups. */
public class SearchService {

    private final TrainRepository trains;
    private final StationRepository stations;
    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final WaitingListRepository waitingList;
    private final FareCalculator fareCalculator;

    public SearchService(TrainRepository trains, StationRepository stations, SeatRepository seats,
                         BookingRepository bookings, WaitingListRepository waitingList, FareCalculator fareCalculator) {
        this.trains = trains;
        this.stations = stations;
        this.seats = seats;
        this.bookings = bookings;
        this.waitingList = waitingList;
        this.fareCalculator = fareCalculator;
    }

    // ================= stations =================

    /** Unique stations sorted by name (TreeSet uses Station's natural order). */
    public TreeSet<Station> getSortedStations() {
        return new TreeSet<>(stations.findAll());
    }

    // ================= search =================

    /** Looks stations up by code first, then searches. */
    public ArrayList<SearchResult> searchAvailability(String sourceCode, String destinationCode, LocalDate date,
                                                      SeatClass seatClass) throws InvalidStationException {
        Station from = stations.findById(sourceCode);
        Station to = stations.findById(destinationCode);
        if (from == null) throw new InvalidStationException("Unknown source station: " + sourceCode);
        if (to == null) throw new InvalidStationException("Unknown destination station: " + destinationCode);
        return searchAvailability(from, to, date, seatClass);
    }

    /**
     * Finds trains serving source -> destination and returns one row per matching class with the live
     * seat availability for the date and the fare for the segment.
     *
     * @param seatClass a class to filter on, or {@code null} for all classes
     * @throws InvalidStationException when a station is missing or both are the same
     * @throws IllegalArgumentException when the date is in the past or too far ahead
     */
    public ArrayList<SearchResult> searchAvailability(Station source, Station destination, LocalDate date,
                                                      SeatClass seatClass) throws InvalidStationException {
        ValidationUtils.validateStations(source, destination);
        ValidationUtils.validateJourneyDate(date);

        ArrayList<SearchResult> results = new ArrayList<>();
        Passenger adult = new Passenger("Sample Adult", 30, "Male", "", BerthType.LOWER);
        try (Connection c = DatabaseConnection.getConnection()) {
            for (Train train : trains.findAll()) {
                if (!train.servesRoute(source, destination)) continue;
                long trainId = trains.findDbId(c, train.getTrainNumber());
                for (SeatClass cls : train.getAvailableClasses()) {
                    if (seatClass != null && cls != seatClass) continue;
                    int free = seats.countAvailable(c, trainId, date, cls, train.getConfirmedCapacity(cls));
                    int racFree = Math.max(0, train.getRacCapacity(cls) - waitingList.count(c, trainId, date, cls, QueueType.RAC));
                    int wlFree = Math.max(0, train.getWaitingListCapacity(cls) - waitingList.count(c, trainId, date, cls, QueueType.WL));
                    double fare = fareCalculator.calculateFare(train, cls, adult, source, destination);
                    results.add(new SearchResult(train, source, destination, date, cls, free, racFree, wlFree, fare));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Train search failed: " + e.getMessage(), e);
        }
        return results;
    }

    /** Trains that serve the route (no availability). */
    public List<Train> searchTrains(Station source, Station destination, LocalDate journeyDate)
            throws InvalidStationException {
        ValidationUtils.validateStations(source, destination);
        List<Train> results = new ArrayList<>();
        for (Train train : trains.findAll()) {
            if (train.servesRoute(source, destination)) results.add(train);
        }
        return results;
    }

    public List<Train> searchTrainsByQuery(String query) {
        List<Train> all = trains.findAll();
        if (query == null || query.trim().isEmpty()) return all;
        String q = query.trim().toLowerCase();
        List<Train> results = new ArrayList<>();
        for (Train t : all) {
            if (t.getTrainNumber().toLowerCase().contains(q) || t.getTrainName().toLowerCase().contains(q)
                    || t.getSource().getName().toLowerCase().contains(q)
                    || t.getDestination().getName().toLowerCase().contains(q)) {
                results.add(t);
            }
        }
        return results;
    }

    // ================= comparators & sorting =================

    public static Comparator<SearchResult> byDepartureTime() {
        return Comparator.comparing(SearchResult::getDepartureTime);
    }

    public static Comparator<SearchResult> byFare() {
        return Comparator.comparingDouble(SearchResult::getFare);
    }

    public static Comparator<SearchResult> byAvailability() {
        return Comparator.comparingInt(SearchResult::getAvailableSeats).reversed();
    }

    /** Returns a new ArrayList sorted by the comparator ({@code ? super} lets a more general comparator be used). */
    public ArrayList<SearchResult> sortResults(List<SearchResult> results, Comparator<? super SearchResult> comparator) {
        ArrayList<SearchResult> sorted = new ArrayList<>(results);
        sorted.sort(comparator);
        return sorted;
    }

    public static Comparator<Train> sortByDepartureTime() {
        return Comparator.comparing(Train::getDepartureTime);
    }

    public static Comparator<Train> sortByDuration() {
        return Comparator.comparing(Train::getJourneyDuration);
    }

    public static Comparator<Train> sortByTrainNumber() {
        return Comparator.comparing(Train::getTrainNumber);
    }

    // ================= bookings =================

    public Optional<Booking> findBookingByPNR(String pnr) {
        return Optional.ofNullable(bookings.findById(pnr));
    }

    public List<Booking> searchBookingsByPassengerName(String partialName) {
        return partialName == null || partialName.isBlank() ? Collections.emptyList()
                : bookings.findByPassengerName(partialName);
    }

    public List<Booking> searchBookingsByPhone(String phone) {
        return phone == null || phone.isBlank() ? Collections.emptyList() : bookings.findByPhone(phone);
    }

    public List<Booking> searchBookingsByTrain(String trainNumber) {
        return trainNumber == null || trainNumber.isBlank() ? Collections.emptyList() : bookings.findByTrain(trainNumber);
    }

    public List<Booking> searchBookingsByUser(String username) {
        return username == null ? Collections.emptyList() : bookings.findByUser(username);
    }
}
