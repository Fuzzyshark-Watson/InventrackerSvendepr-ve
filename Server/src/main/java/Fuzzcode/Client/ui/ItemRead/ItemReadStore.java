package Fuzzcode.Client.ui.ItemRead;

import Fuzzcode.Server.model.ItemRead;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ItemReadStore {

    private static final ItemReadStore INSTANCE = new ItemReadStore();
    public static ItemReadStore getInstance() { return INSTANCE; }

    private ItemReadStore() {}

    private final List<ItemRead> reads = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onReadsChanged(List<ItemRead> reads);
    }

    public void addListener(Listener l) { listeners.add(l); }

    public List<ItemRead> getReads() {
        return List.copyOf(reads);
    }

    public void replaceAll(List<ItemRead> newReads) {
        SwingUtilities.invokeLater(() -> {
            reads.clear();
            for (ItemRead r : newReads) {
                if (!r.deleted()) reads.add(r);
            }
            notifyListeners();
        });
    }

    public void upsert(ItemRead r) {
        SwingUtilities.invokeLater(() -> {
            if (r.deleted()) {
                remove(r.readId());
                return;
            }
            int idx = findIndex(r.readId());
            if (idx >= 0) reads.set(idx, r);
            else reads.add(r);
            notifyListeners();
        });
    }

    public void remove(int readId) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(readId);
            if (idx >= 0) {
                reads.remove(idx);
                notifyListeners();
            }
        });
    }

    private int findIndex(int readId) {
        for (int i = 0; i < reads.size(); i++) {
            if (reads.get(i).readId() == readId) return i;
        }
        return -1;
    }

    private void notifyListeners() {
        List<ItemRead> snapshot = List.copyOf(reads);
        for (Listener l : listeners) l.onReadsChanged(snapshot);
    }
}

