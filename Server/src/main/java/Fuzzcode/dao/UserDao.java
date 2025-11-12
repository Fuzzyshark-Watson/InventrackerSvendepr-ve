package Fuzzcode.dao;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.model.AppUser;
import Fuzzcode.model.UserRole;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    /* =================== CREATE =================== */

    /** Inserts a user; expects already-hashed password and a salt (hashing should be done in a Service). */
    public int insertUser(String username, String passwordHash, String salt, UserRole role) {
        final String sql = """
            INSERT INTO Users (Username, PasswordHash, Salt, Role)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, role.name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return 0;
        }
    }

    /* =================== READ =================== */

    public AppUser findById(int userId) {
        final String sql = """
            SELECT UserID, Username, PasswordHash, Salt, Role, CreatedAt
            FROM Users
            WHERE UserID = ?
            """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return null;
        }
    }

    public AppUser findByUsername(String username) {
        final String sql = """
            SELECT UserID, Username, PasswordHash, Salt, Role, CreatedAt
            FROM Users
            WHERE Username = ?
            """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return null;
        }
    }

    public List<AppUser> listAll() {
        final String sql = """
            SELECT UserID, Username, PasswordHash, Salt, Role, CreatedAt
            FROM Users
            ORDER BY UserID
            """;
        List<AppUser> out = new ArrayList<>();
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return out;
    }

    /* =================== UPDATE =================== */

    /** Replace password hash + salt (e.g., after reset). */
    public boolean updatePassword(int userId, String newHash, String newSalt) {
        final String sql = """
            UPDATE Users
            SET PasswordHash = ?, Salt = ?
            WHERE UserID = ?
            """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setInt(3, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }

    public boolean updateRole(int userId, UserRole newRole) {
        final String sql = "UPDATE Users SET Role = ? WHERE UserID = ?";
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newRole.name());
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }

    public boolean updateUsername(int userId, String newUsername) {
        final String sql = "UPDATE Users SET Username = ? WHERE UserID = ?";
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }

    /* =================== DELETE =================== */

    /** Hard delete (since Users table has no Deleted flag). */
    public boolean delete(int userId) {
        final String sql = "DELETE FROM Users WHERE UserID = ?";
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }

    /* =================== Helper =================== */

    private static AppUser map(ResultSet rs) throws SQLException {
        return new AppUser(
                rs.getInt("UserID"),
                rs.getString("Username"),
                rs.getString("PasswordHash"),
                rs.getString("Salt"),
                UserRole.fromDb(rs.getString("Role")),
                rs.getTimestamp("CreatedAt").toInstant()
        );
    }
}
