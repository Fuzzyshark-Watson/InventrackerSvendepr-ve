package Fuzzcode.Server.websocketServer;

import Fuzzcode.Server.security.AuthContext;
import Fuzzcode.Server.security.JwtAuthenticator;
import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.utilities.MessageHandler;
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
    private volatile Session session;
    public WebSocketServer(AuthContext auth) {
        this(auth, JwtAuthenticator.buildDefault());
    }
    public WebSocketServer(AuthContext auth, JwtAuthenticator authenticator) {
        this.auth = auth;
    }
    @OnWebSocketOpen
    public void onOpen(Session s) {
        this.session = s;
        ACTIVE.add(s);
        sendSafe("hello " + auth.subject());
    }

    @OnWebSocketMessage
    public void onText(Session s, String msg) {
        System.out.println("[TEST] got WS text: " + msg);
        if (msg == null) return;
        if (auth == null) {
            sendSafe("ERROR:not_authenticated");
            return;
        }

        if (msg.startsWith("Item.List")
                || msg.startsWith("Item.Create")
                || msg.startsWith("Item.Update")
                || msg.startsWith("Item.Delete")
                || msg.startsWith("ItemRead.Create")
                || msg.startsWith("ItemRead.Update")
                || msg.startsWith("ItemRead.Delete")
                || msg.startsWith("ItemRead.List")
                || msg.startsWith("Order.List")
                || msg.startsWith("Order.Create")
                || msg.startsWith("Order.Update")
                || msg.startsWith("Order.Delete")
                || msg.startsWith("OrderItem.ListByOrder")
                || msg.startsWith("OrderItem.PositionCounts")
                || msg.startsWith("ItemRead.ListByItem")
                || msg.startsWith("OrderItem.List")
                || msg.startsWith("OrderItem.Create")
                || msg.startsWith("OrderItem.Update")
                || msg.startsWith("OrderItem.Delete")
                || msg.startsWith("Person.List")
                || msg.startsWith("Person.Create")
                || msg.startsWith("Person.Update")
                || msg.startsWith("Person.Delete")
                || msg.startsWith("User.List")
                || msg.startsWith("User.Create")
                || msg.startsWith("User.Update")
                || msg.startsWith("User.Delete")) {

            MessageHandler
                    .getInstance()
                    .enqueueMessage(msg, outbound -> {
                        sendSafe(outbound);
                    });

            return;
        }
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
    // === HELPERS =========================================================

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

}
