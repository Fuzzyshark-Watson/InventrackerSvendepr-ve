package Fuzzcode.dao;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.model.OrderItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderItemDao {

    /* ---------- Convenience (auto-connection) ---------- */

    public OrderItem attach(int orderId, int itemId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return attach(c, orderId, itemId);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return null;
        }
    }
    public boolean detach(int orderId, int itemId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return detach(c, orderId, itemId);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public boolean isAttached(int orderId, int itemId, boolean onlyActive) {
        try (Connection c = ConnectionManager.getConnection()) {
            return isAttached(c, orderId, itemId, onlyActive);
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

    /* ---------- Connection-taking ---------- */
    public OrderItem attach(Connection c, int orderId, int itemId) throws SQLException {
        String reactivate = """
            UPDATE OrderItems SET Deleted = FALSE
            WHERE OrderID = ? AND ItemID = ? AND Deleted = TRUE
        """;
        String insert = """
            INSERT INTO OrderItems (OrderID, ItemID, Deleted)
            SELECT ?, ?, FALSE
            WHERE NOT EXISTS (
              SELECT 1 FROM OrderItems WHERE OrderID = ? AND ItemID = ?
            )
        """;

        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int changed;
            try (PreparedStatement ps1 = c.prepareStatement(reactivate)) {
                ps1.setInt(1, orderId);
                ps1.setInt(2, itemId);
                changed = ps1.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement ps2 = c.prepareStatement(insert)) {
                    ps2.setInt(1, orderId);
                    ps2.setInt(2, itemId);
                    ps2.setInt(3, orderId);
                    ps2.setInt(4, itemId);
                    ps2.executeUpdate();
                }
            }
            c.commit();

            // Read the final state and return it
            return readOne(c, orderId, itemId, /*includeDeleted*/ true);
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(auto);
        }
    }
    public boolean detach(Connection c, int orderId, int itemId) throws SQLException {
        String sql = "UPDATE OrderItems SET Deleted = TRUE WHERE OrderID = ? AND ItemID = ? AND Deleted = FALSE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean isAttached(Connection c, int orderId, int itemId, boolean onlyActive) throws SQLException {
        String sql = """
            SELECT 1 FROM OrderItems
            WHERE OrderID = ? AND ItemID = ?
        """ + (onlyActive ? " AND Deleted = FALSE" : "") + " LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
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
        List<OrderItem> out = new ArrayList<>();
        String sql = """
            SELECT OrderID, ItemID, Deleted
            FROM OrderItems
            WHERE OrderID = ?
        """ + (includeDeleted ? "" : " AND Deleted = FALSE");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /* ---------- Helpers ---------- */
    private OrderItem readOne(Connection c, int orderId, int itemId, boolean includeDeleted) throws SQLException {
        String sql = """
            SELECT OrderID, ItemID, Deleted
            FROM OrderItems
            WHERE OrderID = ? AND ItemID = ?
        """ + (includeDeleted ? "" : " AND Deleted = ") + includeDeleted + " LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }
    private static OrderItem map(ResultSet rs) throws SQLException {
        return new OrderItem(
                rs.getInt("OrderID"),
                rs.getInt("ItemID"),
                rs.getBoolean("Deleted")
        );
    }
}
