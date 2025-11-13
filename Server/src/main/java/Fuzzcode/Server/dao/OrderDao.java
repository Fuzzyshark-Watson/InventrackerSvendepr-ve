package Fuzzcode.Server.dao;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.model.Order;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    public int createOrder(LocalDate createdDate, Integer customerId, Integer loggedById) {
        try (Connection c = ConnectionManager.getConnection()) {
            return createOrder(c, createdDate, customerId, loggedById);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            throw new RuntimeException("Failed to create order", e);
        }
    }
    public Order readOrder(int orderId, boolean includeDeleted) {
        try (Connection c = ConnectionManager.getConnection()) {
            return readOrder(c, orderId, includeDeleted);
        } catch (SQLException e) { LoggerHandler.log(e); return null; }
    }
    public List<Order> listOrders(boolean includeDeleted, Integer customerId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return listOrders(c, includeDeleted, customerId);
        } catch (SQLException e) { LoggerHandler.log(e); return List.of(); }
    }
    public boolean updateOrderDates(int orderId, LocalDate start, LocalDate end) {
        try (Connection c = ConnectionManager.getConnection()) {
            return updateOrderDates(c, orderId, start, end);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public boolean updateOrderStartDate(int orderId, LocalDate start) {
        try (Connection c = ConnectionManager.getConnection()) {

            return updateOrderDates(c, orderId, start, (readOrder(orderId, false).endDate()));
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public boolean updateOrderEndDate(int orderId, LocalDate end) {
        try (Connection c = ConnectionManager.getConnection()) {

            return updateOrderDates(c, orderId,(readOrder(orderId, false).endDate()), end);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public boolean assignCustomer(int orderId, Integer customerId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return assignCustomer(c, orderId, customerId);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }
    public boolean softDeleteOrder(int orderId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return softDeleteOrder(c, orderId);
        } catch (SQLException e) { LoggerHandler.log(e); return false; }
    }

    /* ================= Connection-taking overloads ================= */
    public int createOrder(Connection c, LocalDate createdDate, Integer customerId, Integer loggedById) throws SQLException {
        String sql = """
            INSERT INTO Orders (CreatedDate, StartDate, EndDate, CustomerID, LoggedByID, Deleted)
            VALUES (?, NULL, NULL, ?, ?, FALSE)
        """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(createdDate != null ? createdDate : LocalDate.now()));
            if (customerId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, customerId);
            if (loggedById == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, loggedById);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { return keys.next() ? keys.getInt(1) : 0; }
        }
    }
    public Order readOrder(Connection c, int orderId, boolean includeDeleted) throws SQLException {
        String sql = """
            SELECT OrderID, CreatedDate, StartDate, EndDate, CustomerID, LoggedByID, Deleted
            FROM Orders WHERE OrderID = ?
        """ + (includeDeleted ? "" : " AND Deleted = FALSE");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapOrder(rs) : null; }
        }
    }
    public List<Order> listOrders(Connection c, boolean includeDeleted, Integer customerId) throws SQLException {
        List<Order> out = new ArrayList<>();
        String sql = """
            SELECT OrderID, CreatedDate, StartDate, EndDate, CustomerID, LoggedByID, Deleted
            FROM Orders WHERE 1=1
        """ + (includeDeleted ? "" : " AND Deleted = FALSE")
                + (customerId == null ? "" : " AND CustomerID = ?")
                + " ORDER BY CreatedDate DESC, OrderID DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            if (customerId != null) ps.setInt(idx++, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapOrder(rs));
            }
        }
        return out;
    }
    public boolean updateOrderDates(Connection c, int orderId, LocalDate start, LocalDate end) throws SQLException {
        String sql = """
            UPDATE Orders SET StartDate = ?, EndDate = ?
            WHERE OrderID = ? AND Deleted = FALSE
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (start == null) ps.setNull(1, Types.DATE); else ps.setDate(1, Date.valueOf(start));
            if (end == null) ps.setNull(2, Types.DATE); else ps.setDate(2, Date.valueOf(end));
            ps.setInt(3, orderId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean assignCustomer(Connection c, int orderId, Integer customerId) throws SQLException {
        String sql = "UPDATE Orders SET CustomerID = ? WHERE OrderID = ? AND Deleted = FALSE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (customerId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, customerId);
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean softDeleteOrder(Connection c, int orderId) throws SQLException {
        String sql = "UPDATE Orders SET Deleted = TRUE WHERE OrderID = ? AND Deleted = FALSE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    /* ================= Mapper ================= */
    private static Order mapOrder(ResultSet rs) throws SQLException {
        Date c = rs.getDate("CreatedDate");
        Date s = rs.getDate("StartDate");
        Date e = rs.getDate("EndDate");
        Integer cust = (Integer) rs.getObject("CustomerID");
        Integer logb = (Integer) rs.getObject("LoggedByID");
        return new Order(
                rs.getInt("OrderID"),
                c != null ? c.toLocalDate() : null,
                s != null ? s.toLocalDate() : null,
                e != null ? e.toLocalDate() : null,
                cust,
                logb,
                rs.getBoolean("Deleted")
        );
    }
}
