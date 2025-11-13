package Fuzzcode.Server.service;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.dao.ItemDao;
import Fuzzcode.Server.dao.ItemReadDao;
import Fuzzcode.Server.model.Item;
import Fuzzcode.Server.model.ItemRead;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

public class ItemReadService {

    private static final Duration DUPLICATE_IGNORE_WINDOW = Duration.ofSeconds(2);

    private final ItemDao itemDao = new ItemDao();
    private final ItemReadDao itemReadDao = new ItemReadDao();

    private volatile String lastTag = null;
    private volatile Instant lastReadTime = Instant.EPOCH;

    public ItemRead recordScan(String tagId, String timestampStr) {
        Objects.requireNonNull(tagId, "tagId");
        final Instant nowInstant = parseToInstantOrNow(timestampStr);

        Instant prevTime = lastReadTime;
        String  prevTag  = lastTag;

        if (tagId.equals(prevTag) &&
                Duration.between(prevTime, nowInstant).compareTo(DUPLICATE_IGNORE_WINDOW) < 0) {

            LoggerHandler.log(LoggerHandler.Level.INFO, "⏱ Ignored duplicate read for " + tagId);
            return null; // nothing new stored
        }

        lastTag = tagId;
        lastReadTime = nowInstant;

        Item item = itemDao.readItemByTag(tagId, false);
        if (item == null) {
            LoggerHandler.log(LoggerHandler.Level.WARNING, "⚠ Unknown tag scanned: " + tagId);
            return null;
        }

        int readId = itemReadDao.recordItemRead(tagId, nowInstant);
        if (readId == 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING, "⚠ Failed to insert ItemRead for tag: " + tagId);
            return null;
        }

        LoggerHandler.log("📥 Recorded scan for tag: " + tagId + " as readId=" + readId);
        return new ItemRead(readId, tagId, nowInstant, false);
    }
    public List<ItemRead> listAllActiveReads() {
        return itemReadDao.listAllActiveReads();
    }
    public List<ItemRead> getRecentReads(String tagId, int limit) {
        return itemReadDao.listReadsForTag(tagId, limit);
    }
    public ItemRead updateRead(int readId, String tagId, String timestampStr) {
        Objects.requireNonNull(tagId, "tagId");
        Instant ts = parseToInstantOrNow(timestampStr);

        // Optional: validate that tag exists
        Item item = itemDao.readItemByTag(tagId, false);
        if (item == null) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "⚠ Cannot update ItemRead, unknown tag: " + tagId);
            return null;
        }

        ItemRead updated = itemReadDao.updateItemRead(readId, tagId, ts);
        if (updated == null) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "⚠ Failed to update ItemRead readId=" + readId);
        }
        return updated;
    }
    public boolean deleteRead(int readId) {
        boolean ok = itemReadDao.softDeleteItemRead(readId);
        if (!ok) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "⚠ Failed to delete ItemRead readId=" + readId);
        }
        return ok;
    }
    public List<ItemRead> listReadsForItem(int itemId, Instant from, Instant to) {
        if (from == null) from = Instant.EPOCH;
        if (to == null)   to   = Instant.now();

        Item item = itemDao.readItemById(itemId, false);
        if (item == null) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "Cannot list reads for item " + itemId + ": item not found");
            return List.of();
        }

        return itemReadDao.listReadsForTagInRange(item.tagId(), from, to);
    }
    private static Instant parseToInstantOrNow(String s) {
        if (s == null || s.isBlank()) return Instant.now();

        try {
            return Instant.parse(s); // 2025-02-18T12:34:56Z
        } catch (DateTimeParseException ignore) {}

        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignore) {}

        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            return OffsetDateTime.parse(s, f).toInstant();
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignore) {}

        return Instant.now();
    }

}
