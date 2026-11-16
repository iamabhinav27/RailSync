package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JDBC access to {@code bookings} and {@code booking_passengers} (the tickets of a booking).
 * The id is the PNR. A {@link Booking} is always loaded together with its {@link Ticket}s,
 * seats, travellers and queue positions.
 *
 * <p>Methods that take a {@link Connection} are building blocks used inside the booking and
 * cancellation transactions; the others open their own connection.</p>
 */
public class BookingRepository implements Repository<Booking, String> {

    private static final String HEADER_SELECT =
            "SELECT b.booking_id, b.pnr, b.seat_class, b.journey_date, b.booked_at, b.status, b.total_fare, "
                    + "t.train_number, t.train_name, u.username, "
                    + "ss.station_code AS src_station_code, ss.station_name AS src_station_name, ss.city AS src_city, "
                    + "ss.state AS src_state, ss.zone AS src_zone, ss.distance_marker_km AS src_distance_marker_km, "
                    + "ds.station_code AS dst_station_code, ds.station_name AS dst_station_name, ds.city AS dst_city, "
                    + "ds.state AS dst_state, ds.zone AS dst_zone, ds.distance_marker_km AS dst_distance_marker_km "
                    + "FROM bookings b "
                    + "JOIN trains t ON t.train_id = b.train_id "
                    + "JOIN stations ss ON ss.station_id = b.source_station_id "
                    + "JOIN stations ds ON ds.station_id = b.destination_station_id "
                    + "LEFT JOIN users u ON u.user_id = b.user_id ";

    private static final String TICKET_SELECT =
            "SELECT bp.id, bp.booking_id, bp.ticket_id, bp.status, bp.fare, bp.refund_amount, "
                    + "p.passenger_id, p.name, p.age, p.gender, p.phone, p.concession, p.berth_preference, "
                    + "s.seat_id, s.coach_code, s.seat_number, s.seat_class, s.berth_type, "
                    + "w.queue_type, w.queue_position "
                    + "FROM booking_passengers bp "
                    + "JOIN passengers p ON p.passenger_id = bp.passenger_id "
                    + "LEFT JOIN seats s ON s.seat_id = bp.seat_id "
                    + "LEFT JOIN waiting_list w ON w.booking_passenger_id = bp.id AND w.status = 'ACTIVE' ";

    private final UserRepository users;
    private final TrainRepository trains;
    private final StationRepository stations;
    private final PassengerRepository passengers;
    private final WaitingListRepository waitingList;

    public BookingRepository(UserRepository users, TrainRepository trains, StationRepository stations,
                             PassengerRepository passengers, WaitingListRepository waitingList) {
        this.users = users;
        this.trains = trains;
        this.stations = stations;
        this.passengers = passengers;
        this.waitingList = waitingList;
    }

    // ================================================================ SELECT

