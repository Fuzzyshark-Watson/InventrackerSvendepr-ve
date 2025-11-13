package Fuzzcode.Server.service;

import Fuzzcode.Server.dao.PersonDao;
import Fuzzcode.Server.model.Person;
import Fuzzcode.Server.model.PersonRole;

import java.util.List;

public class PersonService {
    private final PersonDao dao = new PersonDao();

    public Person createPerson(String name, PersonRole role) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        int id = dao.createPerson(name, role);
        return dao.readPerson(id, true);
    }
    public Person getPerson(int id) {
        return dao.readPerson(id, false);
    }
    public Person getPerson(int id, boolean include) {
        return dao.readPerson(id, include);
    }
    public List<Person> listPeople() {
        return dao.readAllActive();
    }
    public Person updatePerson(int id, String name, PersonRole role) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        boolean ok = dao.updatePerson(id, role != null ? name : name, role);
        if (!ok) return null;
        return dao.readPerson(id, true);
    }
    public boolean updateRole(int id, PersonRole newRole) {
        return dao.updatePersonRole(id, newRole);
    }
    public boolean removePerson(int id) {
        return dao.deletePerson(id);
    }
}
