package Fuzzcode.Client.ui.Order;

import Fuzzcode.Server.model.Order;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class OrderStore {

    private static final OrderStore INSTANCE = new OrderStore();
    private OrderStore() {
        // private so nobody can do new OrderStore()
    }
    public static OrderStore getInstance() {
        return INSTANCE;
    }

    private final List<Order> orders = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onOrdersChanged(List<Order> orders);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public List<Order> getOrders() {
        return List.copyOf(orders);
    }

    public void replaceAll(List<Order> newOrders) {
        SwingUtilities.invokeLater(() -> {
            orders.clear();
            // filter out deleted here if you want
            for (Order o : newOrders) {
                if (!o.deleted()) {
                    orders.add(o);
                }
            }
            notifyListeners();
        });
    }

    public void upsert(Order order) {
        SwingUtilities.invokeLater(() -> {
            if (order.deleted()) {
                remove(order.orderId());
                return;
            }
            int idx = findIndex(order.orderId());
            if (idx >= 0) orders.set(idx, order);
            else orders.add(order);
            notifyListeners();
        });
    }

    public void remove(int orderId) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(orderId);
            if (idx >= 0) {
                orders.remove(idx);
                notifyListeners();
            }
        });
    }

    private int findIndex(int orderId) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).orderId() == orderId) return i;
        }
        return -1;
    }

    private void notifyListeners() {
        List<Order> snapshot = List.copyOf(orders);
        for (Listener l : listeners) {
            l.onOrdersChanged(snapshot);
        }
    }
}
