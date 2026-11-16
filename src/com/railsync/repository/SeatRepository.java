package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to the per-date seat inventory in the {@code seats} table.
 *
 * <p>Seats for a train and date are created the first time they are needed. A seat is claimed with
 * {@code UPDATE ... SET status='BOOKED' WHERE seat_id=? AND status='AVAILABLE'}; the update only
 * succeeds for one caller, so even without any other locking a seat cannot be sold twice.</p>
 */
public class SeatRepository {

    /** Creates the seat rows for this train and date if they are missing (INSERT OR IGNORE). */
    public void ensureSeats(Connection c, Train train, long trainId, LocalDate date) throws SQLException {
        int expected = 0;
        for (Coach coach : train.getCoaches()) expected += coach.getTotalConfirmedCapacity();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM seats WHERE train_id = ? AND journey_date = ?")) {
            ps.setLong(1, trainId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= expected) return;
            }
        }
        String sql = "INSERT OR IGNORE INTO seats (train_id, journey_date, coach_code, seat_number, seat_class, berth_type) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Coach coach : train.getCoaches()) {
                for (Seat seat : coach.getSeats()) {
                    ps.setLong(1, trainId);
                    ps.setString(2, date.toString());
                    ps.setString(3, coach.getCoachId());
                    ps.setInt(4, seat.getSeatNumber());
                    ps.setString(5, coach.getSeatClass().name());
                    ps.setString(6, seat.getBerthType().name());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    /**
     * Number of free seats. When the seat rows for that date have not been generated yet,
     * every seat is free, so {@code capacityIfUntouched} is returned.
     */
    public int countAvailable(long trainId, LocalDate date, SeatClass cls, int capacityIfUntouched) {
        try (Connection c = DatabaseConnection.getConnection()) {
            return countAvailable(c, trainId, date, cls, capacityIfUntouched);
        } catch (SQLException e) {
            throw new DatabaseException("Could not count available seats", e);
        }
    }

    public int countAvailable(Connection c, long trainId, LocalDate date, SeatClass cls, int capacityIfUntouched)
            throws SQLException {
        String sql = "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS free "
                + "FROM seats WHERE train_id = ? AND journey_date = ? AND seat_class = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trainId);
            ps.setString(2, date.toString());
            ps.setString(3, cls.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("total") == 0 ? capacityIfUntouched : rs.getInt("free");
            }
        }
    }

    /**
     * Claims the best free seat for the class, preferring the requested berth type.
     *
     * @return the booked seat, or {@code null} when the class is sold out
     */
    public Seat claimNextSeat(Connection c, long trainId, LocalDate date, SeatClass cls, BerthType preferred)
            throws SQLException {
        String select = "SELECT seat_id, coach_code, seat_number, seat_class, berth_type FROM seats "
                + "WHERE train_id = ? AND journey_date = ? AND seat_class = ? AND status = 'AVAILABLE' "
                + "ORDER BY (berth_type = ?) DESC, coach_code, seat_number LIMIT 1";
        String claim = "UPDATE seats SET status = 'BOOKED' WHERE seat_id = ? AND status = 'AVAILABLE'";
        for (int attempt = 0; attempt < 5; attempt++) {
            Seat candidate = null;
            try (PreparedStatement ps = c.prepareStatement(select)) {
                ps.setLong(1, trainId);
                ps.setString(2, date.toString());
                ps.setString(3, cls.name());
                ps.setString(4, preferred != null ? preferred.name() : "");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) candidate = map(rs);
                }
            }
            if (candidate == null) return null;
            try (PreparedStatement ps = c.prepareStatement(claim)) {
                ps.setLong(1, candidate.getSeatId());
                if (ps.executeUpdate() == 1) {          // exactly one caller can win this update
                    candidate.setBooked(true);
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Marks a seat free again (cancellation). */
    public void release(Connection c, long seatId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE seats SET status = 'AVAILABLE' WHERE seat_id = ?")) {
            ps.setLong(1, seatId);
            ps.executeUpdate();
        }
    }

    public Seat findById(long seatId) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT seat_id, coach_code, seat_number, seat_class, berth_type, status FROM seats WHERE seat_id = ?")) {
            ps.setLong(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Seat s = map(rs);
                s.setBooked("BOOKED".equals(rs.getString("status")));
                return s;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load seat " + seatId, e);
        }
    }

    public Seat findById(Connection c, long seatId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seat_id, coach_code, seat_number, seat_class, berth_type FROM seats WHERE seat_id = ?")) {
            ps.setLong(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Booked / total seat rows that exist (used for the occupancy figure in reports). */
    public int[] occupancy() {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), COALESCE(SUM(CASE WHEN status = 'BOOKED' THEN 1 ELSE 0 END), 0) FROM seats")) {
            rs.next();
            return new int[]{rs.getInt(2), rs.getInt(1)};
        } catch (SQLException e) {
            throw new DatabaseException("Could not compute occupancy", e);
        }
    }

    /**
     * Data-integrity check: seat ids held by more than one live ticket. Must always be empty.
     */
    public List<Long> findDoubleAllocatedSeats() {
        List<Long> bad = new ArrayList<>();
        String sql = "SELECT seat_id FROM booking_passengers WHERE seat_id IS NOT NULL AND status <> 'CANCELLED' "
                + "GROUP BY seat_id HAVING COUNT(*) > 1";
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getLong(1));
        } catch (SQLException e) {
            throw new DatabaseException("Could not verify seat allocation", e);
        }
        return bad;
    }

    private static Seat map(ResultSet rs) throws SQLException {
        Seat s = new Seat(rs.getInt("seat_number"), rs.getString("coach_code"),
                SeatClass.valueOf(rs.getString("seat_class")), BerthType.valueOf(rs.getString("berth_type")));
        s.setSeatId(rs.getLong("seat_id"));
        return s;
    }
}
