package Fuzzcode.Server.transportLayer;

import Fuzzcode.Server.model.Item;
import Fuzzcode.Server.model.OrderItem;
import Fuzzcode.Server.model.Position;
import Fuzzcode.Server.service.ItemService;
import Fuzzcode.Server.service.OrderItemService;
import Fuzzcode.Server.utilities.LoggerHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class OrderItemMessageHandler {

    private final OrderItemService orderItemService = new OrderItemService();
    private final ItemService itemService = new ItemService();
    private static final ObjectMapper JSON = new ObjectMapper();

    public OrderItemMessageHandler() {}

    // In:  OrderItem.List
    // Out: OrderItem.Snapshot
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

            if (!"OrderItem.List".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for OrderItem.list: " + type);
                return null;
            }

            List<OrderItem> all = orderItemService.listAll(false); // only active

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "OrderItem.Snapshot");

            ObjectNode payload = outRoot.putObject("payload");
            // spec: "orders": [ { orderId, itemId } ... ]
            ArrayNode arr = payload.putArray("orders");

            for (OrderItem oi : all) {
                ObjectNode node = arr.addObject();
                node.put("orderId", oi.orderId());
                node.put("itemId", oi.itemId());
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "OrderItem.Snapshot\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound OrderItem.List message", e);
            return null;
        }
    }

    // In:  OrderItem.Create / OrderItem.Update)
    // Out: OrderItem.Upsert
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

            // Some of your examples say type:"OrderItem.Delete" under Update, which looks like a typo.
            if (!"OrderItem.Create".equals(type) &&
                    !"OrderItem.Update".equals(type)) {

                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for OrderItem.upsert: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");

            int orderId = payload.path("orderId").asInt(0);
            int itemId  = payload.path("itemId").asInt(0);

            if (orderId <= 0 || itemId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid orderId/itemId in OrderItem payload: " + jsonPart);
                return null;
            }

            OrderItem oi;
            try {
                // assignItemToOrder will:
                //  - check order exists
                //  - check item exists
                //  - attach or revive soft-deleted relation
                oi = orderItemService.assignItemToOrder(itemId, orderId);
            } catch (IllegalArgumentException ex) {
                LoggerHandler.log(LoggerHandler.Level.WARNING, ex.getMessage());
                return null;
            }

            if (oi == null) {
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "OrderItem.Upsert");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("orderId", oi.orderId());
            outPayload.put("itemId", oi.itemId());
            outPayload.put("deleted", oi.deleted());

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "OrderItem.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound OrderItem upsert message", e);
            return null;
        }
    }

    // In:  OrderItem.Delete
    // Out: OrderItem.Deleted
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

            if (!"OrderItem.Delete".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for OrderItem.delete: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int orderId = payload.path("orderId").asInt(0);
            int itemId  = payload.path("itemId").asInt(0);

            if (orderId <= 0 || itemId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid orderId/itemId in OrderItem.Delete payload: " + jsonPart);
                return null;
            }

            boolean ok = orderItemService.detachItemFromOrder(itemId, orderId);
            if (!ok) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "No active relation to detach for order " + orderId + ", item " + itemId);
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "OrderItem.Deleted");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("orderId", orderId);
            outPayload.put("itemId", itemId);

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "OrderItem.Deleted\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound OrderItem.Delete message", e);
            return null;
        }
    }

    // In:  OrderItem.ListByOrder
    // Out: OrderItem.SnapshotForOrder
    public String listByOrder(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "OrderItem.ListByOrder payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (!"OrderItem.ListByOrder".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for OrderItem.listByOrder: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int orderId = payload.path("orderId").asInt(0);
            if (orderId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid orderId in payload: " + jsonPart);
                return null;
            }

            List<OrderItem> relations = orderItemService.getItemsInOrder(orderId, false);
            LoggerHandler.log("Order " + orderId + " contains " + relations.size() + " item(s)");

            // Build outbound JSON
            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "OrderItem.SnapshotForOrder");
            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("orderId", orderId);

            ArrayNode arr = outPayload.putArray("items");
            for (OrderItem oi : relations) {
                ObjectNode relNode = arr.addObject();
                relNode.put("orderId", oi.orderId());
                relNode.put("itemId", oi.itemId());
                relNode.put("deleted", oi.deleted());

                Item item = itemService.getItemById(oi.itemId(), true);
                if (item != null) {
                    ObjectNode itemNode = relNode.putObject("item");
                    itemNode.put("itemId",   item.itemId());
                    itemNode.put("tagId",    item.tagId());
                    itemNode.put("position", item.position().name());
                    if (item.isOverdue() != null) {
                        itemNode.put("isOverdue", item.isOverdue());
                    } else {
                        itemNode.putNull("isOverdue");
                    }
                    itemNode.put("deleted",  item.deleted());
                }
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "OrderItem.SnapshotForOrder\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle OrderItem.ListByOrder", e);
            return null;
        }
    }

    // In:  OrderItem.PositionCounts
    // Out: OrderItem.PositionCounts
    public String positionCounts(String inboundMessage) {
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
            if (!"OrderItem.PositionCounts".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for OrderItem.PositionCounts: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int orderId = payload.path("orderId").asInt(0);
            if (orderId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid orderId in OrderItem.PositionCounts payload: " + jsonPart);
                return null;
            }

            // Use ItemService / ItemDao to list items for order
            List<Item> items = itemService.listItemsForOrder(orderId, false);

            // Count per Position
            Map<Position, Integer> counts = new EnumMap<>(Position.class);
            for (Position p : Position.values()) {
                counts.put(p, 0);
            }
            for (Item item : items) {
                Position p = item.position() != null ? item.position() : Position.HOME;
                counts.put(p, counts.get(p) + 1);
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "OrderItem.PositionCounts");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("orderId", orderId);

            ObjectNode countsNode = outPayload.putObject("counts");
            for (Position p : Position.values()) {
                countsNode.put(p.name(), counts.getOrDefault(p, 0));
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "OrderItem.PositionCounts\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle OrderItem.PositionCounts", e);
            return null;
        }
    }
}
