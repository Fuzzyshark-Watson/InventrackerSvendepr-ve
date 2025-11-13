package Fuzzcode.Client.ui.ItemRead;

import Fuzzcode.Server.model.ItemRead;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ItemReadClient {

    private static final ItemReadClient INSTANCE = new ItemReadClient();
    public static ItemReadClient getInstance() { return INSTANCE; }

    private final ItemReadStore store = ItemReadStore.getInstance();
    private final WsClientEndpoint endpoint = WsClientEndpoint.getInstance();
    private static final ObjectMapper JSON = new ObjectMapper();

    private ItemReadClient() {
        endpoint.addTextListener(this::onMessage);
    }
    // === Outgoing ===
    public void requestAll() {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "ItemRead.List");
            root.putObject("payload");
            endpoint.send("ItemRead.List\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void createRead(String tagId, String readTimeIso) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "ItemRead.Create");
            ObjectNode payload = root.putObject("payload");
            payload.put("tagId", tagId);
            payload.put("readTime", readTimeIso);

            endpoint.send("ItemRead.Create\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void deleteRead(int readId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "ItemRead.Delete");
            ObjectNode payload = root.putObject("payload");
            payload.put("readId", readId);

            endpoint.send("ItemRead.Delete\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void requestForItem(int itemId, String fromIso, String toIso) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "ItemRead.ListByItem");
            ObjectNode payload = root.putObject("payload");
            payload.put("itemId", itemId);
            payload.put("from", fromIso);
            payload.put("to", toIso);

            endpoint.send("ItemRead.ListByItem\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // === Incoming ===
    private void onMessage(String raw) {
        if (raw == null || !raw.startsWith("ItemRead.")) return;

        int brace = raw.indexOf('{');
        if (brace < 0) return;
        String jsonPart = raw.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (type == null || !type.startsWith("ItemRead.")) return;

            JsonNode payload = root.path("payload");

            switch (type) {
                case "ItemRead.Snapshot" -> {
                    List<ItemRead> list = parseReads(payload.path("orders")); // your schema uses "orders"
                    store.replaceAll(list);
                }
                case "ItemRead.Upsert" -> {
                    ItemRead r = parseRead(payload);
                    if (r != null) store.upsert(r);
                }
                case "ItemRead.Deleted" -> {
                    int readId = payload.path("readId").asInt(0);
                    if (readId > 0) store.remove(readId);
                }
                case "ItemRead.SnapshotForItem" -> {
                    // You might want a special per-item store; or just merge into global:
                    List<ItemRead> list = parseReads(payload.path("reads"));
                    // For now, we just replace all – you can refine this later.
                    store.replaceAll(list);
                }
                default -> { /* ignore */ }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static List<ItemRead> parseReads(JsonNode arr) {
        List<ItemRead> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode n : arr) {
            ItemRead r = parseRead(n);
            if (r != null) list.add(r);
        }
        return list;
    }
    private static ItemRead parseRead(JsonNode n) {
        if (n == null || n.isNull()) return null;
        int readId = n.path("readId").asInt(0);
        String tagId = n.path("tagId").asText(null);
        String ts = n.path("readTime").asText(null);
        Instant t = ts != null && !ts.isBlank() ? Instant.parse(ts) : Instant.EPOCH;
        boolean deleted = n.path("deleted").asBoolean(false);
        return new ItemRead(readId, tagId, t, deleted);
    }
}
