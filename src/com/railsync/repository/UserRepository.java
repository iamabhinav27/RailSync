package com.railsync.repository;

import com.railsync.database.DatabaseConnection;
import com.railsync.exception.DatabaseException;
import com.railsync.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** JDBC access to the {@code users} table. The id is the (case-insensitive) username. */
public class UserRepository implements Repository<User, String> {

    private static final String COLUMNS = "user_id, username, name, email, phone, password_hash, role";

    @Override
    public User findById(String username) {
        if (username == null) return null;
        String sql = "SELECT " + COLUMNS + " FROM users WHERE username = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load user " + username, e);
        }
    }

    @Override
    public List<User> findAll() {
        return query("SELECT " + COLUMNS + " FROM users ORDER BY user_id", null);
    }

    /** All users with the PASSENGER role (administrator "view passenger information"). */
    public List<User> findPassengers() {
        return query("SELECT " + COLUMNS + " FROM users WHERE role = ? ORDER BY name", User.Role.PASSENGER.name());
    }

    private List<User> query(String sql, String param) {
        List<User> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load users", e);
        }
        return list;
    }

    /** INSERT a new user. {@code user.getPasswordHash()} must already be a salted hash. */
    @Override
    public void save(User user) {
        String sql = "INSERT INTO users (username, name, email, phone, password_hash, role) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getPasswordHash());
            ps.setString(6, user.getRole().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save user " + user.getUsername() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE users SET name=?, email=?, phone=?, password_hash=? WHERE username=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getUsername());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update user " + user.getUsername(), e);
        }
    }

    @Override
    public void delete(String username) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete user " + username, e);
        }
    }

    public boolean existsByUsername(String username) {
        return findById(username) != null;
    }

    public long count() {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not count users", e);
        }
    }

    /** Database id for a username inside a transaction, or {@code null} when it is not a registered user. */
    public Long findDbId(Connection c, String username) throws SQLException {
        if (username == null) return null;
        try (PreparedStatement ps = c.prepareStatement("SELECT user_id FROM users WHERE username = ?")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return User.create(
                String.valueOf(rs.getLong("user_id")),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                User.Role.valueOf(rs.getString("role")));
    }
}
