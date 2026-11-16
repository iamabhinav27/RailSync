package com.railsync.util;

import java.util.*;
import java.util.function.Function;

/**
 * Generic helper methods used by the services:
 * generic methods, bounded type parameters, {@code ? extends} (producer) and {@code ? super} (consumer).
 */
public final class GenericUtils {

    private GenericUtils() {}

    /** Generic method with a bounded type parameter: the largest element by natural order. */
    public static <T extends Comparable<? super T>> Optional<T> maxOf(Collection<? extends T> items) {
        T best = null;
        for (T item : items) {
            if (best == null || item.compareTo(best) > 0) best = item;
        }
        return Optional.ofNullable(best);
    }

    /**
     * Upper-bounded wildcard ({@code ? extends T}): reads from a collection of any subtype of T.
     * Example: pass a {@code List<Booking>} where a {@code Collection<? extends Booking>} is expected.
     */
    public static double sum(Collection<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) total += n.doubleValue();
        return total;
    }

    /**
     * Lower-bounded wildcard ({@code ? super T}): writes into a collection of any supertype of T.
     * Example: copy {@code Train} objects into a {@code Collection<Object>} or {@code Collection<Comparable<Train>>}.
     */
    public static <T> int copyInto(Collection<? extends T> source, Collection<? super T> target) {
        int copied = 0;
        for (T item : source) {
            if (target.add(item)) copied++;
        }
        return copied;
    }

    /** Groups items by a key (generic method with two type parameters). */
    public static <K, V> Map<K, List<V>> groupBy(Collection<? extends V> items, Function<? super V, ? extends K> keyFn) {
        Map<K, List<V>> groups = new LinkedHashMap<>();
        for (V item : items) {
            groups.computeIfAbsent(keyFn.apply(item), k -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    /** Counts items per key. */
    public static <K, V> Map<K, Integer> countBy(Collection<? extends V> items, Function<? super V, ? extends K> keyFn) {
        Map<K, Integer> counts = new LinkedHashMap<>();
        for (V item : items) {
            counts.merge(keyFn.apply(item), 1, Integer::sum);
        }
        return counts;
    }
}
