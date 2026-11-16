package com.railsync.model;

import java.time.LocalTime;

/**
 * Creates the correct {@link Train} subclass from the stored train type. The database stores
 * {@link Train#getTrainType()}; this factory turns it back into an object.
 */
public final class TrainFactory {

    private TrainFactory() {}

    public static Train create(String trainType, String number, String name, Station source,
                               Station destination, LocalTime departure, LocalTime arrival, double distanceKm) {
        String type = trainType == null ? "" : trainType.toLowerCase();
        if (type.contains("rajdhani")) {
            return new RajdhaniExpress(number, name, source, destination, departure, arrival, distanceKm);
        } else if (type.contains("vande")) {
            return new VandeBharatExpress(number, name, source, destination, departure, arrival, distanceKm);
        } else if (type.contains("shatabdi")) {
            return new ShatabdiExpress(number, name, source, destination, departure, arrival, distanceKm);
        } else if (type.contains("superfast")) {
            return new SuperfastExpress(number, name, source, destination, departure, arrival, distanceKm);
        }
        return new ExpressTrain(number, name, source, destination, departure, arrival, distanceKm);
    }

    /** Train types an administrator may choose when adding a train. */
    public static String[] knownTypes() {
        return new String[]{"Rajdhani Express", "Vande Bharat Express", "Shatabdi Express",
                "Superfast Express", "Mail/Express"};
    }
}
