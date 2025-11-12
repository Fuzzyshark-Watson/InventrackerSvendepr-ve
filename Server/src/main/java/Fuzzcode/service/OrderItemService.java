package Fuzzcode.service;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.dao.*;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.model.OrderItem;

import java.sql.Connection;
import java.util.List;

public class OrderItemService {

    private final OrderItemDao orderItemDao = new OrderItemDao();

    public OrderItem assignItemToOrder(int itemId, int orderId) {
        if (!orderItemDao.orderExists(orderId))
            throw new IllegalArgumentException("Order " + orderId + " does not exist");
        if (!orderItemDao.itemExists(itemId))
            throw new IllegalArgumentException("Item " + itemId + " does not exist");
        OrderItem oi = orderItemDao.attach(orderId, itemId);
        LoggerHandler.log("Assigned: " + oi);
        return oi;
    }
    public boolean detachItemFromOrder(int itemId, int orderId) {
        boolean ok = orderItemDao.detach(orderId, itemId);
        LoggerHandler.log(ok ? "Detached item " + itemId + " from order " + orderId
                : "No active relation to detach");
        return ok;
    }
    public boolean moveItemToAnotherOrder(int itemId, int fromOrderId, int toOrderId) {
        if (fromOrderId == toOrderId) return true; // noop
        try (Connection c = ConnectionManager.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                orderItemDao.detach(c, fromOrderId, itemId);
                orderItemDao.attach(c, toOrderId, itemId);
                c.commit();
                LoggerHandler.log("Moved item " + itemId + " from order " + fromOrderId + " to " + toOrderId);
                return true;
            } catch (Exception e) {
                c.rollback();
                LoggerHandler.log(e);
                return false;
            } finally {
                c.setAutoCommit(auto);
            }
        } catch (Exception e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public List<OrderItem> getItemsInOrder(int orderId, boolean includeDeleted) {
        List<OrderItem> list = orderItemDao.listByOrder(orderId, includeDeleted);
        LoggerHandler.log("Order " + orderId + " contains " + list.size() + " item(s)");
        return list;
    }
    public boolean isAttached(int orderId, int itemId) {
        return orderItemDao.isAttached(orderId, itemId, false);
    }
}
