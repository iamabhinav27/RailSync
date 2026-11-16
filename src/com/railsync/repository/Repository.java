package com.railsync.repository;

import java.util.List;

/**
 * Generic repository contract (generic interface with two type parameters).
 *
 * @param <T>  entity type
 * @param <ID> identifier type (e.g. PNR for bookings, train number for trains)
 */
public interface Repository<T, ID> {

    /** Returns the entity with the given id, or {@code null} when none exists. */
    T findById(ID id);

    /** INSERT a new entity. */
    void save(T entity);

    /** UPDATE an existing entity. */
    void update(T entity);

    /** DELETE the entity with the given id. */
    void delete(ID id);

    /** SELECT every entity. */
    List<T> findAll();
}
