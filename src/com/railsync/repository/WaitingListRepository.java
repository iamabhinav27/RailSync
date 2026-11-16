package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.SeatClass;
import com.railsync.model.WaitingEntry;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** JDBC access to the {@code waiting_list} table (RAC and WL queues per train, date and class). */
public class WaitingListRepository {

    private static final String SELECT =
            "SELECT w.waiting_id, w.booking_passenger_id, w.booking_id, b.pnr, bp.ticket_id, p.name AS passenger_name, "
                    + "t.train_number, w.journey_date, w.seat_class, w.queue_type, w.queue_position "
                    + "FROM waiting_list w "
                    + "JOIN bookings b ON b.booking_id = w.booking_id "
                    + "JOIN booking_passengers bp ON bp.id = w.booking_passenger_id "
                    + "JOIN passengers p ON p.passenger_id = bp.passenger_id "
                    + "JOIN trains t ON t.train_id = w.train_id ";

    public int count(Connection c, long trainId, LocalDate date, SeatClass cls, WaitingEntry.QueueType type)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM waiting_list WHERE train_id = ? AND journey_date = ? AND seat_class = ? "
                + "AND queue_type = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trainId);
            ps.setString(2, date.toString());
            ps.setString(3, cls.name());
            ps.setString(4, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int count(long trainId, LocalDate date, SeatClass cls, WaitingEntry.QueueType type) {
        try (Connection c = DatabaseConnection.getConnection()) {
            return count(c, trainId, date, cls, type);
        } catch (SQLException e) {
            throw new DatabaseException("Could not count queue entries", e);
        }
    }

    public void insert(Connection c, long bookingDbId, long bookingPassengerId, long trainId, LocalDate date,
                       SeatClass cls, WaitingEntry.QueueType type, int position) throws SQLException {
        String sql = "INSERT INTO waiting_list (booking_id, booking_passenger_id, train_id, journey_date, seat_class, "
                + "queue_type, queue_position, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, bookingDbId);
            ps.setLong(2, bookingPassengerId);
            ps.setLong(3, trainId);
            ps.setString(4, date.toString());
            ps.setString(5, cls.name());
            ps.setString(6, type.name());
            ps.setInt(7, position);
            ps.setString(8, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    /** Loads one queue, head first, as a {@link LinkedList} (FIFO). */
    public LinkedList<WaitingEntry> loadQueue(Connection c, long trainId, LocalDate date, SeatClass cls,
                                               WaitingEntry.QueueType type) throws SQLException {
        String sql = SELECT + "WHERE w.train_id = ? AND w.journey_date = ? AND w.seat_class = ? AND w.queue_type = ? "
                + "AND w.status = 'ACTIVE' ORDER BY w.queue_position, w.waiting_id";
        LinkedList<WaitingEntry> queue = new LinkedList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trainId);
            ps.setString(2, date.toString());
            ps.setString(3, cls.name());
            ps.setString(4, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) queue.add(map(rs));
            }
        }
        return queue;
    }

    /** Every active queue entry (administrator "view waiting lists"). */
    public List<WaitingEntry> findAllActive() {
        String sql = SELECT + "WHERE w.status = 'ACTIVE' ORDER BY t.train_number, w.journey_date, w.seat_class, w.queue_type DESC, w.queue_position";
        List<WaitingEntry> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            throw new DatabaseException("Could not load waiting lists", e);
        }
        return list;
    }

    public void setPositionAndType(Connection c, long waitingId, WaitingEntry.QueueType type, int position)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE waiting_list SET queue_type = ?, queue_position = ? WHERE waiting_id = ?")) {
            ps.setString(1, type.name());
            ps.setInt(2, position);
            ps.setLong(3, waitingId);
            ps.executeUpdate();
        }
    }

    public void setStatus(Connection c, long waitingId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE waiting_list SET status = ? WHERE waiting_id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, waitingId);
            ps.executeUpdate();
        }
    }

    /** Marks the queue row of a ticket as cancelled (no-op when the ticket was not queued). */
    public void cancelForTicket(Connection c, long bookingPassengerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE waiting_list SET status = 'CANCELLED' WHERE booking_passenger_id = ? AND status = 'ACTIVE'")) {
            ps.setLong(1, bookingPassengerId);
            ps.executeUpdate();
        }
    }

    private static WaitingEntry map(ResultSet rs) throws SQLException {
        return new WaitingEntry(
                rs.getLong("waiting_id"), rs.getLong("booking_passenger_id"), rs.getLong("booking_id"),
                rs.getString("pnr"), rs.getString("ticket_id"), rs.getString("passenger_name"),
                rs.getString("train_number"), LocalDate.parse(rs.getString("journey_date")),
                SeatClass.valueOf(rs.getString("seat_class")),
                WaitingEntry.QueueType.valueOf(rs.getString("queue_type")), rs.getInt("queue_position"));
    }
}
