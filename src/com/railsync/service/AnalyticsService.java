package com.railsync.service;

import com.railsync.model.Booking;
import com.railsync.model.BookingStatus;
import com.railsync.model.SeatClass;
import com.railsync.model.Ticket;
import com.railsync.model.Train;
import com.railsync.repository.SeatRepository;

import java.util.*;

/**
 * Provides summary statistics and administrative reports from stored trains and bookings.
 */
public class AnalyticsService {

    private final Collection<Train> trains;
    private final Collection<Booking> bookings;
    private final SeatRepository seats;

    public AnalyticsService(Collection<Train> trains, Collection<Booking> bookings, SeatRepository seats) {
        this.trains = trains;
        this.bookings = bookings;
        this.seats = seats;
    }

    /** Counts live tickets with a given status, walking the bookings with an {@link Iterator}. */
    private int countTickets(BookingStatus status) {
        int count = 0;
        Iterator<Booking> it = bookings.iterator();
        while (it.hasNext()) {
            for (Ticket t : it.next().getTickets()) {
                if (!t.isCancelled() && t.getStatus() == status) count++;
            }
        }
        return count;
    }

    public int getTotalTrains() {
        return trains.size();
    }

    public int getTotalBookings() {
        return bookings.size();
    }

    public int getTotalTicketsBooked() {
        int count = 0;
        for (Booking b : bookings) {
            count += b.getTickets().size();
        }
        return count;
    }

    public int getConfirmedTicketsCount() {
        return countTickets(BookingStatus.CONFIRMED);
    }

    public int getRacTicketsCount() {
        return countTickets(BookingStatus.RAC);
    }

    public int getWaitingListTicketsCount() {
        return countTickets(BookingStatus.WAITING);
    }

    public int getCancelledTicketsCount() {
        int count = 0;
        for (Booking b : bookings) {
            for (Ticket t : b.getTickets()) {
                if (t.isCancelled() || t.getStatus() == BookingStatus.CANCELLED) {
                    count++;
                }
            }
        }
        return count;
    }

    public double getTotalGrossRevenue() {
        double revenue = 0.0;
        for (Booking b : bookings) {
            revenue += b.getTotalFare();
        }
        return revenue;
    }

    public double getTotalActiveRevenue() {
        double revenue = 0.0;
        for (Booking b : bookings) {
            for (Ticket t : b.getTickets()) {
                if (!t.isCancelled()) {
                    revenue += t.getIndividualFare();
                }
            }
        }
        return revenue;
    }

    public String getMostOccupiedTrain() {
        if (trains.isEmpty()) return "N/A";

        Map<String, Integer> trainOccupancy = new HashMap<>();
        for (Booking b : bookings) {
            for (Ticket t : b.getTickets()) {
                if (!t.isCancelled()) {
                    trainOccupancy.put(b.getTrainNumber(),
                            trainOccupancy.getOrDefault(b.getTrainNumber(), 0) + 1);
                }
            }
        }

        String maxTrainNo = null;
        int maxCount = -1;
        for (Map.Entry<String, Integer> entry : trainOccupancy.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxTrainNo = entry.getKey();
            }
        }

        if (maxTrainNo == null) {
            // Default to first train if no bookings yet
            Train first = trains.iterator().next();
            return first.getTrainNumber() + " - " + first.getTrainName() + " (0 seats)";
        }

        for (Train t : trains) {
            if (t.getTrainNumber().equalsIgnoreCase(maxTrainNo)) {
                return t.getTrainNumber() + " - " + t.getTrainName() + " (" + maxCount + " booked)";
            }
        }
        return maxTrainNo + " (" + maxCount + " booked)";
    }

    public String getMostPopularRoute() {
        if (bookings.isEmpty()) return "N/A";

        Map<String, Integer> routeCounts = new HashMap<>();
        for (Booking b : bookings) {
            String routeKey = b.getSource().getCode() + " ➔ " + b.getDestination().getCode();
            routeCounts.put(routeKey, routeCounts.getOrDefault(routeKey, 0) + 1);
        }

        String topRoute = "N/A";
        int max = 0;
        for (Map.Entry<String, Integer> entry : routeCounts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                topRoute = entry.getKey();
            }
        }
        return topRoute + " (" + max + " bookings)";
    }

    public Map<SeatClass, Integer> getClassWiseOccupancy() {
        Map<SeatClass, Integer> map = new EnumMap<>(SeatClass.class);
        for (SeatClass sc : SeatClass.values()) {
            map.put(sc, 0);
        }
        for (Booking b : bookings) {
            for (Ticket t : b.getTickets()) {
                if (!t.isCancelled()) {
                    SeatClass sc = t.getSeatClass();
                    map.put(sc, map.getOrDefault(sc, 0) + 1);
                }
            }
        }
        return map;
    }

    /** Booked seats as a percentage of all seat rows generated so far (one row per train, date, seat). */
    public double getAverageOccupancyPercentage() {
        int[] occ = seats.occupancy();
        return occ[1] == 0 ? 0.0 : (double) occ[0] / occ[1] * 100.0;
    }
}
