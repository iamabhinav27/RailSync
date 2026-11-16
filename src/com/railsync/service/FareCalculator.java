package com.railsync.service;

import com.railsync.model.Passenger;
import com.railsync.model.SeatClass;
import com.railsync.model.Station;
import com.railsync.model.Train;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fare calculation and cancellation refunds. {@code calculateFare} is <em>overloaded</em>:
 * one passenger over the whole run, one passenger over an explicit distance, one passenger between
 * two stations, or a whole group of passengers.
 */
public interface FareCalculator {

    /** Fare for one passenger over the train's full distance. */
    double calculateFare(Train train, SeatClass seatClass, Passenger passenger);

    /** Fare for one passenger over an explicit distance (km). */
    double calculateFare(Train train, SeatClass seatClass, Passenger passenger, double distanceKm);

    /** Fare for one passenger travelling between two stations of the train's route. */
    default double calculateFare(Train train, SeatClass seatClass, Passenger passenger, Station from, Station to) {
        return calculateFare(train, seatClass, passenger, train.getDistanceBetween(from, to));
    }

    /** Total fare for a group of passengers over the full run. */
    default double calculateFare(Train train, SeatClass seatClass, List<Passenger> passengers) {
        double total = 0.0;
        for (Passenger p : passengers) {
            total += calculateFare(train, seatClass, p);
        }
        return total;
    }

    /**
     * Refund on cancellation based on the hours left before departure.
     *
     * @param originalFare         the fare initially paid
     * @param journeyDepartureTime the scheduled journey departure timestamp
     * @param cancellationTime     the current cancellation timestamp
     * @param isRacOrWl            whether the ticket was RAC or Waiting List
     * @return array of [netRefundAmount, cancellationFee]
     */
    double[] calculateRefund(double originalFare, LocalDateTime journeyDepartureTime,
                             LocalDateTime cancellationTime, boolean isRacOrWl);
}
