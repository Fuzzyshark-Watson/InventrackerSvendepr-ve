package Fuzzcode.Server.transportLayer;

import Fuzzcode.Server.model.Order;
import Fuzzcode.Server.service.OrderService;
import Fuzzcode.Server.utilities.LoggerHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public class OrderMessageHandler {

    private final OrderService orderService = new OrderService();
    private static final ObjectMapper JSON = new ObjectMapper();

    public OrderMessageHandler() {}

    // ---------------------------------------------------------------------
    // LIST -> Order.Snapshot
    // In:  Order.List { "type": "Order.List", "payload": {} }
    // Out: Order.Snapshot { "type": "Order.Snapshot", "payload": { orders: [...] } }
    // ---------------------------------------------------------------------
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

            if (!"Order.List".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Order.list: " + type);
                return null;
            }

            List<Order> orders = orderService.listActiveOrders();

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Order.Snapshot");

            ObjectNode payload = outRoot.putObject("payload");
            ArrayNode ordersArray = payload.putArray("orders");

            for (Order o : orders) {
                ObjectNode node = ordersArray.addObject();
                node.put("orderId", o.orderId());
                // Dates as yyyy-MM-dd
                if (o.createdDate() != null) node.put("createdDate", o.createdDate().toString());
                if (o.startDate()   != null) node.put("startDate",   o.startDate().toString());
                if (o.endDate()     != null) node.put("endDate",     o.endDate().toString());
                if (o.customerId()  != null) node.put("customerId",  o.customerId());
                if (o.loggedById()  != null) node.put("loggedById",  o.loggedById());
                node.put("deleted", o.deleted());
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Order.Snapshot\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Order.List message", e);
            return null;
        }
    }
    // CREATE / UPDATE -> Order.Upsert
    // In:   Order.Create
    // Out:  Order.Upsert
    // NOTE: createdDate from client is *ignored* on update; DB value is kept.
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

            if (!"Order.Create".equals(type) &&
                    !"Order.Update".equals(type)) {

                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Order.upsert: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            LocalDate createdDate = parseLocalDate(payload.path("createdDate").asText(null));
            LocalDate startDate   = parseLocalDate(payload.path("startDate").asText(null));
            LocalDate endDate     = parseLocalDate(payload.path("endDate").asText(null));

            Integer customerId = payload.path("customerId").isNull()
                    ? null
                    : payload.path("customerId").isInt()
                    ? payload.path("customerId").asInt()
                    : null;

            Integer loggedById = payload.path("loggedById").isNull()
                    ? null
                    : payload.path("loggedById").isInt()
                    ? payload.path("loggedById").asInt()
                    : null;

            Order outOrder;

            if ("Order.Create".equals(type)) {
                outOrder = orderService.createOrder(createdDate, customerId, loggedById);
                if (outOrder == null) return null;
                if (startDate != null || endDate != null) {
                    boolean ok = orderService.updateOrderDates(outOrder.orderId(), startDate, endDate);
                    if (!ok) {
                        LoggerHandler.log(LoggerHandler.Level.WARNING,
                                "Failed to set dates for newly created order " + outOrder.orderId());
                    }
                    outOrder = orderService.getOrder(outOrder.orderId(), true);
                }
            } else {
                int orderId = payload.path("orderId").asInt(0);
                if (orderId <= 0) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing or invalid orderId in Order.Update payload: " + jsonPart);
                    return null;
                }
                if (!orderService.updateOrderDates(orderId, startDate, endDate)) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Failed to update dates for order " + orderId);
                    return null;
                }
                if (customerId != null) {
                    if (!orderService.softDeleteOrder(orderId) && false) {
                    }
                }

                outOrder = orderService.getOrder(orderId, true);
                if (outOrder == null) return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Order.Upsert");
            ObjectNode payloadOut = outRoot.putObject("payload");
            ObjectNode orderNode = payloadOut.putObject("order");

            orderNode.put("orderId", outOrder.orderId());
            if (outOrder.createdDate() != null) orderNode.put("createdDate", outOrder.createdDate().toString());
            if (outOrder.startDate()   != null) orderNode.put("startDate",   outOrder.startDate().toString());
            if (outOrder.endDate()     != null) orderNode.put("endDate",     outOrder.endDate().toString());
            if (outOrder.customerId()  != null) orderNode.put("customerId",  outOrder.customerId());
            if (outOrder.loggedById()  != null) orderNode.put("loggedById",  outOrder.loggedById());
            orderNode.put("deleted", outOrder.deleted());

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Order.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Order upsert message", e);
            return null;
        }
    }
    // DELETE -> returns Order.Upsert with deleted=true
    // In:   ItemRead.Delete { "type": "ItemRead.List", "payload": {} }
    // Out:  ItemRead.Deleted { "type": "ItemRead.Snapshot", ... }
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

            if (!"Order.Delete".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Order.delete: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int orderId = payload.path("orderId").asInt(0);
            if (orderId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid orderId in Order.Delete payload: " + jsonPart);
                return null;
            }

            boolean ok = orderService.softDeleteOrder(orderId);
            if (!ok) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Failed to soft-delete order " + orderId);
                return null;
            }

            Order o = orderService.getOrder(orderId, true);
            if (o == null) {
                return null;
            }
            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Order.Upsert"); // or "Order.Deleted" if you prefer

            ObjectNode payloadOut = outRoot.putObject("payload");
            ObjectNode orderNode = payloadOut.putObject("order");

            orderNode.put("orderId", o.orderId());
            if (o.createdDate() != null) orderNode.put("createdDate", o.createdDate().toString());
            if (o.startDate()   != null) orderNode.put("startDate",   o.startDate().toString());
            if (o.endDate()     != null) orderNode.put("endDate",     o.endDate().toString());
            if (o.customerId()  != null) orderNode.put("customerId",  o.customerId());
            if (o.loggedById()  != null) orderNode.put("loggedById",  o.loggedById());
            orderNode.put("deleted", o.deleted()); // should now be true

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Order.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Order.Delete message", e);
            return null;
        }
    }
    // === Helpers ===
    private static LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s); // expects yyyy-MM-dd
        } catch (DateTimeParseException e) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "Failed to parse LocalDate from '" + s + "', ignoring");
            return null;
        }
    }
}
