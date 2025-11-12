package Fuzzcode.websocketServer;

import Fuzzcode.db.ConnectionManager;
import Fuzzcode.security.AuthContext;
import Fuzzcode.security.JwtAuthenticator;
import Fuzzcode.utilities.LoggerHandler;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
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
        if (msg.equals("fetch : orders_full")) {
            try (Connection conn = ConnectionManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                    SELECT OrderID, CreatedDate, StartDate, EndDate, CustomerID, LoggedByID
                    FROM Orders WHERE Deleted = FALSE
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String created = rs.getDate("CreatedDate").toString();
                        Date start = rs.getDate("StartDate");
                        Date end   = rs.getDate("EndDate");
                        int cust   = rs.getInt("CustomerID");
                        int logged = rs.getInt("LoggedByID");
                        sendSafe(String.format("order:%d:%s:%s:%s:%d:%d",
                                rs.getInt("OrderID"),
                                created,
                                start == null ? "" : start.toString(),
                                end   == null ? "" : end.toString(),
                                cust,
                                logged));
                    }
                }
                try (PreparedStatement ps2 = conn.prepareStatement("""
                        SELECT oi.OrderID, i.ItemID, i.Position
                        FROM OrderItems oi
                        JOIN Items i ON i.ItemID = oi.ItemID
                        WHERE oi.Deleted = FALSE AND i.Deleted = FALSE
                        """)) {
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        while (rs2.next()) {
                            sendSafe(String.format("link:%d:%d:%s",
                                    rs2.getInt("OrderID"),
                                    rs2.getInt("ItemID"),
                                    rs2.getString("Position")));
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
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
