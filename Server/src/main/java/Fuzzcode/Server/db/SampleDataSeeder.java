package Fuzzcode.Server.db;

import Fuzzcode.Server.model.AppUser;
import Fuzzcode.Server.model.Person;
import Fuzzcode.Server.model.PersonRole;
import Fuzzcode.Server.model.UserRole;
import Fuzzcode.Server.service.PersonService;
import Fuzzcode.Server.service.UserService;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public final class SampleDataSeeder {

    private SampleDataSeeder() {}

    public static void seed() {
        try (Connection c = ConnectionManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<Integer> customerIds = insertCustomers(c);
                List<PersonUser> staffIds = insertStaff(c);
                List<Integer> staffPersonIds = new ArrayList<>();
                for (PersonUser pu : staffIds) {
                    staffPersonIds.add(pu.person().personId());
                }
                int orderCount = 25;
                int nextTagNum = 1;

                Random rng = new Random(42);

                for (int i = 0; i < orderCount; i++) {
                    int customerId = customerIds.get(rng.nextInt(customerIds.size()));
                    int loggedById = staffPersonIds.get(rng.nextInt(staffPersonIds.size()));

                    LocalDate created = LocalDate.now().minusDays(7 - i * 2L); // spaced out
                    LocalDate start   = created.plusDays(1 + rng.nextInt(2));
                    LocalDate end     = rng.nextBoolean() ? start.plusDays(1 + rng.nextInt(3)) : null;

                    int orderId = insertOrder(c, created, start, end, customerId, loggedById);

                    int itemsThisOrder = 50 + rng.nextInt(6); // 10..15
                    for (int n = 0; n < itemsThisOrder; n++) {
                        String tag = "TAG-%05d".formatted(nextTagNum++);
                        String position = switch (rng.nextInt(4)) {
                            case 0 -> "HOME";
                            case 1 -> "IN_TRANSIT_OUT";
                            case 2 -> "DELIVERED";
                            default -> "IN_TRANSIT_RETURN";
                        };
                        boolean overdue = rng.nextInt(10) == 0; // ~10%

                        int itemId = insertItem(c, tag, position, overdue);
                        insertOrderItem(c, orderId, itemId);

                        int reads = 1 + rng.nextInt(22); // 1..4 reads
                        for (int r = 0; r < reads; r++) {
                            insertItemRead(c, tag,
                                    LocalDateTime.now().minusHours(rng.nextInt(200)));
                        }
                    }
                }

                c.commit();
                System.out.println("[SEED] Sample data inserted.");
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Seeding failed", e);
        }
    }

    // --- inserts -------------------------------------------------------------

    private static List<Integer> insertCustomers(Connection c) throws SQLException {
        PersonService personService = new PersonService();
        List<Integer> ids = new ArrayList<>();
        String[] names = {
                "Acme Corp", "Globex Inc", "Soylent Co", "Initech Ltd", "Umbrella PLC"
        };
        for (String n : names) {
            Person p = personService.createPerson(n,PersonRole.CUSTOMER);
            ids.add(p.personId());
        }
        return ids;
    }
    public record PersonUser(Person person, AppUser user) {}

    private static List<PersonUser> insertStaff(Connection c) throws SQLException {
        List<PersonUser> ids = new ArrayList<>();
        PersonService personService = new PersonService();
        UserService userService = new UserService();

        record Users(String name, PersonRole pRole, UserRole uRole, String password) {}

        Users[] users = {
                new Users("admin",  PersonRole.ADMIN, UserRole.ADMIN, "root"),
                new Users("Mads", PersonRole.USER, UserRole.USER, "B4D1dea5AreFree"),
                new Users("Kim",  PersonRole.USER, UserRole.USER,"Wh4tTheHex"),
                new Users("Lars",   PersonRole.USER, UserRole.USER,"L0stInTr4nsl8ion"),
                new Users("Trine", PersonRole.USER, UserRole.USER,"PurrS1st3nceP4ys"),
                new Users("Nikolaj",  PersonRole.USER, UserRole.USER,"Knot2DayS4tan"),
                new Users("Zlatko",   PersonRole.USER, UserRole.USER,"TacoBoutS3curity"),
                new Users("Sofie", PersonRole.USER, UserRole.USER,"CtrlAltD3l1cious"),
                new Users("Bjarne",  PersonRole.USER, UserRole.USER,"B33M0vi3IsArt"),
                new Users("Ann",   PersonRole.USER, UserRole.USER,"P4ssTheSalt123"),
                new Users("Roland", PersonRole.USER, UserRole.USER,"SudoMakeM3ASandwich"),
                new Users("Ulrich",  PersonRole.USER, UserRole.USER,"G1mm3ABr34kF4st"),
                new Users("Bodil",   PersonRole.USER, UserRole.USER,"P0t8oP0t4hTo"),
                new Users("Dar", PersonRole.USER, UserRole.USER,"N0M0reMrN1ceG1"),
                new Users("Birthe",   PersonRole.USER, UserRole.USER,"ByteM3Plz"),
                new Users("Søren", PersonRole.USER, UserRole.USER,"IM3wYou2"),
                new Users("Nicolas",  PersonRole.USER, UserRole.USER,"Unb3arablyPunNy"),
                new Users("Ghita",   PersonRole.USER, UserRole.USER,"R3turnOfTheSn4cks"),
                new Users("Danica",  PersonRole.USER, UserRole.USER,"N0tS0F1shyNow"),
        };
        for (Users s : users) {
            Person p = personService.createPerson(s.name,s.pRole);
            AppUser u = userService.register(s.name, s.password,s.uRole);
            ids.add(new PersonUser(p, u));
        }
        return ids;
    }
    private static List<Integer> insertDrivers(Connection c) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        PersonService personService = new PersonService();
        // Drivers
        record Drivers(String name, PersonRole pRole) {}
        Drivers[] drivers = {
                new Drivers("Jakob",PersonRole.DRIVER),
                new Drivers("Thomas",  PersonRole.DRIVER),
                new Drivers("Iben",PersonRole.DRIVER),
                new Drivers("Anders",  PersonRole.DRIVER),
                new Drivers("Sidse",PersonRole.DRIVER),
                new Drivers("Birgitte",  PersonRole.DRIVER),
                new Drivers("Paprika",PersonRole.DRIVER),
                new Drivers("David",  PersonRole.DRIVER),
                new Drivers("Mille",PersonRole.DRIVER),
                new Drivers("Gustav",  PersonRole.DRIVER),
                new Drivers("Jesper",PersonRole.DRIVER),
                new Drivers("Ali",  PersonRole.DRIVER),
        };
        for (Drivers s : drivers) {
            Person p = personService.createPerson(s.name,s.pRole);
            ids.add(p.personId());
        }
        return ids;
    }
    private static int insertOrder(Connection c,
                                   LocalDate created, LocalDate start, LocalDate end,
                                   int customerId, int loggedById) throws SQLException {
        String sql = """
                INSERT INTO Orders(CreatedDate, StartDate, EndDate, CustomerID, LoggedByID, Deleted)
                VALUES(?, ?, ?, ?, ?, FALSE)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(created));
            if (start != null) ps.setDate(2, Date.valueOf(start)); else ps.setNull(2, Types.DATE);
            if (end   != null) ps.setDate(3, Date.valueOf(end));   else ps.setNull(3, Types.DATE);
            ps.setInt(4, customerId);
            ps.setInt(5, loggedById);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int insertItem(Connection c, String tagId, String position, boolean overdue) throws SQLException {
        String sql = """
                INSERT INTO Items(TagID, Position, IsOverdue, Deleted)
                VALUES(?, ?, ?, FALSE)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tagId);
            ps.setString(2, position);
            if (overdue) ps.setBoolean(3, true); else ps.setNull(3, Types.BOOLEAN);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void insertOrderItem(Connection c, int orderId, int itemId) throws SQLException {
        String sql = """
                INSERT INTO OrderItems(OrderID, ItemID, Deleted) VALUES(?, ?, FALSE)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
    }

    private static void insertItemRead(Connection c, String tagId, LocalDateTime readTime) throws SQLException {
        String sql = """
                INSERT INTO ItemRead(TagID, Deleted, ReadTime) VALUES(?, FALSE, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tagId);
            ps.setTimestamp(2, Timestamp.valueOf(readTime));
            ps.executeUpdate();
        }
    }
}
