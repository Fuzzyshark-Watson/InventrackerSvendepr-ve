package Fuzzcode.Server.websocketServer;
import Fuzzcode.Server.apiEndpoint.LoginServlet;
import Fuzzcode.Server.security.AuthContext;
import Fuzzcode.Server.security.JwtAuthenticator;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;

import java.nio.charset.StandardCharsets;

public class WsServerHandler {
    private Server server;
    private final JwtAuthenticator jwtAuth = JwtAuthenticator.buildHmacForTests(
            "system-client",
            "ws-service",
            "e3f7a9c4b8d1f0a2c6e9d4b3f7a8c1e2d3f4b5a6c7d8e9f0a1b2c3d4e5f6a7b8".getBytes(StandardCharsets.UTF_8) //
    );
    public void bootWebsocket() {
        server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new LoginServlet(jwtAuth)), "/api/login");

        JettyWebSocketServletContainerInitializer.configure(context, (sc, container) -> container.addMapping("/ws", (req, res) -> {
            String auth = req.getHeader("Authorization");
            String headerJwt = (auth != null && auth.startsWith("Bearer "))
                    ? auth.substring("Bearer ".length()).trim() : null;

            String queryJwt = java.util.Optional.ofNullable(req.getParameterMap().get("token"))
                    .flatMap(list -> list.stream().findFirst())
                    .orElse(null);

            String token = (headerJwt != null && !headerJwt.isBlank()) ? headerJwt : queryJwt;

            if (token == null || token.isBlank()) {
                System.out.println("[WS] Forbidden: missing token");
                try { res.sendForbidden("Missing token"); } catch (Exception ignore) {}
                return null;
            }

            try {
                AuthContext authC = jwtAuth.verify(token);
                return new WebSocketServer(authC);
            } catch (Exception e) {
                System.out.println("[WS] Forbidden: invalid token - " + e.getMessage());
                try { res.sendForbidden("Invalid token"); } catch (Exception ignore) {}
                return null;
            }
        }));

        try { server.start(); } catch (Exception e) { throw new RuntimeException(e); }
        System.out.println("WebSocket ready at ws://localhost:8080/ws");
        try { server.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }
    public void stopWebsocket() {
        if (server != null && server.isRunning()) {
            try {
                server.stop();
            } catch (Exception e) {
                System.err.println("Failed to stop WebSocket Server: " + e.getMessage());
            }
        }
    }
}

