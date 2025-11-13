package Fuzzcode.Client.websocketClient;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class WsClientEndpoint extends Session.Listener.Abstract
        implements Session.Listener.AutoDemanding {

    private static WsClientEndpoint INSTANCE;
    private final WsClient wsClient;

    private final List<Consumer<String>> textListeners = new ArrayList<>();
    private final Consumer<String> log;

    // === SingleTon ===
    private WsClientEndpoint(String uri, Consumer<String> log) throws Exception {
        this.log = log;
        this.wsClient = new WsClient();
        wsClient.start(uri, this);
    }
    public static synchronized void init(String uri, Consumer<String> log) throws Exception {
        if (INSTANCE == null) {
            INSTANCE = new WsClientEndpoint(uri, log);
        }
    }
    public static WsClientEndpoint getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Call WsClientEndpoint.init(...) first");
        }
        return INSTANCE;
    }

    public void addTextListener(Consumer<String> listener) {
        textListeners.add(listener);
    }
    public void send(String text) { wsClient.send(text); }
    @Override
    public void onWebSocketOpen(Session session) {
        log("Connected: " + session);
    }

    @Override
    public void onWebSocketText(String message) {
        String shortMsg = message.length() > 400
                ? message.substring(0, 400) + " ..."
                : message;
        log("WS <- " + shortMsg);
        for (Consumer<String> l : textListeners) {
            l.accept(message);
        }
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback cb) {
        cb.succeed();
    }

    @Override
    public void onWebSocketClose(int code, String reason, Callback cb) {
        log("Closed " + code + " " + reason);
        cb.succeed();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log("Error: " + cause);
        cause.printStackTrace(); // optional
    }

    private void log(String msg) {
        if (log != null) {
            log.accept(msg);
        }
    }
}
