package Fuzzcode;

import Fuzzcode.broker.BrokerHandler;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.db.DatabaseInitializer;
import Fuzzcode.db.SampleDataSeeder;
import Fuzzcode.security.JwtAuthenticator;
import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.utilities.MessageHandler;
import Fuzzcode.websocketClient.WsClient;
import Fuzzcode.websocketClient.WsClientEndpoint;
import Fuzzcode.websocketServer.WsServerHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) throws Exception {
        LoggerHandler.log("=== START Main ====");
        LoggerHandler.log("Connection Manager Initialized");
        ConnectionManager.init(
                "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "admin",
                "root"
        );
        DatabaseInitializer.initSchema();
        //SampleDataSeeder.seed();
        MessageHandler MesH = MessageHandler.getInstance();
        LoggerHandler.log("MessageHandler Initialized");

        // Start Broker (With Subscriber)
        BrokerHandler brokerHandler = new BrokerHandler();
        brokerHandler.startBroker();
        brokerHandler.startSubscriber("PC", "FXR90CBBF41/data/read");
        LoggerHandler.log("BrokerHandler Initialized");

        // Start WebSocket Server
        WsServerHandler wsServerHandler = new WsServerHandler();
        Thread wsServerThread = new Thread(wsServerHandler::bootWebsocket, "WebSocket-Server-Thread");
        wsServerThread.setDaemon(true);
        wsServerThread.start();
        LoggerHandler.log("Websocket Server Initialized");

        // Start WebSocket Client with JWT token
        WsClient wsClient = new WsClient();
        byte[] SECRET = "e3f7a9c4b8d1f0a2c6e9d4b3f7a8c1e2d3f4b5a6c7d8e9f0a1b2c3d4e5f6a7b8"
                .getBytes(StandardCharsets.UTF_8);

        String token = JwtAuthenticator.issueHmacTestToken(
                "system-client",
                "ws-service",
                SECRET,
                "admin",
                "admin",
                3600
        );

        Thread wsClientThread = new Thread(() -> {
            try {
                wsClient.start("ws://localhost:8080/ws?token=" +
                        URLEncoder.encode(token, StandardCharsets.UTF_8), new WsClientEndpoint());
                wsClient.send("ping");
                wsClient.stop();
                System.out.println("WebSocket Client connected with JWT token.");
            } catch (Exception e) {
                System.err.println("WebSocket Client failed: " + e.getMessage());
            }
        }, "WebSocket-Client-Thread");
        wsClientThread.setDaemon(true);
        wsClientThread.start();
        LoggerHandler.log("Websocket Client Initialized with JWT Token: " + token);

        // Shutdown control
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch stopSignal = new CountDownLatch(1);

        // Console listener for ENTER
        Thread stopper = new Thread(() -> {
            System.out.println("Server running. Press ENTER to stop.");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                br.readLine();
            } catch (IOException ignored) {}
            running.set(false);
            stopSignal.countDown();
        }, "stop-listener");
        stopper.setDaemon(true);
        stopper.start();

        // Handle Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            stopSignal.countDown();
        }, "shutdown-hook"));

        // Main loop
        while (running.get()) {
            try {
                Thread.sleep(200);
                // Optional: health checks
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        stopSignal.await();

        try {
            System.out.println("Stopping WebSocket Client...");
            wsClient.close();
        } catch (Throwable t) {
            LoggerHandler.log("WS Client shutdown error: " + t.getMessage());
        }

        try {
            System.out.println("Stopping Broker...");
            brokerHandler.stopBroker();
        } catch (Throwable t) {
            LoggerHandler.log("Broker shutdown error: " + t.getMessage());
        }

        try {
            System.out.println("Stopping WebSocket Server...");
            wsServerHandler.stopWebsocket();
        } catch (Throwable t) {
            LoggerHandler.log("WS Server shutdown error: " + t.getMessage());
        }

        try {
            ConnectionManager.close();
        } catch (Throwable t) {
            LoggerHandler.log("DB close error: " + t.getMessage());
        }

        LoggerHandler.outputReport();
        System.out.println("Stopped cleanly.");
    }
}