package Fuzzcode.service;

import Fuzzcode.dao.PersonDao;
import Fuzzcode.model.Person;
import Fuzzcode.model.PersonRole;

import java.util.List;

public class PersonService {
    private final PersonDao dao = new PersonDao();
    public Person createPerson(String name, PersonRole role) {
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
    public boolean updateRole(int id, PersonRole newRole) {
        return dao.updatePersonRole(id, newRole);
    }
    public boolean removePerson(int id) {
        return dao.deletePerson(id);
    }
}
