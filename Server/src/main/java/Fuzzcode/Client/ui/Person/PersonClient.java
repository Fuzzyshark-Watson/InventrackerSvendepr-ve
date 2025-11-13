package Fuzzcode.Client.ui.Person;

import Fuzzcode.Server.model.Person;
import Fuzzcode.Server.model.PersonRole;
import Fuzzcode.Client.websocketClient.WsClientEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class PersonClient {

    private static final PersonClient INSTANCE = new PersonClient();
    public static PersonClient getInstance() { return INSTANCE; }

    private final PersonStore store = PersonStore.getInstance();
    private final WsClientEndpoint endpoint = WsClientEndpoint.getInstance();
    private static final ObjectMapper JSON = new ObjectMapper();

    private PersonClient() {
        endpoint.addTextListener(this::onMessage);
    }

    // === Outgoing ===
    public void requestAll() {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Person.List");
            root.putObject("payload");
            endpoint.send("Person.List\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void createPerson(String name, PersonRole role) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Person.Create");
            ObjectNode payload = root.putObject("payload");
            payload.put("name", name);
            payload.put("role", role.name());
            endpoint.send("Person.Create\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void updatePerson(Person p) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Person.Update");
            ObjectNode payload = root.putObject("payload");
            payload.put("personId", p.personId());
            payload.put("name", p.name());
            payload.put("role", p.role().name());
            endpoint.send("Person.Update\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void deletePerson(int personId) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("type", "Person.Delete");
            ObjectNode payload = root.putObject("payload");
            payload.put("personId", personId);
            endpoint.send("Person.Delete\n" + JSON.writeValueAsString(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // === Incoming ===
    private void onMessage(String raw) {
        if (raw == null || !raw.startsWith("Person.")) return;

        int brace = raw.indexOf('{');
        if (brace < 0) return;
        String jsonPart = raw.substring(brace).trim();

        try {
            JsonNode root = JSON.readTree(jsonPart);
            String type = root.path("type").asText(null);
            if (type == null || !type.startsWith("Person.")) return;

            JsonNode payload = root.path("payload");

            switch (type) {
                case "Person.Snapshot" -> {
                    List<Person> list = parsePeople(payload.path("orders"));
                    store.replaceAll(list);
                }
                case "Person.Upsert" -> {
                    Person p = parsePerson(payload);
                    if (p != null) store.upsert(p);
                }
                case "Person.Deleted" -> {
                    int id = payload.path("personId").asInt(0);
                    if (id > 0) store.remove(id);
                }
                default -> { }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static List<Person> parsePeople(JsonNode arr) {
        List<Person> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode n : arr) {
            Person p = parsePerson(n);
            if (p != null) list.add(p);
        }
        return list;
    }
    private static Person parsePerson(JsonNode n) {
        if (n == null || n.isNull()) return null;
        int id = n.path("personId").asInt(0);
        if (id <= 0) return null;
        String name = n.path("name").asText(null);
        String roleStr = n.path("role").asText("CUSTOMER");
        PersonRole role = PersonRole.valueOf(roleStr);
        boolean deleted = n.path("deleted").asBoolean(false);
        return new Person(id, name, role, deleted);
    }
}

