package Fuzzcode.Client.ui.User;

import Fuzzcode.Server.model.AppUser;
import Fuzzcode.Server.model.UserRole;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UserClient {

    private static final UserClient INSTANCE = new UserClient();
    public static UserClient getInstance() { return INSTANCE; }

    private final UserStore store = UserStore.getInstance();
    private final WsClientEndpoint endpoint = WsClientEndpoint.getInstance();
    private static final ObjectMapper JSON = new ObjectMapper();

    private UserClient() {
        endpoint.addTextListener(this::onMessage);
    }

    // === Outgoing ========================================================

    public void requestAll() {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "User.List");
            root.putObject("payload");
            endpoint.send("User.List\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void createUser(String username, String password, UserRole role) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "User.Create");
            ObjectNode payload = root.putObject("payload");
            payload.put("username", username);
            payload.put("password", password); // server hashes it
            payload.put("role", role.name());
            endpoint.send("User.Create\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void updateUserRole(int userId, UserRole newRole) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "User.Update");
            ObjectNode payload = root.putObject("payload");
            payload.put("userId", userId);
            payload.put("role", newRole.name());
            endpoint.send("User.Update\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void deleteUser(int userId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "User.Delete");
            ObjectNode payload = root.putObject("payload");
            payload.put("userId", userId);
            endpoint.send("User.Delete\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // === Incoming ========================================================

    private void onMessage(String raw) {
        if (raw == null || !raw.startsWith("User.")) return;

        int brace = raw.indexOf('{');
        if (brace < 0) return;
        String jsonPart = raw.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (type == null || !type.startsWith("User.")) return;

            JsonNode payload = root.path("payload");

            switch (type) {
                case "User.Snapshot" -> {
                    List<AppUser> users = parseUsers(payload.path("orders"));
                    store.replaceAll(users);
                }
                case "User.Upsert" -> {
                    AppUser u = parseUser(payload);
                    if (u != null) store.upsert(u);
                }
                case "User.Deleted" -> {
                    int id = payload.path("userId").asInt(0);
                    if (id > 0) store.remove(id);
                }
                default -> { }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<AppUser> parseUsers(JsonNode arr) {
        List<AppUser> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode n : arr) {
            AppUser u = parseUser(n);
            if (u != null) list.add(u);
        }
        return list;
    }

    private static AppUser parseUser(JsonNode n) {
        if (n == null || n.isNull()) return null;
        int id = n.path("userId").asInt(0);
        if (id <= 0) return null;
        String username = n.path("username").asText(null);
        String roleStr  = n.path("role").asText("USER");
        UserRole role   = UserRole.fromDb(roleStr); // or valueOf(roleStr)
        // No password/hash from server for security; fill dummy:
        return new AppUser(id, username, null, null, role, Instant.EPOCH);
    }
}

