package com.railsync.service;

import com.railsync.model.Train;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works only with the abstract {@link Train} type. Every call below is resolved at run time to
 * the subclass method (RajdhaniExpress, VandeBharatExpress, ...): runtime polymorphism / dynamic
 * method dispatch.
 */
public class TrainService {

    /** Describes a train using its own overridden getters. */
    public String describe(Train train) {
        return String.format("%s %s [%s] | %.0f km/h | surcharge Rs %.0f | catering: %s | priority %d",
                train.getTrainNumber(), train.getTrainName(), train.getTrainType(), train.getSpeedKmph(),
                train.getSurcharge(), train.isCateringIncluded() ? "yes" : "no", train.getPriorityLevel());
    }

    /** Trains ordered by their (subclass-defined) priority, highest first. */
    public List<Train> byPriority(List<? extends Train> trains) {
        List<Train> sorted = new ArrayList<>(trains);
        sorted.sort(Comparator.comparingInt(Train::getPriorityLevel).thenComparing(Train::getTrainNumber));
        return sorted;
    }
}
