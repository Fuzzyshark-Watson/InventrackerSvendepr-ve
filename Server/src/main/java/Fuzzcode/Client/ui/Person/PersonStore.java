package Fuzzcode.Client.ui.Person;

import Fuzzcode.Server.model.Person;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class PersonStore {

    private static final PersonStore INSTANCE = new PersonStore();
    public static PersonStore getInstance() { return INSTANCE; }

    private PersonStore() {}

    private final List<Person> people = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onPeopleChanged(List<Person> people);
    }

    public void addListener(Listener l) { listeners.add(l); }

    public List<Person> getPeople() {
        return List.copyOf(people);
    }

    public void replaceAll(List<Person> list) {
        SwingUtilities.invokeLater(() -> {
            people.clear();
            for (Person p : list) {
                if (!p.deleted()) people.add(p);
            }
            notifyListeners();
        });
    }

    public void upsert(Person p) {
        SwingUtilities.invokeLater(() -> {
            if (p.deleted()) {
                remove(p.personId());
                return;
            }
            int idx = findIndex(p.personId());
            if (idx >= 0) people.set(idx, p);
            else people.add(p);
            notifyListeners();
        });
    }

    public void remove(int personId) {
        SwingUtilities.invokeLater(() -> {
            int idx = findIndex(personId);
            if (idx >= 0) {
                people.remove(idx);
                notifyListeners();
            }
        });
    }

    private int findIndex(int id) {
        for (int i = 0; i < people.size(); i++) {
            if (people.get(i).personId() == id) return i;
        }
        return -1;
    }

    private void notifyListeners() {
        List<Person> snapshot = List.copyOf(people);
        for (Listener l : listeners) l.onPeopleChanged(snapshot);
    }
}
