package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.Station;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC access to the {@code stations} table. The id is the station code (e.g. "NDLS"). */
public class StationRepository implements Repository<Station, String> {

    private static final String COLUMNS =
            "station_code, station_name, city, state, zone, distance_marker_km";

    @Override
    public Station findById(String code) {
        String sql = "SELECT " + COLUMNS + " FROM stations WHERE station_code = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code == null ? "" : code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load station " + code, e);
        }
    }

    @Override
    public List<Station> findAll() {
        List<Station> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM stations ORDER BY station_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            throw new DatabaseException("Could not load stations", e);
        }
        return list;
    }

    @Override
    public void save(Station s) {
        String sql = "INSERT INTO stations (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getCode());
            ps.setString(2, s.getName());
            ps.setString(3, s.getCity());
            ps.setString(4, s.getState());
            ps.setString(5, s.getZone());
            ps.setDouble(6, s.getDistanceMarkerKm());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save station " + s.getCode() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Station s) {
        String sql = "UPDATE stations SET station_name=?, city=?, state=?, zone=?, distance_marker_km=? WHERE station_code=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getCity());
            ps.setString(3, s.getState());
            ps.setString(4, s.getZone());
            ps.setDouble(5, s.getDistanceMarkerKm());
            ps.setString(6, s.getCode());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update station " + s.getCode(), e);
        }
    }

    @Override
    public void delete(String code) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM stations WHERE station_code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete station " + code + " (it may be used by a train)", e);
        }
    }

    public long count() {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stations")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not count stations", e);
        }
    }

    /** Station database id by code, or -1 when unknown (used inside transactions). */
    public long findDbId(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT station_id FROM stations WHERE station_code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /** All stations keyed by database id (used to rebuild trains). */
    public Map<Long, Station> findAllById(Connection c) throws SQLException {
        Map<Long, Station> map = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT station_id, " + COLUMNS + " FROM stations");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getLong("station_id"), map(rs));
            }
        }
        return map;
    }

    static Station map(ResultSet rs) throws SQLException {
        return map(rs, "");
    }

    /** Maps a station from columns with an optional alias prefix (e.g. "src_"). */
    static Station map(ResultSet rs, String prefix) throws SQLException {
        return new Station(
                rs.getString(prefix + "station_code"),
                rs.getString(prefix + "station_name"),
                rs.getString(prefix + "city"),
                rs.getString(prefix + "state"),
                rs.getString(prefix + "zone"),
                rs.getDouble(prefix + "distance_marker_km"));
    }
}
