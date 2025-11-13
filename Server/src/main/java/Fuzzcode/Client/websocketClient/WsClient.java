package Fuzzcode.Client.websocketClient;

import org.eclipse.jetty.websocket.client.WebSocketClient;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

public class WsClient {
    private final WebSocketClient client = new WebSocketClient();
    private volatile Session session;
    private final ScheduledExecutorService keepAlive =
            Executors.newSingleThreadScheduledExecutor();

    // === START ===
    public void start(String uri, Session.Listener listener) throws Exception {
        if (!client.isStarted())
            client.start();

        client.setMaxTextMessageSize(512 * 1024*2*2*2);

        this.session = client.connect(listener, URI.create(uri)).get();
        startKeepAlive();
    }
    private void startKeepAlive() {
        keepAlive.scheduleAtFixedRate(() -> {
            try {
                if (isOpen()) {
                    session.sendPing(ByteBuffer.wrap(new byte[]{}), Callback.NOOP);
                }
            } catch (Throwable ignore) { }
        }, 15, 25, TimeUnit.SECONDS);
    }

    // === SEND ===
    public void send(String text) {
        if (!isOpen()) throw new IllegalStateException("WebSocket not open");
        session.sendText(text, Callback.NOOP);
    }

    // === STOP ===
    public void close() { if (session != null) session.close(); }
    public void stop() throws Exception { close(); if (client.isStarted()) client.stop(); }

    // === HELLO? ===
    public boolean isOpen() { return session != null && session.isOpen(); }
}
