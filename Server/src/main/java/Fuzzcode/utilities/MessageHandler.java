package Fuzzcode.utilities;

import Fuzzcode.service.ItemReadService;
import Fuzzcode.service.OrderItemService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MessageHandler {
    private final ItemReadService itemReadService = new ItemReadService();
    private final OrderItemService orderItemService = new OrderItemService();

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MessageHandler INSTANCE = new MessageHandler();
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    private MessageHandler() {
        startProcessing();
    }
    public static MessageHandler getInstance() {
        return INSTANCE;
    }
    public void enqueueMessage(String message) {
        try {
            messageQueue.put(message);
            LoggerHandler.log("Message queued: " + message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggerHandler.log("=== THREAD enqueueMessage ended! ===");
            LoggerHandler.log("ERROR: Failed to enqueue message: " + e.getMessage());
        }
    }
    public void startProcessing() {
        LoggerHandler.log("=== Start startProcessing ===");
        Thread processor = new Thread(() -> {
            while (true) {
                try {
                    String message = messageQueue.take();
                    processMessage(message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LoggerHandler.log("=== THREAD startProcessing ended! ===");
                    break;
                }
            }
        });
        processor.setDaemon(true);
        processor.start();
    }
    private void processMessage(String message) {
        LoggerHandler.log("MessageHandling Logic is still missing, but message received!");
        String prefix;
        if (message.startsWith("WEBSOCKET")) {
            prefix = "WEBSOCKET";
        } else if (message.startsWith("BROKER")) {
            prefix = "BROKER";
        } else {
            prefix = "UNKNOWN";
        }
        switch (prefix) {
            case "WEBSOCKET":
                System.out.println("Handle WebSocket message");
                //TODO Websocket Method
                break;
            case "BROKER":
                try {
                    int brace = message.indexOf('{');
                    if (brace < 0) {
                        LoggerHandler.log(LoggerHandler.Level.WARNING, "BROKER payload had no JSON: " + message);
                        return;
                    }
                    String jsonPart = message.substring(brace);

                    JsonNode root  = JSON.readTree(jsonPart);
                    String  tagId  = root.path("data").path("idHex").asString(null);
                    String  ts     = root.path("timestamp").asString(null);

                    if (tagId == null || tagId.isBlank()) {
                        LoggerHandler.log(LoggerHandler.Level.WARNING, "Missing tag id in payload: " + jsonPart);
                        return;
                    }

                    itemReadService.recordScan(tagId, ts);

            } catch (Exception e) {
                LoggerHandler.log(LoggerHandler.Level.ERROR, "BROKER parse/handle failed: " + e.getMessage());
            }
                break;
            default:
                System.out.println("Unknown message type");
                break;
        }
    }


}