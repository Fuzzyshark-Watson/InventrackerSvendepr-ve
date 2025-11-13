package Fuzzcode.Client.ui.User;

import Fuzzcode.Server.model.AppUser;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UserStore {

    private static final UserStore INSTANCE = new UserStore();
    public static UserStore getInstance() { return INSTANCE; }

    private UserStore() {}

    private final List<AppUser> users = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onUsersChanged(List<AppUser> users);
    }

    public void addListener(Listener l) { listeners.add(l); }

    public List<AppUser> getUsers() {
        return List.copyOf(users);
    }

    public void replaceAll(List<AppUser> list) {
        SwingUtilities.invokeLater(() -> {
            users.clear();
            users.addAll(list);
            notifyListeners();
        });
    }

    public void upsert(AppUser user) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(user.userId());
            if (idx >= 0) users.set(idx, user);
            else users.add(user);
            notifyListeners();
        });
    }

    public void remove(int userId) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(userId);
            if (idx >= 0) {
                users.remove(idx);
                notifyListeners();
            }
        });
    }

    private int findIndex(int id) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).userId() == id) return i;
        }
        return -1;
    }

    private void notifyListeners() {
        List<AppUser> snapshot = List.copyOf(users);
        for (Listener l : listeners) l.onUsersChanged(snapshot);
    }
}

