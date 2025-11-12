package Fuzzcode.dao;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.model.ItemRead;

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
}
