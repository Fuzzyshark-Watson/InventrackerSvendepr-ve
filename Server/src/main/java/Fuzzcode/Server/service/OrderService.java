package Fuzzcode.Server.service;

import Fuzzcode.Server.dao.ItemDao;
import Fuzzcode.Server.model.Item;
import Fuzzcode.Server.model.UserRole;
import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.dao.OrderDao;
import Fuzzcode.Server.dao.OrderItemDao;
import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.model.Order;
import Fuzzcode.Server.model.OrderItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class OrderService {
    private final OrderDao orderDao = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final ItemDao itemDao = new ItemDao();

    /* ===== Order lifecycle ===== */
    public Order createOrder(LocalDate createdDate, Integer customerId, Integer loggedById) {

        int id = orderDao.createOrder(createdDate, customerId, loggedById);
        return id > 0 ? orderDao.readOrder(id, true) : null;
    }
    public Order getOrder(int orderId) {
        return orderDao.readOrder(orderId, false);
    }
    public Order getOrder(int orderId, boolean include) {
        return orderDao.readOrder(orderId, include);
    }

    public boolean updateOrderDates(int orderId, LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            LoggerHandler.log(LoggerHandler.Level.WARNING, "End date before start date: " + start + " > " + end);
            return false;
        }
        return orderDao.updateOrderDates(orderId, start, end);
    }
    public boolean updateOrderStartDate(int orderId, LocalDate start) {

        return orderDao.updateOrderStartDate(orderId, start);
    }
    public boolean updateOrderEndDate(int orderId, LocalDate end) {
        Order o = orderDao.readOrder(orderId, false);
        if (o == null) return false;
        if (o.startDate() != null && end != null && end.isBefore(o.startDate())) {
            LoggerHandler.log(LoggerHandler.Level.WARNING, "End date before start date for order " + orderId);
            return false;
        }
        return orderDao.updateOrderEndDate(orderId, end);
    }
    public boolean softDeleteOrder(int orderId) {
        return orderDao.softDeleteOrder(orderId);
    }
    public boolean softDeleteOrder(int orderId, UserRole actorRole) {
        if (actorRole != UserRole.ADMIN)
            throw new SecurityException("Admin role required");
        return softDeleteOrder(orderId);
    }
    public boolean startOrder(int orderId, LocalDate when) {
        Order o = orderDao.readOrder(orderId, false);
        if (o == null) return false;
        return orderDao.updateOrderDates(orderId, when != null ? when : LocalDate.now(), o.endDate());
    }
    public boolean closeOrder(int orderId, LocalDate when) {
        Order o = orderDao.readOrder(orderId, false);
        if (o == null || o.deleted()) return false;
        LocalDate end = when != null ? when : LocalDate.now();
        LocalDate start = o.startDate() != null ? o.startDate() : end;
        return orderDao.updateOrderDates(orderId, start, end);
    }

    public List<Order> listActiveOrders() {
        return orderDao.listOrders(false, null);
    }
    public List<Item> listItemsForOrder(int orderId, boolean includeDeleted) {
        try (Connection c = ConnectionManager.getConnection()) {
            return itemDao.listByOrder(orderId, includeDeleted);  // joins inside ItemDao
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return List.of();
        }
    }
    public boolean detachItem(int orderId, int itemId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return orderItemDao.detach(c, orderId, itemId);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public List<OrderItem> listOrderItems(int orderId, boolean includeDeleted) {
        try (Connection c = ConnectionManager.getConnection()) {
            return orderItemDao.listByOrder(c, orderId, includeDeleted);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return List.of();
        }
    }
    public int countActiveItems(int orderId) {
        try (Connection c = ConnectionManager.getConnection()) {
            return orderItemDao.countActiveItems(c, orderId);
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return 0;
        }
    }
}
