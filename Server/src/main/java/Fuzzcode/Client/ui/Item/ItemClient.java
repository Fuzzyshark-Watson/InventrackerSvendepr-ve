package Fuzzcode.Client.ui.Item;

import Fuzzcode.Server.model.Item;
import Fuzzcode.Server.model.Position;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class ItemClient {

    private static final ItemClient INSTANCE = new ItemClient();
    public static ItemClient getInstance() { return INSTANCE; }

    private final ItemStore store = ItemStore.getInstance();
    private final WsClientEndpoint endpoint = WsClientEndpoint.getInstance();
    private static final ObjectMapper JSON = new ObjectMapper();

    private ItemClient() {
        endpoint.addTextListener(this::onMessage);
    }
    // === Outgoing ===
    public void requestAll() {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Item.List");
            root.putObject("payload");
            endpoint.send("Item.List\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void createItem(String tagId, Position position, boolean overdue) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Item.Create");
            ObjectNode payload = root.putObject("payload");
            payload.put("tagId", tagId);
            payload.put("position", position.name());
            payload.put("isOverdue", overdue);

            endpoint.send("Item.Create\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void updateItem(Item item) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Item.Update");
            ObjectNode payload = root.putObject("payload");
            payload.put("itemId", item.itemId());
            payload.put("position", item.position().name());
            payload.put("isOverdue", item.isOverdue() != null && item.isOverdue());

            endpoint.send("Item.Update\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void deleteItem(int itemId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Item.Delete");
            ObjectNode payload = root.putObject("payload");
            payload.put("itemId", itemId);

            endpoint.send("Item.Delete\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    // === Incoming ===
    private void onMessage(String raw) {
        if (raw == null || !raw.startsWith("Item.")) return;

        int brace = raw.indexOf('{');
        if (brace < 0) return;
        String jsonPart = raw.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (type == null || !type.startsWith("Item.")) return;

            JsonNode payload = root.path("payload");

            switch (type) {
                case "Item.Snapshot" -> {
                    List<Item> items = parseItems(payload.path("orders"));
                    store.replaceAll(items);
                }
                case "Item.Upsert" -> {
                    Item item = parseItem(payload);
                    if (item != null) store.upsert(item);
                }
                case "Item.Deleted" -> {
                    int itemId = payload.path("itemId").asInt(0);
                    if (itemId > 0) store.remove(itemId);
                }
                default -> { /* ignore */ }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static List<Item> parseItems(JsonNode arr) {
        List<Item> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode n : arr) {
            Item i = parseItem(n);
            if (i != null) list.add(i);
        }
        return list;
    }
    private static Item parseItem(JsonNode n) {
        if (n == null || n.isNull()) return null;
        int itemId = n.path("itemId").asInt(0); // may be 0 for snapshot if server omitted it
        String tagId = n.path("tagId").asText(null);
        String positionStr = n.path("position").asText("HOME");
        Position pos = Position.fromString(positionStr);
        Boolean overdue = n.hasNonNull("isOverdue") ? n.path("isOverdue").asBoolean() : null;
        boolean deleted = n.path("deleted").asBoolean(false);
        return new Item(itemId, tagId, pos, overdue, deleted);
    }
}
