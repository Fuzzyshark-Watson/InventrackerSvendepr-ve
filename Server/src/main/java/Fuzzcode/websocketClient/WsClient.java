package Fuzzcode.websocketClient;

import org.eclipse.jetty.websocket.client.WebSocketClient;
import java.net.URI;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

// WsClient.java
public class WsClient {
    private final WebSocketClient client = new WebSocketClient();
    private volatile Session session;

    public void start(String uri, Session.Listener listener) throws Exception {
        if (!client.isStarted()) client.start();
        this.session = client.connect(listener, URI.create(uri)).get();
    }

    public boolean isOpen() { return session != null && session.isOpen(); }

    public void send(String text) {
        if (!isOpen()) throw new IllegalStateException("WebSocket not open");
        session.sendText(text, Callback.NOOP);
    }

    public void close() { if (session != null) session.close(); }

    public void stop() throws Exception { close(); if (client.isStarted()) client.stop(); }
}
