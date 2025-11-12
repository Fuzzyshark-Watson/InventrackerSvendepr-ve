package Fuzzcode.db;

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
                // id buckets
                List<Integer> customerIds = insertCustomers(c);
                List<Integer> staffIds    = insertStaff(c);
                // create orders
                int orderCount = 5;
                int nextTagNum = 1;

                Random rng = new Random(42); // deterministic for repeatable seeds

                for (int i = 0; i < orderCount; i++) {
                    int customerId = customerIds.get(rng.nextInt(customerIds.size()));
                    int loggedById = staffIds.get(rng.nextInt(staffIds.size()));

                    LocalDate created = LocalDate.now().minusDays(7 - i * 2L); // spaced out
                    LocalDate start   = created.plusDays(1 + rng.nextInt(2));
                    LocalDate end     = rng.nextBoolean() ? start.plusDays(1 + rng.nextInt(3)) : null;

                    int orderId = insertOrder(c, created, start, end, customerId, loggedById);

                    // 10–15 items per order
                    int itemsThisOrder = 10 + rng.nextInt(6); // 10..15
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

                        // sprinkle some reads for this tag
                        int reads = 1 + rng.nextInt(4); // 1..4 reads
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
        String[] names = {
                "Acme Corp", "Globex Inc", "Soylent Co", "Initech Ltd", "Umbrella PLC"
        };
        List<Integer> ids = new ArrayList<>();
        String sql = "INSERT INTO People(Name, Role, Deleted) VALUES(?, 'CUSTOMER', FALSE)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (String n : names) {
                ps.setString(1, n);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    private static List<Integer> insertStaff(Connection c) throws SQLException {
        // mix of USER/DRIVER
        record Staff(String name, String role) {}
        Staff[] staff = {
                new Staff("Alice Admin", "USER"),
                new Staff("Bob Booker",  "USER"),
                new Staff("Cara Courier","DRIVER"),
                new Staff("Dan Driver",  "DRIVER"),
                new Staff("Eve Entry",   "USER"),
        };
        List<Integer> ids = new ArrayList<>();
        String sql = "INSERT INTO People(Name, Role, Deleted) VALUES(?, ?, FALSE)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Staff s : staff) {
                ps.setString(1, s.name());
                ps.setString(2, s.role());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) ids.add(rs.getInt(1));
                }
            }
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
