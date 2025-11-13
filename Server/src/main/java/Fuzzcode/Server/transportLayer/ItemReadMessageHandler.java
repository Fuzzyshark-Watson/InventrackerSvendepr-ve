package Fuzzcode.Server.transportLayer;

import Fuzzcode.Server.model.ItemRead;
import Fuzzcode.Server.service.ItemReadService;
import Fuzzcode.Server.utilities.LoggerHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

public class ItemReadMessageHandler {

    private final ItemReadService itemReadService = new ItemReadService();;
    private static final ObjectMapper JSON = new ObjectMapper();

    public ItemReadMessageHandler() {
    }
    // In:   ItemRead.Create // ItemRead.Update
    // Out:  ItemRead.Upsert
    public String upsert(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);

            if (!"ItemRead.Create".equals(type) &&
                    !"ItemRead.Update".equals(type)) {

                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for upsert: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            String tagId    = payload.path("tagId").asText(null);
            String readTime = payload.path("readTime").asText(null);

            if (tagId == null || tagId.isBlank()) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing tagId in payload: " + jsonPart);
                return null;
            }

            ItemRead result;

            if ("ItemRead.Create".equals(type)) {
                // No readId in payload; create new
                result = itemReadService.recordScan(tagId, readTime);
            } else {
                // Update: payload must contain readId
                int readId = payload.path("readId").asInt(0);
                if (readId <= 0) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing or invalid readId for update: " + jsonPart);
                    return null;
                }
                result = itemReadService.updateRead(readId, tagId, readTime);
            }

            if (result == null) {
                // duplicate, unknown tag, or failure => no outbound message
                return null;
            }

            // Build outbound: ItemRead.Upsert
            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "ItemRead.Upsert");
            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("readId", result.readId());
            outPayload.put("tagId", result.tagId());
            outPayload.put("readTime", result.readTime().toString());

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            // You’ve been prefixing with "ItemRead.Upsert\n"
            return "ItemRead.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound ItemRead upsert message", e);
            return null;
        }
    }
    // In:   ItemRead.List { "type": "ItemRead.List", "payload": {} }
    // Out:  ItemRead.Snapshot { "type": "ItemRead.Snapshot", ... }
    public String readAll(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);

            if (!"ItemRead.List".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for readAll: " + type);
                return null;
            }

            List<ItemRead> reads = itemReadService.listAllActiveReads();

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "ItemRead.Snapshot");

            ObjectNode payload = outRoot.putObject("payload");

            // NOTE: your example calls this "orders" – keeping that for compatibility
            ArrayNode ordersArray = payload.putArray("orders");

            for (ItemRead r : reads) {
                ObjectNode node = ordersArray.addObject();
                // Example shows only tagId + readTime; add readId if you like
                node.put("tagId", r.tagId());
                node.put("readTime", r.readTime().toString());
                // node.put("readId", r.readId()); // optional, but useful
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "ItemRead.Snapshot\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound ItemRead.List message", e);
            return null;
        }
    }
    // In:   ItemRead.Delete { "type": "ItemRead.Delete", "payload": {"readId": 1} }
    // Out:  ItemRead.Deleted { "type": "ItemRead.Deleted", "payload": {"readId": 1} }
    public String delete(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);

            if (!"ItemRead.Delete".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for delete: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int readId = payload.path("readId").asInt(0);
            if (readId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid readId in delete payload: " + jsonPart);
                return null;
            }

            boolean ok = itemReadService.deleteRead(readId);
            if (!ok) {
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "ItemRead.Deleted");
            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("readId", readId);

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "ItemRead.Deleted\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound ItemRead.Delete message", e);
            return null;
        }
    }
    // In:  ItemRead.ListByItem
    // Out: ItemRead.SnapshotForItem
    public String listByItem(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (!"ItemRead.ListByItem".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for ItemRead.ListByItem: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int itemId = payload.path("itemId").asInt(0);
            if (itemId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid itemId in ItemRead.ListByItem payload: " + jsonPart);
                return null;
            }

            String fromStr = payload.path("from").asText(null);
            String toStr   = payload.path("to").asText(null);

            Instant from = parseInstantOrDefault(fromStr, Instant.EPOCH);
            Instant to   = parseInstantOrDefault(toStr, Instant.now());

            List<ItemRead> reads = itemReadService.listReadsForItem(itemId, from, to);

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "ItemRead.SnapshotForItem");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("itemId", itemId);

            ArrayNode arr = outPayload.putArray("reads");
            for (ItemRead r : reads) {
                ObjectNode n = arr.addObject();
                n.put("readId", r.readId());
                n.put("tagId", r.tagId());
                n.put("readTime", r.readTime().toString());
                n.put("deleted", r.deleted());
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "ItemRead.SnapshotForItem\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle ItemRead.ListByItem", e);
            return null;
        }
    }
    // === HELPER ===
    private static Instant parseInstantOrDefault(String s, Instant def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Instant.parse(s); // 2025-02-02T09:32:10Z
        } catch (DateTimeParseException ignore) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException ignore2) {
                return def;
            }
        }
    }
}

