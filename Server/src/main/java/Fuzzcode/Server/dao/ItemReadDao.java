package Fuzzcode.Server.dao;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.model.ItemRead;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ItemReadDao {

    public int recordItemRead(String tagId, Instant timestamp) {
        String sql = """
            INSERT INTO ItemRead (TagID, Deleted, ReadTime)
            VALUES (?, FALSE, ?)
        """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, tagId);
            ps.setTimestamp(2, Timestamp.from(timestamp));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
            return 0;
        }
    }
    public List<ItemRead> listReadsForTag(String tagId, int limit) {
        List<ItemRead> out = new ArrayList<>();
        String sql = """
            SELECT ReadID, TagID, ReadTime, Deleted
            FROM ItemRead
            WHERE TagID = ? AND Deleted = FALSE
            ORDER BY ReadTime DESC
            LIMIT ?
        """;

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, tagId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ItemRead(
                            rs.getInt("ReadID"),
                            rs.getString("TagID"),
                            rs.getTimestamp("ReadTime").toInstant(),
                            rs.getBoolean("Deleted")
                    ));
                }
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return out;
    }
    public List<ItemRead> listAllActiveReads() {
        List<ItemRead> out = new ArrayList<>();
        String sql = """
            SELECT ReadID, TagID, ReadTime, Deleted
            FROM ItemRead
            WHERE Deleted = FALSE
            ORDER BY ReadTime DESC
        """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(new ItemRead(
                        rs.getInt("ReadID"),
                        rs.getString("TagID"),
                        rs.getTimestamp("ReadTime").toInstant(),
                        rs.getBoolean("Deleted")
                ));
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return out;
    }
    public ItemRead updateItemRead(int readId, String tagId, Instant ts) {
        String sql = """
            UPDATE ItemRead
            SET TagID = ?, ReadTime = ?
            WHERE ReadID = ? AND Deleted = FALSE
        """;
        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tagId);
            ps.setTimestamp(2, Timestamp.from(ts));
            ps.setInt(3, readId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                return null;
            }
            String selectSql = """
                SELECT ReadID, TagID, ReadTime, Deleted
                FROM ItemRead
                WHERE ReadID = ?
            """;
            try (PreparedStatement ps2 = c.prepareStatement(selectSql)) {
                ps2.setInt(1, readId);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (rs.next()) {
                        return new ItemRead(
                                rs.getInt("ReadID"),
                                rs.getString("TagID"),
                                rs.getTimestamp("ReadTime").toInstant(),
                                rs.getBoolean("Deleted")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
        }
        return null;
    }
    public boolean softDeleteItemRead(int readId) {
        String sql = """
            UPDATE ItemRead
            SET Deleted = TRUE
            WHERE ReadID = ? AND Deleted = FALSE
        """;

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, readId);
            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            LoggerHandler.log(e);
            return false;
        }
    }
    public List<ItemRead> listReadsForTagInRange(String tagId, Instant from, Instant to) {
        List<ItemRead> out = new ArrayList<>();

        String sql = """
        SELECT ReadID, TagID, ReadTime, Deleted
        FROM ItemRead
        WHERE TagID = ? AND Deleted = FALSE
          AND ReadTime >= ? AND ReadTime <= ?
        ORDER BY ReadTime ASC
    """;

        try (Connection c = ConnectionManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, tagId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ItemRead(
                            rs.getInt("ReadID"),
                            rs.getString("TagID"),
                            rs.getTimestamp("ReadTime").toInstant(),
                            rs.getBoolean("Deleted")
                    ));
                }
            }
        } catch (SQLException e) {
            LoggerHandler.log(e);
        }

        return out;
    }
}