    @Override
    public Booking findById(String pnr) {
        if (pnr == null) return null;
        List<Booking> list = load("b.pnr = ?", pnr.trim().toUpperCase());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Booking> findAll() {
        return load(null, null);
    }

    /** Every booking indexed by PNR (HashMap) for quick lookups. */
    public Map<String, Booking> findAllAsMap() {
        Map<String, Booking> map = new HashMap<>();
        for (Booking b : findAll()) map.put(b.getPnr(), b);
        return map;
    }

    public List<Booking> findByUser(String username) {
        return load("u.username = ?", username == null ? "" : username.trim());
    }

    public List<Booking> findByTrain(String trainNumber) {
        return load("t.train_number = ?", trainNumber == null ? "" : trainNumber.trim());
    }

    public List<Booking> findByPassengerName(String partialName) {
        return load("b.booking_id IN (SELECT bp2.booking_id FROM booking_passengers bp2 "
                + "JOIN passengers p2 ON p2.passenger_id = bp2.passenger_id WHERE lower(p2.name) LIKE ?)",
                "%" + (partialName == null ? "" : partialName.trim().toLowerCase()) + "%");
    }

    public List<Booking> findByPhone(String partialPhone) {
        return load("b.booking_id IN (SELECT bp2.booking_id FROM booking_passengers bp2 "
                + "JOIN passengers p2 ON p2.passenger_id = bp2.passenger_id WHERE p2.phone LIKE ?)",
                "%" + (partialPhone == null ? "" : partialPhone.trim()) + "%");
    }

    public long count() {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bookings")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not count bookings", e);
        }
    }

    /** Bookings (any status) that still reference a train; a train with dependents cannot be deleted. */
    public int countByTrain(String trainNumber) {
        String sql = "SELECT COUNT(*) FROM bookings b JOIN trains t ON t.train_id = b.train_id WHERE t.train_number = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, trainNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not count bookings for train " + trainNumber, e);
        }
    }

    public boolean pnrExists(Connection c, String pnr) {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM bookings WHERE pnr = ?")) {
            ps.setString(1, pnr);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not check PNR " + pnr, e);
        }
    }

    private List<Booking> load(String filter, String param) {
        try (Connection c = DatabaseConnection.getConnection()) {
            return load(c, filter, param);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load bookings: " + e.getMessage(), e);
        }
    }

    /** Loads bookings matching a WHERE fragment on aliases b / t / u (null = all), with their tickets. */
    public List<Booking> load(Connection c, String filter, String param) throws SQLException {
        String where = filter == null ? "" : "WHERE " + filter + " ";
        Map<Long, Booking> byId = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(HEADER_SELECT + where + "ORDER BY b.booking_id DESC")) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long dbId = rs.getLong("booking_id");
                    String username = rs.getString("username");
                    Booking b = new Booking(
                            "BK" + (10000 + dbId), rs.getString("pnr"),
                            rs.getString("train_number"), rs.getString("train_name"),
                            StationRepository.map(rs, "src_"), StationRepository.map(rs, "dst_"),
                            LocalDate.parse(rs.getString("journey_date")),
                            SeatClass.valueOf(rs.getString("seat_class")),
                            username,
                            LocalDateTime.parse(rs.getString("booked_at")));
                    b.setDbId(dbId);
                    byId.put(dbId, b);
                }
            }
        }
        if (byId.isEmpty()) return new ArrayList<>();

        String ticketFilter = filter == null ? ""
                : "WHERE bp.booking_id IN (SELECT b.booking_id FROM bookings b "
                + "JOIN trains t ON t.train_id = b.train_id LEFT JOIN users u ON u.user_id = b.user_id WHERE " + filter + ") ";
        try (PreparedStatement ps = c.prepareStatement(TICKET_SELECT + ticketFilter + "ORDER BY bp.id")) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Booking b = byId.get(rs.getLong("booking_id"));
                    if (b != null) b.addTicket(mapTicket(rs, b));
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private Ticket mapTicket(ResultSet rs, Booking booking) throws SQLException {
        Seat seat = null;
        long seatId = rs.getLong("seat_id");
        if (!rs.wasNull()) {
            seat = new Seat(rs.getInt("seat_number"), rs.getString("coach_code"),
                    SeatClass.valueOf(rs.getString("seat_class")), BerthType.valueOf(rs.getString("berth_type")));
            seat.setSeatId(seatId);
            seat.setBooked(true);
        }
        int rac = 0;
        int wl = 0;
        String queueType = rs.getString("queue_type");
        if (queueType != null) {
            if ("RAC".equals(queueType)) rac = rs.getInt("queue_position"); else wl = rs.getInt("queue_position");
        }
        Ticket t = new Ticket(rs.getString("ticket_id"), booking.getPnr(), PassengerRepository.map(rs),
                booking.getSeatClass(), BookingStatus.fromDb(rs.getString("status")), seat, rac, wl,
                rs.getDouble("fare"));
        t.setDbId(rs.getLong("id"));
        t.setRefundAmount(rs.getDouble("refund_amount"));
        return t;
    }

    // ================================================================ INSERT

    /** INSERT a complete booking (header, travellers, tickets, queue rows) in one transaction. */
    @Override
    public void save(Booking booking) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Long userId = users.findDbId(c, booking.getBookedByUserId());
                long trainId = trains.findDbId(c, booking.getTrainNumber());
                long srcId = stations.findDbId(c, booking.getSource().getCode());
                long dstId = stations.findDbId(c, booking.getDestination().getCode());
                if (trainId < 0 || srcId < 0 || dstId < 0) {
                    throw new SQLException("Booking refers to an unknown train or station");
                }
                long bookingDbId = insertBooking(c, booking.getPnr(), userId, trainId, srcId, dstId,
                        booking.getSeatClass(), booking.getJourneyDate(), booking.getBookingTimestamp(),
                        booking.getStatus());
                booking.setDbId(bookingDbId);
                for (Ticket t : booking.getTickets()) {
                    long passengerId = passengers.findOrCreate(c, t.getPassenger(), userId);
                    Long seatId = t.getSeat() != null && t.getSeat().getSeatId() > 0 ? t.getSeat().getSeatId() : null;
                    long bpId = insertTicket(c, bookingDbId, passengerId, seatId, t.getTicketId(), t.getStatus(),
                            t.getIndividualFare());
                    t.setDbId(bpId);
                    if (t.getStatus() == BookingStatus.RAC) {
                        waitingList.insert(c, bookingDbId, bpId, trainId, booking.getJourneyDate(),
                                booking.getSeatClass(), WaitingEntry.QueueType.RAC, Math.max(1, t.getRacPosition()));
                    } else if (t.getStatus() == BookingStatus.WAITING) {
                        waitingList.insert(c, bookingDbId, bpId, trainId, booking.getJourneyDate(),
                                booking.getSeatClass(), WaitingEntry.QueueType.WL, Math.max(1, t.getWlPosition()));
                    }
                }
                refreshBookingStatus(c, bookingDbId);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save booking " + booking.getPnr() + ": " + e.getMessage(), e);
        }
    }

    public long insertBooking(Connection c, String pnr, Long userDbId, long trainId, long srcId, long dstId,
                              SeatClass cls, LocalDate date, LocalDateTime bookedAt, BookingStatus status)
            throws SQLException {
        String sql = "INSERT INTO bookings (pnr, user_id, train_id, source_station_id, destination_station_id, "
                + "seat_class, journey_date, booked_at, status, total_fare) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, pnr);
            if (userDbId == null) ps.setNull(2, Types.INTEGER); else ps.setLong(2, userDbId);
            ps.setLong(3, trainId);
            ps.setLong(4, srcId);
            ps.setLong(5, dstId);
            ps.setString(6, cls.name());
            ps.setString(7, date.toString());
            ps.setString(8, bookedAt.toString());
            ps.setString(9, status.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public long insertTicket(Connection c, long bookingDbId, long passengerDbId, Long seatId, String ticketId,
                             BookingStatus status, double fare) throws SQLException {
        String sql = "INSERT INTO booking_passengers (booking_id, passenger_id, seat_id, ticket_id, status, fare) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bookingDbId);
            ps.setLong(2, passengerDbId);
            if (seatId == null) ps.setNull(3, Types.INTEGER); else ps.setLong(3, seatId);
            ps.setString(4, ticketId);
            ps.setString(5, status.name());
            ps.setDouble(6, fare);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    // ================================================================ UPDATE

    /** UPDATE the booking row and each of its ticket rows (status, seat, fare, refund) in one transaction. */
    @Override
    public void update(Booking booking) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "UPDATE booking_passengers SET status = ?, seat_id = ?, fare = ?, refund_amount = ? WHERE id = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (Ticket t : booking.getTickets()) {
                        if (t.getDbId() <= 0) continue;
                        ps.setString(1, t.getStatus().name());
                        Seat seat = t.getSeat();
                        if (seat == null || seat.getSeatId() <= 0 || t.isCancelled()) ps.setNull(2, Types.INTEGER);
                        else ps.setLong(2, seat.getSeatId());
                        ps.setDouble(3, t.getIndividualFare());
                        ps.setDouble(4, t.getRefundAmount());
                        ps.setLong(5, t.getDbId());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                refreshBookingStatus(c, booking.getDbId());
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not update booking " + booking.getPnr() + ": " + e.getMessage(), e);
        }
    }

    /** Marks one ticket cancelled: status, refund, timestamp, and releases its seat link. */
    public void markTicketCancelled(Connection c, long bookingPassengerId, double refund) throws SQLException {
        String sql = "UPDATE booking_passengers SET status = 'CANCELLED', seat_id = NULL, refund_amount = ?, cancelled_at = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, refund);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setLong(3, bookingPassengerId);
            ps.executeUpdate();
        }
    }

    public void setTicketStatus(Connection c, long bookingPassengerId, BookingStatus status, Long seatId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE booking_passengers SET status = ?, seat_id = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            if (seatId == null) ps.setNull(2, Types.INTEGER); else ps.setLong(2, seatId);
            ps.setLong(3, bookingPassengerId);
            ps.executeUpdate();
        }
    }

    /** Recomputes the booking's total fare and overall status from its tickets. */
    public void refreshBookingStatus(Connection c, long bookingDbId) throws SQLException {
        String sql = "UPDATE bookings SET "
                + "total_fare = (SELECT COALESCE(SUM(fare), 0) FROM booking_passengers WHERE booking_id = bookings.booking_id), "
                + "status = CASE "
                + "WHEN NOT EXISTS (SELECT 1 FROM booking_passengers WHERE booking_id = bookings.booking_id AND status <> 'CANCELLED') THEN 'CANCELLED' "
                + "WHEN EXISTS (SELECT 1 FROM booking_passengers WHERE booking_id = bookings.booking_id AND status = 'CONFIRMED') THEN 'CONFIRMED' "
                + "WHEN EXISTS (SELECT 1 FROM booking_passengers WHERE booking_id = bookings.booking_id AND status = 'RAC') THEN 'RAC' "
                + "ELSE 'WAITING' END "
                + "WHERE booking_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, bookingDbId);
            ps.executeUpdate();
        }
    }

    // ================================================================ DELETE

    /** DELETE a booking by PNR, freeing any seats it still holds. Tickets and queue rows cascade. */
    @Override
    public void delete(String pnr) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE seats SET status = 'AVAILABLE' WHERE seat_id IN (SELECT bp.seat_id FROM booking_passengers bp "
                                + "JOIN bookings b ON b.booking_id = bp.booking_id WHERE b.pnr = ? AND bp.seat_id IS NOT NULL)")) {
                    ps.setString(1, pnr);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bookings WHERE pnr = ?")) {
                    ps.setString(1, pnr);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete booking " + pnr, e);
        }
    }
}
