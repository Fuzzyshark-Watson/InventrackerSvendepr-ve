package Fuzzcode;

import Fuzzcode.Server.broker.BrokerHandler;
import Fuzzcode.Server.db.ConnectionManager;
import Fuzzcode.Server.db.DatabaseInitializer;
import Fuzzcode.Client.ui.Components.MainUI;
import Fuzzcode.Client.ui.styles.Styles;
import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.utilities.MessageHandler;
import Fuzzcode.Server.websocketServer.WsServerHandler;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {


    public static void main(String[] args) throws Exception {

        LoggerHandler.log("=== START Main ====");

        try {
            ConnectionManager.init(
                    "jdbc:h2:file:./data/prod_db;MODE=MySQL;AUTO_SERVER=TRUE",
                    "admin",
                    "root"
            );
            DatabaseInitializer.initSchema();
            LoggerHandler.log("Connection Manager Initialized");

        } catch (Exception e) {
            e.printStackTrace();
            LoggerHandler.log(e);
        }
       try {
            //SampleDataSeeder.seed();
        } catch (Exception e) {
            e.printStackTrace();
            LoggerHandler.log(e);
        }

        MessageHandler MesH = MessageHandler.getInstance();
        try {
            MesH.startProcessing();
            LoggerHandler.log("MessageHandler Initialized");

        } catch (Exception e) {
            e.printStackTrace();
            LoggerHandler.log(e);
        }

        BrokerHandler brokerHandler = new BrokerHandler();
        try {
            brokerHandler.startBroker();
            brokerHandler.startSubscriber("PC", "FXR90CBBF41/data/read");
            LoggerHandler.log("BrokerHandler Initialized");
        } catch (Exception e) {
            e.printStackTrace();
            LoggerHandler.log(e);

            return;
        }

        WsServerHandler wsServerHandler = new WsServerHandler();
        Thread wsServerThread = new Thread(wsServerHandler::bootWebsocket, "WebSocket-Server-Thread");
        try {
            wsServerThread.setDaemon(true);
            wsServerThread.start();
            LoggerHandler.log("Websocket Server Initialized");
        } catch (Exception e) {
            e.printStackTrace();
            LoggerHandler.log(e);
        }
        LoggerHandler.outputReport();

        Styles.apply();
        SwingUtilities.invokeLater(() -> new MainUI().startLogin());

        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch stopSignal = new CountDownLatch(1);

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
            //wsClient.close();
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
    public void initializeUI(){

    }
}