package Fuzzcode.websocketServer;

import Fuzzcode.security.AuthContext;
import Fuzzcode.security.JwtAuthenticator;
import Fuzzcode.utilities.LoggerHandler;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket
public class WebSocketServer {
    private static final Set<Session> ACTIVE =
            ConcurrentHashMap.newKeySet();

    public static int activeCount() { return ACTIVE.size(); }

    private volatile AuthContext auth;
    private final JwtAuthenticator authenticator; // inject instead of static buildDefault
    private volatile Session session;

    public WebSocketServer(AuthContext auth) {
        this(auth, JwtAuthenticator.buildDefault()); // fallback if you don’t wire DI
    }
    public WebSocketServer(AuthContext auth, JwtAuthenticator authenticator) {
        this.auth = auth;
        this.authenticator = authenticator;
    }

    @OnWebSocketOpen
    public void onOpen(Session s) {
        this.session = s;
        ACTIVE.add(s);
        sendSafe("hello " + auth.subject());
    }

    @OnWebSocketMessage
    public void onText(Session s, String msg) {
        if (msg == null) return;

        if (msg.startsWith("AUTH ")) {
            String newToken = msg.substring(5).trim();
            try {
                AuthContext newAuth = authenticator.verify(newToken);
                this.auth = newAuth;
                sendSafe("auth_ok:" + newAuth.subject());
            } catch (Exception e) {
                sendSafe("auth_failed");
                closeSafe(1008, "Authentication failed");
            }
            return;
        }

        // Normal echo (includes identity)
        sendSafe("echo@" + auth.subject() + ": " + msg);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        Session s = this.session;
        if (s != null) {
            ACTIVE.remove(s);
            this.session = null;
        }
         LoggerHandler.log(LoggerHandler.Level.ERROR, "WS close [" + statusCode + "] " + reason);
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        Session s = this.session;
        if (s != null) {
            ACTIVE.remove(s);
        }
         LoggerHandler.log(LoggerHandler.Level.ERROR, cause.toString());
    }

    // ---- helpers ------------------------------------------------------------

    private void sendSafe(String text) {
        Session s = this.session;
        if (s == null || !s.isOpen()) return;
        s.sendText(text, Callback.NOOP);
    }

    private void closeSafe(int code, String reason) {
        Session s = this.session;
        if (s == null) return;
        s.close(code, reason, Callback.NOOP);
    }

    public void pingIfIdle() {
        Session s = this.session;
        if (s == null || !s.isOpen()) return;
        s.sendText("ping", Callback.NOOP);
    }
}
