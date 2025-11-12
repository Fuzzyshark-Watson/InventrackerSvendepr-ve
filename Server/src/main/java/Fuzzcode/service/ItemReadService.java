package Fuzzcode.service;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.dao.ItemDao;
import Fuzzcode.dao.ItemReadDao;
import Fuzzcode.model.Item;
import Fuzzcode.model.ItemRead;

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

    public void recordScan(String tagId, String timestampStr) {
        Objects.requireNonNull(tagId, "tagId");
        final Instant nowInstant = parseToInstantOrNow(timestampStr);

        Instant prevTime = lastReadTime;
        String  prevTag  = lastTag;

        if (tagId.equals(prevTag) && Duration.between(prevTime, nowInstant).compareTo(DUPLICATE_IGNORE_WINDOW) < 0) {
            LoggerHandler.log(LoggerHandler.Level.INFO, "⏱ Ignored duplicate read for " + tagId);
            return;
        }

        lastTag = tagId;
        lastReadTime = nowInstant;

        Item item = itemDao.readItemByTag(tagId, false);
        if (item == null) {
            LoggerHandler.log(LoggerHandler.Level.WARNING, "⚠ Unknown tag scanned: " + tagId);
            return;
        }

        itemReadDao.recordItemRead(tagId, nowInstant);
        LoggerHandler.log("📥 Recorded scan for tag: " + tagId);
    }
    public List<ItemRead> getRecentReads(String tagId, int limit) {
        return itemReadDao.listReadsForTag(tagId, limit);
    }
    private static Instant parseToInstantOrNow(String s) {
        if (s == null || s.isBlank()) return Instant.now();

        try {
            return Instant.parse(s); // Format : 2025-02-18T12:34:56Z
        } catch (DateTimeParseException ignore) {}

        try {
            return OffsetDateTime.parse(s).toInstant(); // Format : 2025-02-18T12:34:56Z
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
