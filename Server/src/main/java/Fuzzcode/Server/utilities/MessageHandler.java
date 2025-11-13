package Fuzzcode.Server.utilities;

import Fuzzcode.Server.transportLayer.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class MessageHandler {

    private static final MessageHandler INSTANCE = new MessageHandler();

    // To help make sure that everything is returned on the same connection.
    private static final class Job {
        final String message;
        final Consumer<String> reply;

        Job(String message, Consumer<String> reply) {
            this.message = message;
            this.reply = reply;
        }
    }
    private final BlockingQueue<Job> messageQueue = new LinkedBlockingQueue<>();

    // HANDLERS
    private final ItemReadMessageHandler itemReadMessageHandler = new ItemReadMessageHandler();
    private final ItemMessageHandler itemMessageHandler = new ItemMessageHandler();
    private final OrderMessageHandler orderMessageHandler = new OrderMessageHandler();
    private final OrderItemMessageHandler orderItemMessageHandler = new OrderItemMessageHandler();
    private final PersonMessageHandler personMessageHandler = new PersonMessageHandler();
    private final UserMessageHandler userMessageHandler = new UserMessageHandler();

    // SETUP
    private MessageHandler() {
        startProcessing();
    }
    public static MessageHandler getInstance() {
        return INSTANCE;
    }


    public void enqueueMessage(String message) {
        enqueueMessage(message, null);
    }
    public void enqueueMessage(String message, Consumer<String> reply) {
        try {
            messageQueue.put(new Job(message, reply));
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
                    Job job = messageQueue.take();
                    processMessage(job);
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

    private void processMessage(Job job) {
        String message = job.message;
        String outbound = null;

        // === ORDERS ===
        if (message.startsWith("Order.List")) {
            outbound = orderMessageHandler.list(message);
        }
        else if (message.startsWith("Order.Create")
                || message.startsWith("Order.Update")) {
            outbound = orderMessageHandler.upsert(message);
        }
        else if (message.startsWith("Order.Delete")) {
            outbound = orderMessageHandler.delete(message);
        }


        // ==== ORDER ITEMS ===
        if (message.startsWith("OrderItem.ListByOrder")) {
            outbound = orderItemMessageHandler.listByOrder(message);
        }
        else if (message.startsWith("OrderItem.PositionCounts")) {
            outbound = orderItemMessageHandler.positionCounts(message);
        }
        else if (message.startsWith("OrderItem.List")) {
            outbound = orderItemMessageHandler.list(message);
        }
        else if (message.startsWith("OrderItem.Create")
                || message.startsWith("OrderItem.Update")) {
            outbound = orderItemMessageHandler.upsert(message);
        }
        else if (message.startsWith("OrderItem.Delete")) {
            outbound = orderItemMessageHandler.delete(message);
        }


        // === ITEMS ===
        if (message.startsWith("Item.List")) {
            outbound = itemMessageHandler.list(message);
        }
        else if (message.startsWith("Item.Create")
                || message.startsWith("Item.Update")) {
            outbound = itemMessageHandler.upsert(message);
        }
        else if (message.startsWith("Item.Delete")) {
            outbound = itemMessageHandler.delete(message);
        }


        // === ITEMREADS ===
        if (message.startsWith("ItemRead.ListByItem")) {
            outbound = itemReadMessageHandler.listByItem(message);
        }
        else if (message.startsWith("ItemRead.List")) {
            outbound = itemReadMessageHandler.readAll(message);
        }
        else if (message.startsWith("BrokerItemRead.Create")) {
            outbound = itemReadMessageHandler.upsert(message);
        }
        else if (message.startsWith("ItemRead.Create")
                || message.startsWith("ItemRead.Update")) {
            outbound = itemReadMessageHandler.upsert(message);
        }
        else if (message.startsWith("ItemRead.Delete")) {
            outbound = itemReadMessageHandler.delete(message);
        }


        // ===== PERSON ===
        if (message.startsWith("Person.List")) {
            outbound = personMessageHandler.list(message);
        }
        else if (message.startsWith("Person.Create")
                || message.startsWith("Person.Update")) {
            outbound = personMessageHandler.upsert(message);
        }
        else if (message.startsWith("Person.Delete")) {
            outbound = personMessageHandler.delete(message);
        }


        // ===== USERS ===
        if (message.startsWith("User.List")) {
            outbound = userMessageHandler.list(message);
        }
        else if (message.startsWith("User.Create")
                || message.startsWith("User.Update")) {
            outbound = userMessageHandler.upsert(message);
        }
        else if (message.startsWith("User.Delete")) {
            outbound = userMessageHandler.delete(message);
        }


        // === Finally +++
        if (outbound != null && job.reply != null) {
            job.reply.accept(outbound);
        } else if (outbound == null) {
            System.out.println("No outbound message produced");
        }
    }

}
