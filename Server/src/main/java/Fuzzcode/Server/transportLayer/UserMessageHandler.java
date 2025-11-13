package Fuzzcode.Server.transportLayer;

import Fuzzcode.Server.model.AppUser;
import Fuzzcode.Server.model.UserRole;
import Fuzzcode.Server.service.UserService;
import Fuzzcode.Server.utilities.LoggerHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public class UserMessageHandler {

    private final UserService userService = new UserService();
    private static final ObjectMapper JSON = new ObjectMapper();

    public UserMessageHandler() {}

    // In:  User.List
    // Out: User.Snapshot
    public String list(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);

            if (!"User.List".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for User.list: " + type);
                return null;
            }

            List<AppUser> users = userService.listAll();

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "User.Snapshot");

            ObjectNode payload = outRoot.putObject("payload");
            ArrayNode arr = payload.putArray("orders");

            for (AppUser u : users) {
                ObjectNode node = arr.addObject();
                node.put("userId", u.userId());
                node.put("username", u.username());
                node.put("role", u.role() != null ? u.role().name() : null);
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "User.Snapshot\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound User.List message", e);
            return null;
        }
    }
    // In:  User.Create // User.Update
    // Out: User.Upsert
    public String upsert(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);

            if (!"User.Create".equals(type) &&
                    !"User.Update".equals(type)) {

                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for User.upsert: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");

            AppUser outUser;

            if ("User.Create".equals(type)) {
                String username = payload.path("username").asText(null);
                String password = payload.path("password").asText(null);
                String roleStr  = payload.path("role").asText(null);

                UserRole role = parseRole(roleStr);
                if (role == null) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Invalid or missing role in User.Create payload: " + roleStr);
                    return null;
                }

                try {
                    outUser = userService.register(username, password, role);
                } catch (Exception ex) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Failed to register user: " + ex.getMessage());
                    return null;
                }
            } else {
                // User.Update
                int userId = payload.path("userId").asInt(0);
                if (userId <= 0) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing or invalid userId in User.Update payload: " + jsonPart);
                    return null;
                }

                String newUsername = payload.hasNonNull("username")
                        ? payload.path("username").asText()
                        : null;
                String newPassword = payload.hasNonNull("password")
                        ? payload.path("password").asText()
                        : null;
                String roleStr = payload.path("role").asText(null);
                UserRole newRole = parseRole(roleStr);

                // Apply changes if present
                if (newUsername != null && !newUsername.isBlank()) {
                    if (!userService.updateUsername(userId, newUsername)) {
                        LoggerHandler.log(LoggerHandler.Level.WARNING,
                                "Failed to update username for user " + userId);
                        return null;
                    }
                }

                if (newPassword != null && !newPassword.isBlank()) {
                    if (!userService.updatePassword(userId, newPassword)) {
                        LoggerHandler.log(LoggerHandler.Level.WARNING,
                                "Failed to update password for user " + userId);
                        return null;
                    }
                }

                if (newRole != null) {
                    if (!userService.updateRole(userId, newRole)) {
                        LoggerHandler.log(LoggerHandler.Level.WARNING,
                                "Failed to update role for user " + userId);
                        return null;
                    }
                }

                outUser = userService.getById(userId);
            }

            if (outUser == null) {
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "User.Upsert");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("userId", outUser.userId());
            outPayload.put("username", outUser.username());
            outPayload.put("role", outUser.role() != null ? outUser.role().name() : null);

            // Again, strongly recommended NOT to send any password/hash:
            // outPayload.put("password", outUser.passwordHash());

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "User.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound User upsert message", e);
            return null;
        }
    }
    // In:  User.Delete
    // Out: User.Deleted
    public String delete(String inboundMessage) {
        int brace = inboundMessage.indexOf('{');
        if (brace < 0) {
            LoggerHandler.log(LoggerHandler.Level.WARNING,
                    "BROKER payload had no JSON: " + inboundMessage);
            return null;
        }

        String jsonPart = inboundMessage.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);

            if (!"User.Delete".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for User.delete: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int userId = payload.path("userId").asInt(0);
            if (userId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid userId in User.Delete payload: " + jsonPart);
                return null;
            }

            boolean ok = userService.deleteUser(userId);
            if (!ok) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Failed to delete user " + userId);
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "User.Deleted");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("userId", userId);

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "User.Deleted\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound User.Delete message", e);
            return null;
        }
    }
    private static UserRole parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UserRole.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
