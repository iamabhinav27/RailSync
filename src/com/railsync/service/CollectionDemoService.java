package com.railsync.service;

import com.railsync.model.*;
import com.railsync.util.GenericUtils;

import java.util.*;

/**
 * Collections used on real application data. Each method has a job in the app:
 * <ul>
 *   <li>{@link HashMap}: PNR -> booking for instant look-ups;</li>
 *   <li>{@link HashSet}: reject duplicate station codes;</li>
 *   <li>{@link TreeSet}: sorted, unique train numbers for selection lists;</li>
 *   <li>{@link Iterator}: remove cancelled bookings from a working list;</li>
 *   <li>{@link ArrayList} / {@link LinkedList}: search results and the waiting-list queue
 *       (see {@code SearchService} and {@code WaitingListManager}).</li>
 * </ul>
 */
public class CollectionDemoService {

    /** HashMap keyed by PNR. */
    public Map<String, Booking> indexByPnr(Collection<? extends Booking> bookings) {
        Map<String, Booking> map = new HashMap<>();
        for (Booking b : bookings) map.put(b.getPnr(), b);
        return map;
    }

    /** HashSet: the station codes that appear more than once in an import list. */
    public Set<String> findDuplicateStationCodes(Collection<? extends Station> stations) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new TreeSet<>();
        for (Station s : stations) {
            if (!seen.add(s.getCode())) duplicates.add(s.getCode());
        }
        return duplicates;
    }

    /** TreeSet of train numbers in sorted order, without duplicates. */
    public TreeSet<String> sortedTrainNumbers(Collection<? extends Train> trains) {
        TreeSet<String> numbers = new TreeSet<>();
        for (Train t : trains) numbers.add(t.getTrainNumber());
        return numbers;
    }

    /** TreeSet of unique station names, sorted case-insensitively. */
    public TreeSet<String> sortedStationNames(Collection<? extends Station> stations) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Station s : stations) names.add(s.getName());
        return names;
    }

    /** Iterator.remove(): keeps only bookings that are not cancelled. */
    public List<Booking> activeBookings(Collection<? extends Booking> bookings) {
        List<Booking> active = new ArrayList<>(bookings);
        Iterator<Booking> it = active.iterator();
        while (it.hasNext()) {
            if (it.next().isCancelled()) it.remove();
        }
        return active;
    }

    /** Generic methods with bounded wildcards on real data. */
    public String describeFares(Collection<? extends Booking> bookings) {
        List<Double> fares = new ArrayList<>();
        for (Booking b : bookings) fares.add(b.getTotalFare());
        Collection<Number> sink = new ArrayList<>();
        GenericUtils.copyInto(fares, sink);                       // ? extends Double -> ? super Double
        return String.format("%d fares, total Rs %.2f, highest Rs %.2f", sink.size(), GenericUtils.sum(sink),
                GenericUtils.maxOf(fares).orElse(0.0));
    }

    /** Console demonstration used by {@code --demo}. */
    public void printDemo(Collection<? extends Train> trains, Collection<? extends Station> stations,
                          Collection<? extends Booking> bookings) {
        System.out.println("TreeSet  train numbers : " + sortedTrainNumbers(trains));
        System.out.println("TreeSet  station names : " + sortedStationNames(stations));
        System.out.println("HashSet  duplicate codes: " + findDuplicateStationCodes(stations));
        System.out.println("HashMap  PNRs indexed  : " + indexByPnr(bookings).keySet());
        System.out.println("Iterator active bookings: " + activeBookings(bookings).size() + " of " + bookings.size());
        System.out.println("Generics fare summary  : " + describeFares(bookings));
    }
}
