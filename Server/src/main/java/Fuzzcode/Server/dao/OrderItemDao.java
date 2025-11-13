package Fuzzcode.Server.dao;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.model.OrderItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderItemDao {




    /* ---------- Convenience (auto-connection) ---------- */
    public OrderItem attach(int orderId, int itemId) {
        try (var c = ConnectionManager.getConnection()) {
            return attach(c, orderId, itemId);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            throw new RuntimeException(e);
        }
    }
    public boolean detach(int orderId, int itemId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return detach(c, orderId, itemId);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public boolean isAttached(int orderId, int itemId, boolean includeDeleted) {
        try (Connection c = ConnectionManager.getConnection()) {
            return isAttached(c, orderId, itemId, includeDeleted);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public int countActiveItems(int orderId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return countActiveItems(c, orderId);
        } catch (SQLException e) { LoggerHandler.log(e); return 0; }
    }
    public List<OrderItem> listByOrder(int orderId, boolean includeDeleted) {
        try (Connection c = ConnectionManager.getConnection()) {
            return listByOrder(c, orderId, includeDeleted);
        } catch (SQLException e) { LoggerHandler.log(e); return List.of(); }
    }
    public List<OrderItem> listAll(boolean includeDeleted) {
        try (Connection c = ConnectionManager.getConnection()) {
            return listAll(c, includeDeleted);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return List.of();
        }
    }

    /* ---------- Connection-taking ---------- */
    public OrderItem attach(Connection c, int orderId, int itemId) throws SQLException {
        // 1) Forbid two ACTIVE orders for the same item
        try (var chk = c.prepareStatement("""
        SELECT 1 FROM OrderItems WHERE ItemID=? AND Deleted=FALSE LIMIT 1
    """)) {
            chk.setInt(1, itemId);
            try (var rs = chk.executeQuery()) {
                if (rs.next()) throw new IllegalStateException("Item already attached to an active order");
            }
        }
        // 2) Revive if same OrderID has a soft-deleted relation
        try (var up = c.prepareStatement("""
        UPDATE OrderItems SET Deleted=FALSE
         WHERE OrderID=? AND ItemID=? AND Deleted=TRUE
    """)) {
            up.setInt(1, orderId);
            up.setInt(2, itemId);
            if (up.executeUpdate() == 1) {
                return readOne(c, orderId, itemId, true); // tiny private helper
            }
        }
        // 3) Insert fresh (PK guards duplicates in same order)
        try (var ins = c.prepareStatement("""
        INSERT INTO OrderItems(OrderID, ItemID, Deleted) VALUES (?,?,FALSE)
    """)) {
            ins.setInt(1, orderId);
            ins.setInt(2, itemId);
            ins.executeUpdate();
        }
        return readOne(c, orderId, itemId, true);
    }
    public boolean detach(Connection c, int orderId, int itemId) throws SQLException {
        String sql = "UPDATE OrderItems SET Deleted = TRUE WHERE OrderID = ? AND ItemID = ? AND Deleted = FALSE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean isAttached(Connection c, int orderId, int itemId, boolean includeDeleted) throws SQLException {
        String sql = """
        SELECT 1
          FROM OrderItems
         WHERE OrderID = ? AND ItemID = ?
    """;
        if (!includeDeleted) {
            sql += " AND Deleted = FALSE";
        }
        sql += " LIMIT 1";

        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }
    public int countActiveItems(Connection c, int orderId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM OrderItems WHERE OrderID = ? AND Deleted = FALSE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }
    public List<OrderItem> listByOrder(Connection c, int orderId, boolean includeDeleted) throws SQLException {
        String sql = """
        SELECT OrderID, ItemID, Deleted
        FROM OrderItems
        WHERE OrderID = ?
    """;
        if (!includeDeleted) {
            sql += " AND Deleted = FALSE";
        }

        try (
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<OrderItem>();
                while (rs.next()) {
                    out.add(new OrderItem(
                            rs.getInt("OrderID"),
                            rs.getInt("ItemID"),
                            rs.getBoolean("Deleted")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            throw new RuntimeException(e);
        }
    }
    public List<OrderItem> listAll(Connection c, boolean includeDeleted) throws SQLException {
        String sql = """
        SELECT OrderID, ItemID, Deleted
        FROM OrderItems
    """;
        if (!includeDeleted) {
            sql += " WHERE Deleted = FALSE";
        }

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<OrderItem> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new OrderItem(
                        rs.getInt("OrderID"),
                        rs.getInt("ItemID"),
                        rs.getBoolean("Deleted")
                ));
            }
            return out;
        }
    }

    /* ---------- Helpers ---------- */
    private OrderItem readOne(Connection c, int orderId, int itemId, boolean includeDeleted) throws SQLException {
        String sql = """
      SELECT OrderID, ItemID, Deleted
        FROM OrderItems
       WHERE OrderID=? AND ItemID=?""" + (includeDeleted ? "" : " AND Deleted=FALSE") + " LIMIT 1";
        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new OrderItem(
                        rs.getInt("OrderID"),
                        rs.getInt("ItemID"),
                        rs.getBoolean("Deleted")
                );
            }
        }
    }
    public boolean orderExists(int orderId) {
        try (var c = ConnectionManager.getConnection();
             var ps = c.prepareStatement("SELECT 1 FROM Orders WHERE OrderID=?")) {
            ps.setInt(1, orderId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    public boolean itemExists(int itemId) {
        try (var c = ConnectionManager.getConnection();
             var ps = c.prepareStatement("SELECT 1 FROM Items WHERE ItemID=?")) {
            ps.setInt(1, itemId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    private static OrderItem map(ResultSet rs) throws SQLException {
        return new OrderItem(
                rs.getInt("OrderID"),
                rs.getInt("ItemID"),
                rs.getBoolean("Deleted")
        );
    }
}
