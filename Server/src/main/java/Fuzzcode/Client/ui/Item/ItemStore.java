package Fuzzcode.Client.ui.Item;

import Fuzzcode.Server.model.Item;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ItemStore {

    private static final ItemStore INSTANCE = new ItemStore();
    public static ItemStore getInstance() { return INSTANCE; }

    private ItemStore() {}

    private final List<Item> items = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onItemsChanged(List<Item> items);
    }

    public void addListener(Listener l) { listeners.add(l); }

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public void replaceAll(List<Item> newItems) {
        SwingUtilities.invokeLater(() -> {
            items.clear();
            for (Item i : newItems) {
                if (!i.deleted()) items.add(i);
            }
            notifyListeners();
        });
    }

    public void upsert(Item item) {
        SwingUtilities.invokeLater(() -> {
            if (item.deleted()) {
                remove(item.itemId());
                return;
            }
            int idx = findIndex(item.itemId());
            if (idx >= 0) items.set(idx, item);
            else items.add(item);
            notifyListeners();
        });
    }

    public void remove(int itemId) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(itemId);
            if (idx >= 0) {
                items.remove(idx);
                notifyListeners();
            }
        });
    }

    private int findIndex(int itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).itemId() == itemId) return i;
        }
        return -1;
    }

    private void notifyListeners() {
        List<Item> snapshot = List.copyOf(items);
        for (Listener l : listeners) l.onItemsChanged(snapshot);
    }
}

