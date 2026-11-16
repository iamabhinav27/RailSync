package com.railsync.database;

import com.railsync.exception.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the RailSync schema (idempotent: safe to run on every start-up). */
public final class DatabaseInitializer {

    private DatabaseInitializer() {}

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS users (
            user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
            username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
            name          TEXT NOT NULL,
            email         TEXT NOT NULL DEFAULT '',
            phone         TEXT NOT NULL DEFAULT '',
            password_hash TEXT NOT NULL,
            role          TEXT NOT NULL CHECK (role IN ('PASSENGER','ADMIN')),
            created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )""",
        """
        CREATE TABLE IF NOT EXISTS stations (
            station_id         INTEGER PRIMARY KEY AUTOINCREMENT,
            station_code       TEXT NOT NULL UNIQUE COLLATE NOCASE,
            station_name       TEXT NOT NULL,
            city               TEXT NOT NULL DEFAULT '',
            state              TEXT NOT NULL DEFAULT '',
            zone               TEXT NOT NULL DEFAULT 'IR',
            distance_marker_km REAL NOT NULL DEFAULT 0
        )""",
        """
        CREATE TABLE IF NOT EXISTS trains (
            train_id               INTEGER PRIMARY KEY AUTOINCREMENT,
            train_number           TEXT NOT NULL UNIQUE,
            train_name             TEXT NOT NULL,
            train_type             TEXT NOT NULL,
            source_station_id      INTEGER NOT NULL REFERENCES stations(station_id),
            destination_station_id INTEGER NOT NULL REFERENCES stations(station_id),
            departure_time         TEXT NOT NULL,
            arrival_time           TEXT NOT NULL,
            distance_km            REAL NOT NULL,
            base_fare_rate         REAL NOT NULL,
            CHECK (source_station_id <> destination_station_id)
        )""",
        """
        CREATE TABLE IF NOT EXISTS train_routes (
            route_id        INTEGER PRIMARY KEY AUTOINCREMENT,
            train_id        INTEGER NOT NULL REFERENCES trains(train_id) ON DELETE CASCADE,
            station_id      INTEGER NOT NULL REFERENCES stations(station_id),
            sequence_number INTEGER NOT NULL,
            arrival_time    TEXT,
            departure_time  TEXT,
            UNIQUE (train_id, sequence_number)
        )""",
        """
        CREATE TABLE IF NOT EXISTS train_coaches (
            coach_id     INTEGER PRIMARY KEY AUTOINCREMENT,
            train_id     INTEGER NOT NULL REFERENCES trains(train_id) ON DELETE CASCADE,
            coach_code   TEXT NOT NULL,
            seat_class   TEXT NOT NULL,
            total_seats  INTEGER NOT NULL CHECK (total_seats > 0),
            rac_capacity INTEGER NOT NULL DEFAULT 0,
            wl_capacity  INTEGER NOT NULL DEFAULT 0,
            UNIQUE (train_id, coach_code)
        )""",
        // One row per physical seat per train per journey date. The UNIQUE key plus the
        // conditional UPDATE in SeatRepository guarantee a seat is never sold twice.
        """
        CREATE TABLE IF NOT EXISTS seats (
            seat_id      INTEGER PRIMARY KEY AUTOINCREMENT,
            train_id     INTEGER NOT NULL REFERENCES trains(train_id) ON DELETE CASCADE,
            journey_date TEXT NOT NULL,
            coach_code   TEXT NOT NULL,
            seat_number  INTEGER NOT NULL,
            seat_class   TEXT NOT NULL,
            berth_type   TEXT NOT NULL,
            status       TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','BOOKED')),
            UNIQUE (train_id, journey_date, coach_code, seat_number)
        )""",
        """
        CREATE TABLE IF NOT EXISTS passengers (
            passenger_id     INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id          INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
            name             TEXT NOT NULL,
            age              INTEGER NOT NULL CHECK (age BETWEEN 0 AND 125),
            gender           TEXT NOT NULL,
            phone            TEXT NOT NULL DEFAULT '',
            concession       TEXT NOT NULL DEFAULT 'NONE',
            berth_preference TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS bookings (
            booking_id             INTEGER PRIMARY KEY AUTOINCREMENT,
            pnr                    TEXT NOT NULL UNIQUE,
            user_id                INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
            train_id               INTEGER NOT NULL REFERENCES trains(train_id),
            source_station_id      INTEGER NOT NULL REFERENCES stations(station_id),
            destination_station_id INTEGER NOT NULL REFERENCES stations(station_id),
            seat_class             TEXT NOT NULL,
            journey_date           TEXT NOT NULL,
            booked_at              TEXT NOT NULL,
            status                 TEXT NOT NULL CHECK (status IN ('CONFIRMED','RAC','WAITING','CANCELLED')),
            total_fare             REAL NOT NULL DEFAULT 0
        )""",
        // One row per ticket (a booking can hold several passengers). seat_id is UNIQUE, so the
        // database itself refuses to give one seat to two tickets.
        """
        CREATE TABLE IF NOT EXISTS booking_passengers (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            booking_id    INTEGER NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
            passenger_id  INTEGER NOT NULL REFERENCES passengers(passenger_id),
            seat_id       INTEGER UNIQUE REFERENCES seats(seat_id),
            ticket_id     TEXT NOT NULL UNIQUE,
            status        TEXT NOT NULL CHECK (status IN ('CONFIRMED','RAC','WAITING','CANCELLED')),
            fare          REAL NOT NULL DEFAULT 0,
            refund_amount REAL NOT NULL DEFAULT 0,
            cancelled_at  TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS waiting_list (
            waiting_id           INTEGER PRIMARY KEY AUTOINCREMENT,
            booking_id           INTEGER NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
            booking_passenger_id INTEGER NOT NULL UNIQUE REFERENCES booking_passengers(id) ON DELETE CASCADE,
            train_id             INTEGER NOT NULL REFERENCES trains(train_id) ON DELETE CASCADE,
            journey_date         TEXT NOT NULL,
            seat_class           TEXT NOT NULL,
            queue_type           TEXT NOT NULL CHECK (queue_type IN ('RAC','WL')),
            queue_position       INTEGER NOT NULL,
            status               TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PROMOTED','CANCELLED')),
            created_at           TEXT NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS idx_seats_lookup ON seats(train_id, journey_date, seat_class, status)",
        "CREATE INDEX IF NOT EXISTS idx_bookings_user ON bookings(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_bookings_train ON bookings(train_id, journey_date)",
        "CREATE INDEX IF NOT EXISTS idx_bp_booking ON booking_passengers(booking_id)",
        "CREATE INDEX IF NOT EXISTS idx_passengers_user ON passengers(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_waiting_queue ON waiting_list(train_id, journey_date, seat_class, queue_type, status, queue_position)"
    };

    /** Creates all tables and indexes if they do not exist yet. */
    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not initialise database at "
                    + DatabaseConnection.getDatabasePath() + ": " + e.getMessage(), e);
        }
    }
}
