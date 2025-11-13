package Fuzzcode.Server.apiEndpoint;

import Fuzzcode.Server.security.JwtAuthenticator;
import Fuzzcode.Server.service.UserService;
import org.mindrot.jbcrypt.BCrypt;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class LoginServlet extends HttpServlet {

    private final JwtAuthenticator jwtAuth;

    public LoginServlet(JwtAuthenticator jwtAuth) {
        this.jwtAuth = jwtAuth;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        UserService userService = new UserService();
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"missing username or password\"}");
            return;
        }

        var user = userService.getByUsername(username);
        if (user == null || !BCrypt.checkpw(password, user.passwordHash())) {
            unauthorized(resp, "invalid credentials");
            return;
        }

        String role = user.role().name(); // or however your AppUser exposes it

        String token = jwtAuth.issueToken(
                username,
                role,
                3600
        );

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json");
        resp.getWriter().write("{\"REGISTER\":\"" + token + "\"}");
    }
    private void unauthorized(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json");
        resp.getWriter().write("{\"ERROR\":\"" + msg + "\"}");
    }
}

