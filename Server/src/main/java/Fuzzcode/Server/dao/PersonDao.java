package Fuzzcode.Server.dao;

import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.model.Person;
import Fuzzcode.Server.model.PersonRole;
import Fuzzcode.Server.utilities.LoggerHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PersonDao {


    public int createPerson(String name, PersonRole role) {
        String sql = """
            INSERT INTO People (Name, Role, Deleted)
            VALUES (?, ?, FALSE)
        """;

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, role.name());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return 0;
        }
    }
    public Person readPerson(int id, boolean includeDeleted) {
        String sql = """
            SELECT PersonID, Name, Role, Deleted
            FROM People
            WHERE PersonID = ?
        """ + (includeDeleted ? "" : " AND Deleted = FALSE");

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Person(
                            rs.getInt("PersonID"),
                            rs.getString("Name"),
                            PersonRole.valueOf(rs.getString("Role")),
                            rs.getBoolean("Deleted")
                    );
                }
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return null;
    }
    public List<Person> readAllActive() {
        List<Person> people = new ArrayList<>();
        String sql = """
            SELECT PersonID, Name, Role, Deleted
            FROM People
            WHERE Deleted = FALSE
        """;

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                people.add(new Person(
                        rs.getInt("PersonID"),
                        rs.getString("Name"),
                        PersonRole.valueOf(rs.getString("Role")),
                        rs.getBoolean("Deleted")
                ));
            }

        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return people;
    }
    public boolean updatePerson(int id, String name, PersonRole role) {
        String sql = """
            UPDATE People
            SET Name = ?, Role = ?
            WHERE PersonID = ? AND Deleted = FALSE
        """;

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, role.name());
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public boolean updatePersonRole(int id, PersonRole newRole) {
        String sql = """
            UPDATE People
            SET Role = ?
            WHERE PersonID = ? AND Deleted = FALSE
        """;

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newRole.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public boolean softDeletePerson(int id) {
        String sql = """
            UPDATE People
            SET Deleted = TRUE
            WHERE PersonID = ? AND Deleted = FALSE
        """;

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public boolean deletePerson(int id) {
        String sql = """
            UPDATE People
            SET Deleted = TRUE
            WHERE PersonID = ? AND Deleted = FALSE
        """;

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
}
