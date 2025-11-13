package Fuzzcode.Client.ui.Components;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Client.websocketClient.WsClient;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;

import javax.swing.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainUI  {
    // === WebSocket ===
    private final WsClient wsClient = new WsClient();

    // === login state ===
    private String jwtToken;

    // === UI ===
    private MainWindow mainWindow;
    // === Orders UI + state ===

    // === Log Panel ===
    public void startLogin(){
        new LoginWindow(this).setVisible(true);
    }
    void startMainWindow(String username) {
        mainWindow = new MainWindow(this, username);
        mainWindow.setVisible(true);
    }
    public boolean handleLogin(String username, char[] password) {
        try {
            String passString = new String(password);

            String token = doHttpLogin(username, passString); // returns JWT or null
            if (token == null) {
                JOptionPane.showMessageDialog(null,
                        "Invalid username or password.",
                        "Login failed", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            this.jwtToken = token;

            connectWebSocketWithToken();
            startMainWindow(username);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Login error: " + e.getMessage(),
                    "Login error", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
    private void connectWebSocketWithToken() {
        try {
            String uri = "ws://localhost:8080/ws?token=" +
                    URLEncoder.encode(jwtToken, StandardCharsets.UTF_8);

            WsClientEndpoint.init(uri, this::log);

            log("WS connected from Main UI, with JWT");

        } catch (Exception e) {
            log("WS connect failed: " + e.getMessage());
            JOptionPane.showMessageDialog(mainWindow,
                    "WebSocket connection failed:\n" + e.getMessage(),
                    "WS Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private String doHttpLogin(String username, String password) throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();

        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build();
        LoggerHandler.log("HTTP Request: " + request);
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        LoggerHandler.log("HTTP Response: " + response);

        if (response.statusCode() != 200) {
            return null;
        }
        String json = response.body();
        int start = json.indexOf("\"REGISTER\":\"");
        if (start < 0) return null;
        start += "\"REGISTER\":\"".length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    // === For LOG PANEL ===
    private void log(String s) {
        if (mainWindow != null) {
            mainWindow.appendToLog(s);
        } else {
            System.out.println(s);
        }
    }
}
