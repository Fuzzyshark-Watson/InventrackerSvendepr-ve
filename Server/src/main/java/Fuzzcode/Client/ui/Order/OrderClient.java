package Fuzzcode.Client.ui.Order;

import Fuzzcode.Server.model.Order;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OrderClient {

    private static final OrderClient INSTANCE = new OrderClient();

    private final OrderStore store = OrderStore.getInstance();
    private final WsClientEndpoint endpoint = WsClientEndpoint.getInstance();

    private static final ObjectMapper JSON = new ObjectMapper();

    private OrderClient() {
        endpoint.addTextListener(this::onMessage);
    }

    public static OrderClient getInstance() { return INSTANCE; }

    // === Outgoing ===
    public void requestAll() {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Order.List");
            root.putObject("payload"); // empty {}

            String json = JSON.writeValueAsString(root);
            endpoint.send("Order.List\n" + json);
        } catch (Exception e) {
            e.printStackTrace(); // or LoggerHandler.log(...)
        }
    }
    public void createOrder(Order order) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Order.Create");
            ObjectNode payload = root.putObject("payload");
            payload.put("createdDate", toStr(order.createdDate()));
            payload.put("startDate",   toStr(order.startDate()));
            payload.put("endDate",     toStr(order.endDate()));
            if (order.customerId() != null) payload.put("customerId", order.customerId());
            else payload.putNull("customerId");
            if (order.loggedById() != null) payload.put("loggedById", order.loggedById());
            else payload.putNull("loggedById");

            String json = JSON.writeValueAsString(root);
            endpoint.send("Order.Create\n" + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void updateOrder(Order order) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Order.Update");
            ObjectNode payload = root.putObject("payload");
            payload.put("orderId", order.orderId());
            payload.put("createdDate", toStr(order.createdDate()));
            payload.put("startDate",   toStr(order.startDate()));
            payload.put("endDate",     toStr(order.endDate()));
            if (order.customerId() != null) payload.put("customerId", order.customerId());
            else payload.putNull("customerId");
            if (order.loggedById() != null) payload.put("loggedById", order.loggedById());
            else payload.putNull("loggedById");

            String json = JSON.writeValueAsString(root);
            endpoint.send("Order.Update\n" + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void deleteOrder(int orderId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Order.Delete");
            ObjectNode payload = root.putObject("payload");
            payload.put("orderId", orderId);

            String json = JSON.writeValueAsString(root);
            endpoint.send("Order.Delete\n" + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static String toStr(LocalDate d) {
        return d == null ? null : d.toString();
    }

    // === Incoming ===
    private void onMessage(String raw) {
        if (raw == null) return;
        // Only handle Order.* messages
        if (!raw.startsWith("Order.")) return;

        int brace = raw.indexOf('{');
        if (brace < 0) {
            // no JSON part => ignore
            return;
        }
        String jsonPart = raw.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (type == null || !type.startsWith("Order.")) return;

            JsonNode payload = root.path("payload");

            switch (type) {
                case "Order.Snapshot" -> {
                    List<Order> orders = parseOrders(payload.path("orders"));
                    store.replaceAll(orders);
                }
                case "Order.Upsert" -> {
                    Order order = parseOrder(payload.path("order"));
                    if (order != null) {
                        store.upsert(order);
                    }
                }
                case "Order.Deleted" -> {
                    int orderId = payload.path("orderId").asInt(0);
                    if (orderId > 0) {
                        store.remove(orderId);
                    }
                }
                default -> {
                    // ignore other Order.* types on client
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === HELPERS ===
    private static List<Order> parseOrders(JsonNode arrNode) {
        List<Order> list = new ArrayList<>();
        if (arrNode == null || !arrNode.isArray()) return list;
        for (JsonNode n : arrNode) {
            Order o = parseOrder(n);
            if (o != null) list.add(o);
        }
        return list;
    }
    private static Order parseOrder(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;

        // In snapshot: node *is* the order object.
        // In upsert: node = payload.order (already unwrapped above).
        int orderId = node.path("orderId").asInt(0);
        if (orderId <= 0) return null;

        LocalDate createdDate = parseDate(node.path("createdDate").asText(null));
        LocalDate startDate   = parseDate(node.path("startDate").asText(null));
        LocalDate endDate     = parseDate(node.path("endDate").asText(null));

        Integer customerId = node.hasNonNull("customerId") ? node.path("customerId").asInt() : null;
        Integer loggedById = node.hasNonNull("loggedById") ? node.path("loggedById").asInt() : null;
        boolean deleted    = node.path("deleted").asBoolean(false);

        return new Order(orderId, createdDate, startDate, endDate, customerId, loggedById, deleted);
    }
    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s); // ISO-8601: yyyy-MM-dd
    }
}
