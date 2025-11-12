package Fuzzcode.websocketServer;

import Fuzzcode.security.AuthContext;
import Fuzzcode.security.JwtAuthenticator;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

@org.eclipse.jetty.websocket.api.annotations.WebSocket
public class WebSocketServer {
    private volatile AuthContext auth;
    private volatile Session session;

    public WebSocketServer(AuthContext auth) {
        this.auth = auth;
    }
    @OnWebSocketOpen
    public void onOpen(Session session) {
        this.session = session;
        session.sendText("hello " + auth.subject(), Callback.NOOP);
    }
    @OnWebSocketMessage
    public void onText(Session session, String msg) {
        // Optional: support in-band re-auth without reconnecting
        if (msg.startsWith("AUTH ")) {
            String newToken = msg.substring(5).trim();
            try {
                // Re-use the same rules used at handshake. If you’ve got a shared instance, call that.
                AuthContext newAuth = JwtAuthenticator.buildDefault().verify(newToken);
                this.auth = newAuth;
                session.sendText("auth_ok:" + newAuth.subject(), Callback.NOOP);
            } catch (Exception e) {
                session.sendText("auth_failed", Callback.NOOP);
                session.close(1008, "Authentication failed", Callback.NOOP);
            }
            return;
        }

        // Normal messages: you already have identity in auth
        session.sendText("echo@" + auth.subject() + ": " + msg, Callback.NOOP);
    }
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        System.out.printf("Closed: %d %s%n", statusCode, reason);
    }
}
