package Fuzzcode.Client.ui.OrderItem;

import Fuzzcode.Server.model.OrderItem;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class OrderItemStore {

    private static final OrderItemStore INSTANCE = new OrderItemStore();
    public static OrderItemStore getInstance() { return INSTANCE; }

    private OrderItemStore() {}

    private final List<OrderItem> relations = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onOrderItemsChanged(List<OrderItem> orderItems);
    }

    public void addListener(Listener l) { listeners.add(l); }

    public List<OrderItem> getOrderItems() {
        return List.copyOf(relations);
    }

    public void replaceAll(List<OrderItem> list) {
        SwingUtilities.invokeLater(() -> {
            relations.clear();
            for (OrderItem oi : list) {
                if (!oi.deleted()) relations.add(oi);
            }
            notifyListeners();
        });
    }

    public void upsert(OrderItem oi) {
        SwingUtilities.invokeLater(() -> {
            if (oi.deleted()) {
                remove(oi.orderId(), oi.itemId());
                return;
            }
            int idx = findIndex(oi.orderId(), oi.itemId());
            if (idx >= 0) relations.set(idx, oi);
            else relations.add(oi);
            notifyListeners();
        });
    }

    public void remove(int orderId, int itemId) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(orderId, itemId);
            if (idx >= 0) {
                relations.remove(idx);
                notifyListeners();
            }
        });
    }

    private int findIndex(int orderId, int itemId) {
        for (int i = 0; i < relations.size(); i++) {
            OrderItem oi = relations.get(i);
            if (oi.orderId() == orderId && oi.itemId() == itemId) return i;
        }
        return -1;
    }

    private void notifyListeners() {
        List<OrderItem> snapshot = List.copyOf(relations);
        for (Listener l : listeners) l.onOrderItemsChanged(snapshot);
    }
}

