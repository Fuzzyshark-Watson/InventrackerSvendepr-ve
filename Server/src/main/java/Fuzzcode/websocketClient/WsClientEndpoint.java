package Fuzzcode.websocketClient;

import java.nio.ByteBuffer;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

// WsClientEndpoint.java
public class WsClientEndpoint extends Session.Listener.Abstract
        implements Session.Listener.AutoDemanding {

    @Override public void onWebSocketOpen(Session session) {
        System.out.println("Connected: " + session);
        session.sendText("Hello Server!", new Callback() { public void succeed() { } public void fail(Throwable x) { x.printStackTrace(); }});
    }

    @Override public void onWebSocketText(String message) { System.out.println("Received: " + message); }

    @Override public void onWebSocketBinary(ByteBuffer payload, Callback cb) { cb.succeed(); }

    @Override public void onWebSocketClose(int code, String reason, Callback cb) { System.out.println("Closed " + code + " " + reason); cb.succeed(); }

    @Override public void onWebSocketError(Throwable cause) { cause.printStackTrace(); }
}
