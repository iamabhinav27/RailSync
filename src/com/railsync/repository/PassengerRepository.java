package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.BerthType;
import com.railsync.model.Passenger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** JDBC access to the {@code passengers} table (the travellers named on tickets). Id = passenger_id. */
public class PassengerRepository implements Repository<Passenger, Long> {

    private static final String COLUMNS =
            "passenger_id, name, age, gender, phone, concession, berth_preference";

    @Override
    public Passenger findById(Long id) {
        List<Passenger> list = query("SELECT " + COLUMNS + " FROM passengers WHERE passenger_id = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Passenger> findAll() {
        return query("SELECT " + COLUMNS + " FROM passengers ORDER BY passenger_id", null);
    }

    /** Travellers a user has saved before (lets the booking form offer them again). */
    public List<Passenger> findByUser(long userDbId) {
        return query("SELECT " + COLUMNS + " FROM passengers WHERE user_id = ? ORDER BY name", userDbId);
    }

    private List<Passenger> query(String sql, Long param) {
        List<Passenger> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load passengers", e);
        }
        return list;
    }

    /** INSERT a passenger that is not linked to any user account. */
    @Override
    public void save(Passenger p) {
        try (Connection c = DatabaseConnection.getConnection()) {
            p.setPassengerId(insert(c, p, null));
        } catch (SQLException e) {
            throw new DatabaseException("Could not save passenger " + p.getName(), e);
        }
    }

    @Override
    public void update(Passenger p) {
        String sql = "UPDATE passengers SET name=?, age=?, gender=?, phone=?, concession=?, berth_preference=? WHERE passenger_id=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setInt(2, p.getAge());
            ps.setString(3, p.getGender());
            ps.setString(4, p.getPhone());
            ps.setString(5, p.getConcession().name());
            ps.setString(6, p.getBerthPreference() != null ? p.getBerthPreference().name() : null);
            ps.setLong(7, p.getPassengerId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update passenger " + p.getName(), e);
        }
    }

    @Override
    public void delete(Long id) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM passengers WHERE passenger_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete passenger " + id + " (it may be on a ticket)", e);
        }
    }

    /**
     * Returns the id of an identical saved traveller for this user, creating the row if needed.
     * Runs on the caller's connection so it is part of the booking transaction.
     */
    public long findOrCreate(Connection c, Passenger p, Long userDbId) throws SQLException {
        String sql = "SELECT passenger_id FROM passengers WHERE "
                + (userDbId == null ? "user_id IS NULL" : "user_id = ?")
                + " AND lower(name) = lower(?) AND age = ? AND gender = ? AND phone = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (userDbId != null) ps.setLong(i++, userDbId);
            ps.setString(i++, p.getName());
            ps.setInt(i++, p.getAge());
            ps.setString(i++, p.getGender());
            ps.setString(i, p.getPhone());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    p.setPassengerId(id);
                    return id;
                }
            }
        }
        long id = insert(c, p, userDbId);
        p.setPassengerId(id);
        return id;
    }

    private long insert(Connection c, Passenger p, Long userDbId) throws SQLException {
        String sql = "INSERT INTO passengers (user_id, name, age, gender, phone, concession, berth_preference) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (userDbId == null) ps.setNull(1, Types.INTEGER); else ps.setLong(1, userDbId);
            ps.setString(2, p.getName());
            ps.setInt(3, p.getAge());
            ps.setString(4, p.getGender());
            ps.setString(5, p.getPhone());
            ps.setString(6, p.getConcession().name());
            ps.setString(7, p.getBerthPreference() != null ? p.getBerthPreference().name() : null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static Passenger map(ResultSet rs) throws SQLException {
        String berth = rs.getString("berth_preference");
        Passenger p = new Passenger(
                rs.getString("name"),
                rs.getInt("age"),
                rs.getString("gender"),
                rs.getString("phone"),
                Passenger.Concession.valueOf(rs.getString("concession")),
                berth != null ? BerthType.valueOf(berth) : null);
        p.setPassengerId(rs.getLong("passenger_id"));
        return p;
    }
}
