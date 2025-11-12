package Fuzzcode.dao;

import Fuzzcode.model.Position;
import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.model.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDao {
    private Item mapItem(ResultSet rs) throws SQLException {
        return new Item(
                rs.getInt("ItemID"),
                rs.getString("TagID"),
                Position.fromString(rs.getString("Position")),
                (Boolean) rs.getObject("IsOverdue"),
                rs.getBoolean("Deleted")
        );
    }
    public int createItem(String tagId, Position position, boolean overdue) {
        String sql = """
            INSERT INTO Items (TagID, Position, IsOverdue, Deleted)
            VALUES (?, ?, ?, FALSE)
        """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tagId);
            if (position == null) ps.setString(2, String.valueOf(Position.HOME));
            else ps.setString(2, position.name());
            ps.setBoolean(3, overdue);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return 0;
        }
    }
    public Item readItemById(int itemId, boolean includeDeleted) {
        String sql = """
        SELECT ItemID, TagID, Position, IsOverdue, Deleted
        FROM Items
        WHERE ItemID = ?
    """ + (includeDeleted ? "" : " AND Deleted = FALSE");

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, itemId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapItem(rs) : null;
            }

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return null;
        }
    }
    public Item readItemByTag(String tagId, boolean includeDeleted) {
        String sql = """
        SELECT ItemID, TagID, Position, IsOverdue, Deleted
        FROM Items
        WHERE TagID = ?
    """ + (includeDeleted ? "" : " AND Deleted = FALSE");

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, tagId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapItem(rs) : null;
            }

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return null;
        }
    }
    private Item readItem(String sql, SqlConsumer setter) {
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            setter.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return null;
        }
    }
    public boolean updateTag(String tagId, int itemId) {
        String sql = "UPDATE Items SET TagID = ? WHERE ItemID = ? AND Deleted = FALSE";

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tagId);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public boolean updateOverdue(int itemId, boolean overdue) {
        String sql = "UPDATE Items SET IsOverdue = ? WHERE ItemID = ? AND Deleted = FALSE";

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, overdue);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public boolean updatePosition(int itemId, Position position) {
        if (position == null) return false; // avoid NPE / NOT NULL violation
        try (var c = ConnectionManager.getConnection();
             var ps = c.prepareStatement("""
             UPDATE Items
                SET Position = ?
              WHERE ItemID = ? AND Deleted = FALSE
         """)) {
            ps.setString(1, position.name());
            ps.setInt(2, itemId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false; // or wrap to RuntimeException if that's your convention
        }
    }
    public boolean softDelete(int itemId) {
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE Items SET Deleted = TRUE WHERE ItemID = ? AND Deleted = FALSE")) {
            ps.setInt(1, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public List<Item> listAll(boolean includeDeleted) {
        List<Item> out = new ArrayList<>();
        String sql = "SELECT * FROM Items" + (includeDeleted ? "" : " WHERE Deleted = FALSE");

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) out.add(map(rs));

        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return out;
    }
    private Item map(ResultSet rs) throws SQLException {
        return new Item(
                rs.getInt("ItemID"),
                rs.getString("TagID"),
                Position.fromString(rs.getString("Position")),
                rs.getBoolean("IsOverdue"),
                rs.getBoolean("Deleted")
        );
    }
    private interface SqlConsumer { void accept(PreparedStatement ps) throws SQLException; }
}
