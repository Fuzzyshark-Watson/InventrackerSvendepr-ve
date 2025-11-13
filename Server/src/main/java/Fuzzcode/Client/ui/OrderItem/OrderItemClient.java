package Fuzzcode.Client.ui.OrderItem;

import Fuzzcode.Client.ui.models.ReadsTableModel;
import Fuzzcode.Server.model.OrderItem;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class OrderItemClient {

    private static final OrderItemClient INSTANCE = new OrderItemClient();
    public static OrderItemClient getInstance() { return INSTANCE; }

    private final WsClientEndpoint endpoint = WsClientEndpoint.getInstance();
    private static final ObjectMapper JSON = new ObjectMapper();

    // UI model to update (optional but pragmatic)
    private Fuzzcode.Client.ui.models.ReadsTableModel readsModel;

    private OrderItemClient() {
        endpoint.addTextListener(this::onMessage);
    }

    public void bindReadsModel(Fuzzcode.Client.ui.models.ReadsTableModel model) {
        this.readsModel = model;
    }

    // === Outgoing ===
    public void requestAll() {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "OrderItem.List");
            root.putObject("payload");
            endpoint.send("OrderItem.List\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void attach(int orderId, int itemId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "OrderItem.Create");
            ObjectNode payload = root.putObject("payload");
            payload.put("orderId", orderId);
            payload.put("itemId", itemId);

            endpoint.send("OrderItem.Create\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void detach(int orderId, int itemId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "OrderItem.Delete");
            ObjectNode payload = root.putObject("payload");
            payload.put("orderId", orderId);
            payload.put("itemId", itemId);
            payload.put("deleted", true);

            endpoint.send("OrderItem.Delete\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void requestItemsForOrder(int orderId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "OrderItem.ListByOrder");
            ObjectNode payload = root.putObject("payload");
            payload.put("orderId", orderId);

            String json = JSON.writeValueAsString(root);
            endpoint.send("OrderItem.ListByOrder\n" + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === Incoming ===
    private void onMessage(String raw) {
        if (raw == null || !raw.startsWith("OrderItem.")) return;
        int brace = raw.indexOf('{');
        if (brace < 0) return;
        String jsonPart = raw.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (!"OrderItem.SnapshotForOrder".equals(type)) return;

            JsonNode payload = root.path("payload");
            int orderId = payload.path("orderId").asInt(0);

            List<ReadsTableModel.Row> rows = new ArrayList<>();
            for (JsonNode rel : payload.path("items")) {
                JsonNode itemNode = rel.path("item");
                int itemId       = itemNode.path("itemId").asInt(0);
                String tagId     = itemNode.path("tagId").asText("");
                String position  = itemNode.path("position").asText("");
                boolean overdue  = itemNode.path("isOverdue").asBoolean(false);
                rows.add(new ReadsTableModel.Row(itemId, tagId, position, overdue));
            }

            if (readsModel != null) {
                SwingUtilities.invokeLater(() ->
                        readsModel.setSnapshotForOrder(orderId, rows)
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === HELPERS ===
    private static List<OrderItem> parseOrderItems(JsonNode arr) {
        List<OrderItem> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode n : arr) {
            OrderItem oi = parseOrderItem(n);
            if (oi != null) list.add(oi);
        }
        return list;
    }
    private static OrderItem parseOrderItem(JsonNode n) {
        if (n == null || n.isNull()) return null;
        int orderId = n.path("orderId").asInt(0);
        int itemId  = n.path("itemId").asInt(0);
        if (orderId <= 0 || itemId <= 0) return null;
        boolean deleted = n.path("deleted").asBoolean(false);
        return new OrderItem(orderId, itemId, deleted);
    }
}

