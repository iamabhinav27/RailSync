package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.*;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/**
 * JDBC access to {@code trains}, {@code train_routes} and {@code train_coaches}.
 * The id is the train number (e.g. "12952"). Trains come back as the correct subclass
 * ({@link RajdhaniExpress}, {@link VandeBharatExpress}, ...) through {@link TrainFactory}.
 */
public class TrainRepository implements Repository<Train, String> {

    private final StationRepository stations;

    public TrainRepository(StationRepository stations) {
        this.stations = stations;
    }

    // ---------------------------------------------------------------- SELECT

    @Override
    public Train findById(String trainNumber) {
        if (trainNumber == null) return null;
        List<Train> list = load(trainNumber.trim());
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Train> findAll() {
        return load(null);
    }

    public long count() {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM trains")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not count trains", e);
        }
    }

    /** Database id of a train number, or -1 when unknown. */
    public long findDbId(Connection c, String trainNumber) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT train_id FROM trains WHERE train_number = ?")) {
            ps.setString(1, trainNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public long findDbId(String trainNumber) {
        try (Connection c = DatabaseConnection.getConnection()) {
            return findDbId(c, trainNumber);
        } catch (SQLException e) {
            throw new DatabaseException("Could not look up train " + trainNumber, e);
        }
    }

    private List<Train> load(String trainNumber) {
        try (Connection c = DatabaseConnection.getConnection()) {
            Map<Long, Station> stationById = stations.findAllById(c);
            String where = trainNumber == null ? "" : " WHERE train_number = ?";
            String sub = trainNumber == null ? ""
                    : " WHERE train_id IN (SELECT train_id FROM trains WHERE train_number = ?)";

            Map<Long, Train> trains = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM trains" + where + " ORDER BY train_number")) {
                if (trainNumber != null) ps.setString(1, trainNumber);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Train t = TrainFactory.create(
                                rs.getString("train_type"),
                                rs.getString("train_number"),
                                rs.getString("train_name"),
                                stationById.get(rs.getLong("source_station_id")),
                                stationById.get(rs.getLong("destination_station_id")),
                                LocalTime.parse(rs.getString("departure_time")),
                                LocalTime.parse(rs.getString("arrival_time")),
                                rs.getDouble("distance_km"));
                        t.setBaseFareRatePerKm(rs.getDouble("base_fare_rate"));
                        trains.put(rs.getLong("train_id"), t);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT train_id, station_id FROM train_routes" + sub + " ORDER BY train_id, sequence_number")) {
                if (trainNumber != null) ps.setString(1, trainNumber);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Train t = trains.get(rs.getLong("train_id"));
                        Station s = stationById.get(rs.getLong("station_id"));
                        if (t != null && !s.equals(t.getSource()) && !s.equals(t.getDestination())) {
                            t.addIntermediateStation(s);
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT train_id, coach_code, seat_class, total_seats, rac_capacity, wl_capacity FROM train_coaches"
                            + sub + " ORDER BY coach_id")) {
                if (trainNumber != null) ps.setString(1, trainNumber);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Train t = trains.get(rs.getLong("train_id"));
                        if (t != null) {
                            t.addCoach(new Coach(rs.getString("coach_code"),
                                    SeatClass.valueOf(rs.getString("seat_class")),
                                    rs.getInt("total_seats"), rs.getInt("rac_capacity"), rs.getInt("wl_capacity")));
                        }
                    }
                }
            }
            return new ArrayList<>(trains.values());
        } catch (SQLException e) {
            throw new DatabaseException("Could not load trains: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- INSERT

    /** INSERT a train with its route stops and coaches in one transaction (commit / rollback). */
    @Override
    public void save(Train train) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                long srcId = requireStation(c, train.getSource());
                long dstId = requireStation(c, train.getDestination());
                long trainId;
                String sql = "INSERT INTO trains (train_number, train_name, train_type, source_station_id, "
                        + "destination_station_id, departure_time, arrival_time, distance_km, base_fare_rate) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, train.getTrainNumber());
                    ps.setString(2, train.getTrainName());
                    ps.setString(3, train.getTrainType());
                    ps.setLong(4, srcId);
                    ps.setLong(5, dstId);
                    ps.setString(6, train.getDepartureTime().toString());
                    ps.setString(7, train.getArrivalTime().toString());
                    ps.setDouble(8, train.getDistanceKm());
                    ps.setDouble(9, train.getBaseFareRatePerKm());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        trainId = keys.getLong(1);
                    }
                }
                insertRoute(c, trainId, train);
                String coachSql = "INSERT INTO train_coaches (train_id, coach_code, seat_class, total_seats, rac_capacity, wl_capacity) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(coachSql)) {
                    for (Coach coach : train.getCoaches()) {
                        ps.setLong(1, trainId);
                        ps.setString(2, coach.getCoachId());
                        ps.setString(3, coach.getSeatClass().name());
                        ps.setInt(4, coach.getTotalConfirmedCapacity());
                        ps.setInt(5, coach.getRacCapacity());
                        ps.setInt(6, coach.getWaitingListCapacity());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save train " + train.getTrainNumber() + ": " + e.getMessage(), e);
        }
    }

    private void insertRoute(Connection c, long trainId, Train train) throws SQLException {
        String sql = "INSERT INTO train_routes (train_id, station_id, sequence_number, arrival_time, departure_time) VALUES (?, ?, ?, ?, ?)";
        List<Station> route = train.getFullRouteStations();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < route.size(); i++) {
                Station s = route.get(i);
                String time = train.estimatedTimeAt(s).toString();
                ps.setLong(1, trainId);
                ps.setLong(2, requireStation(c, s));
                ps.setInt(3, i + 1);
                if (i == 0) ps.setNull(4, Types.VARCHAR); else ps.setString(4, time);
                if (i == route.size() - 1) ps.setNull(5, Types.VARCHAR); else ps.setString(5, time);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private long requireStation(Connection c, Station s) throws SQLException {
        long id = stations.findDbId(c, s.getCode());
        if (id < 0) throw new SQLException("Unknown station " + s.getCode());
        return id;
    }

    // ---------------------------------------------------------------- UPDATE

    /** UPDATE train details (name, type, timings, distance, fare rate) and refresh route times. */
    @Override
    public void update(Train train) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                long trainId = findDbId(c, train.getTrainNumber());
                if (trainId < 0) throw new SQLException("Train " + train.getTrainNumber() + " does not exist");
                String sql = "UPDATE trains SET train_name=?, train_type=?, departure_time=?, arrival_time=?, "
                        + "distance_km=?, base_fare_rate=? WHERE train_id=?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, train.getTrainName());
                    ps.setString(2, train.getTrainType());
                    ps.setString(3, train.getDepartureTime().toString());
                    ps.setString(4, train.getArrivalTime().toString());
                    ps.setDouble(5, train.getDistanceKm());
                    ps.setDouble(6, train.getBaseFareRatePerKm());
                    ps.setLong(7, trainId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM train_routes WHERE train_id = ?")) {
                    ps.setLong(1, trainId);
                    ps.executeUpdate();
                }
                insertRoute(c, trainId, train);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not update train " + train.getTrainNumber() + ": " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- DELETE

    /**
     * DELETE a train (its routes, coaches and seat inventory go with it). The foreign key from
     * {@code bookings} refuses the delete when bookings still reference the train.
     */
    @Override
    public void delete(String trainNumber) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM trains WHERE train_number = ?")) {
            ps.setString(1, trainNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete train " + trainNumber + ": " + e.getMessage(), e);
        }
    }
}
