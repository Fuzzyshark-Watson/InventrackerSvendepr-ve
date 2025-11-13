package Fuzzcode.utilities;

import Fuzzcode.db.ConnectionManager;
import Fuzzcode.service.ItemReadService;
import Fuzzcode.service.OrderItemService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
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
        if (message.startsWith("BROKER")) {
            int brace = message.indexOf('{');
            if (brace < 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING, "BROKER payload had no JSON: " + message);
                return;
            }
            System.out.println(message);

            String jsonPart = message.substring(brace);

            JsonNode root  = JSON.readTree(jsonPart);
            String  tagId  = root.path("data").path("idHex").asString(null);
            String  ts     = root.path("timestamp").asString(null);

            if (tagId == null || tagId.isBlank()) {
                LoggerHandler.log(LoggerHandler.Level.WARNING, "Missing tag id in payload: " + jsonPart);
                return;
            }
            itemReadService.recordScan(tagId, ts);
        }
        else {
            System.out.println("Unknown message type");
        }
    }
}