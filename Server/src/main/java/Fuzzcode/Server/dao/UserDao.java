package Fuzzcode.Server.dao;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.model.AppUser;
import Fuzzcode.Server.model.UserRole;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {
    private static final String BASE_SELECT = """
        SELECT UserID, Username, PasswordHash, Salt, Role, CreatedAt
        FROM Users
        WHERE Deleted = FALSE
        """;
    /* === UPDATE =================================== */
    public AppUser findByUsername(String username) {
        String sql = BASE_SELECT + " AND Username = ?";
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public AppUser findById(int id) {
        String sql = BASE_SELECT + " AND UserID = ?";

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int createUser(String username, String hash, String salt, UserRole role) {
        String sql = """
                INSERT INTO Users
                (Username, PasswordHash, Salt, Role, CreatedAt, Deleted)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(), FALSE)
                """;

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role.name());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("User insert did not return generated key");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
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
    /* === UPDATE =================================== */
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
    /* === DELETE =================================== */
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
    /* === Helper =================================== */
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
