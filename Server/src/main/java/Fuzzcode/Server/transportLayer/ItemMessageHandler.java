package Fuzzcode.Server.transportLayer;

import Fuzzcode.Server.model.Item;
import Fuzzcode.Server.model.Position;
import Fuzzcode.Server.service.ItemService;
import Fuzzcode.Server.utilities.LoggerHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public class ItemMessageHandler {

    private final ItemService itemService = new ItemService();
    private static final ObjectMapper JSON = new ObjectMapper();

    public ItemMessageHandler() {}

    // In:  Item.List
    // Out: Item.Snapshot
    public String list(String inboundMessage) {
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

            if (!"Item.List".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Item.list: " + type);
                return null;
            }

            List<Item> items = itemService.listActiveItems();

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Item.Snapshot");

            ObjectNode payload = outRoot.putObject("payload");
            // spec says "orders" here – keeping that for compatibility
            ArrayNode arr = payload.putArray("orders");

            for (Item it : items) {
                ObjectNode node = arr.addObject();
                node.put("itemId", it.itemId());
                node.put("tagId", it.tagId());
                node.put("position", it.position() != null ? it.position().name() : Position.HOME.name());
                node.put("isOverdue", it.isOverdue() != null && it.isOverdue());
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Item.Snapshot\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Item.List message", e);
            return null;
        }
    }

    // In:  Item.Create // Item.Update
    // Out: Item.Upsert
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

            if (!"Item.Create".equals(type) &&
                    !"Item.Update".equals(type)) {

                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Item.upsert: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");

            Item result;

            if ("Item.Create".equals(type)) {
                // tagId required
                String tagId = payload.path("tagId").asText(null);
                if (tagId == null || tagId.isBlank()) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing tagId in Item.Create payload: " + jsonPart);
                    return null;
                }

                String posStr = payload.path("position").asText(null);
                Position pos = Position.fromString(posStr);
                if (pos == null) pos = Position.HOME;

                boolean overdue = payload.path("isOverdue").asBoolean(false);

                result = itemService.createItem(tagId, pos, overdue);

            } else {
                // Item.Update
                int itemId = payload.path("itemId").asInt(0);
                if (itemId <= 0) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing or invalid itemId in Item.Update payload: " + jsonPart);
                    return null;
                }

                // position required for update in your contract
                String posStr = payload.path("position").asText(null);
                Position pos = Position.fromString(posStr);
                if (pos == null) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing or invalid position in Item.Update payload: " + jsonPart);
                    return null;
                }

                boolean overdue = payload.path("isOverdue").asBoolean(false);

                boolean moved = itemService.moveItem(itemId, pos);
                boolean over  = itemService.markOverdue(itemId, overdue);

                if (!moved && !over) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Failed to update item " + itemId);
                    return null;
                }

                result = itemService.getItemById(itemId, true);
            }

            if (result == null) {
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Item.Upsert");
            ObjectNode outPayload = outRoot.putObject("payload");

            outPayload.put("itemId", result.itemId());
            outPayload.put("tagId", result.tagId());
            outPayload.put("position", result.position() != null ? result.position().name() : Position.HOME.name());
            outPayload.put("isOverdue", result.isOverdue() != null && result.isOverdue());

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Item.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Item upsert message", e);
            return null;
        }
    }

    // IN:  Item.Delete
    // Out: Item.Deleted
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

            if (!"Item.Delete".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Item.delete: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int itemId = payload.path("itemId").asInt(0);
            if (itemId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid itemId in Item.Delete payload: " + jsonPart);
                return null;
            }

            boolean ok = itemService.deleteItem(itemId);
            if (!ok) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Failed to delete item " + itemId);
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Item.Deleted");
            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("itemId", itemId);

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Item.Deleted\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Item.Delete message", e);
            return null;
        }
    }
}
