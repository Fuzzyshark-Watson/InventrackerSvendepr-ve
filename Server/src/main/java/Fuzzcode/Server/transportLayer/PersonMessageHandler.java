package Fuzzcode.Server.transportLayer;

import Fuzzcode.Server.model.Person;
import Fuzzcode.Server.model.PersonRole;
import Fuzzcode.Server.service.PersonService;
import Fuzzcode.Server.utilities.LoggerHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public class PersonMessageHandler {

    private final PersonService personService = new PersonService();
    private static final ObjectMapper JSON = new ObjectMapper();

    public PersonMessageHandler() {}

    // In:  Person.List
    // Out: Person.Snapshot
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

            if (!"Person.List".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Person.list: " + type);
                return null;
            }

            List<Person> people = personService.listPeople();

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Person.Snapshot");

            ObjectNode payload = outRoot.putObject("payload");
            // spec calls this "orders" – keep it for compatibility
            ArrayNode arr = payload.putArray("orders");

            for (Person p : people) {
                ObjectNode node = arr.addObject();
                node.put("personId", p.personId());
                node.put("name", p.name());
                node.put("role", p.role() != null ? p.role().name() : null);
            }

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Person.Snapshot\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Person.List message", e);
            return null;
        }
    }
    // In:  Person.Create // Person.Update
    // Out: Person.Upsert
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

            if (!"Person.Create".equals(type) &&
                    !"Person.Update".equals(type)) {

                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Person.upsert: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            String name = payload.path("name").asText(null);
            String roleStr = payload.path("role").asText(null);

            if (name == null || name.isBlank()) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing name in Person payload: " + jsonPart);
                return null;
            }

            PersonRole role = parseRole(roleStr);
            if (role == null) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Invalid role in Person payload: " + roleStr);
                return null;
            }

            Person outPerson;

            if ("Person.Create".equals(type)) {
                outPerson = personService.createPerson(name, role);
            } else { // Person.Update
                int personId = payload.path("personId").asInt(0);
                if (personId <= 0) {
                    LoggerHandler.log(LoggerHandler.Level.WARNING,
                            "Missing or invalid personId in Person.Update payload: " + jsonPart);
                    return null;
                }
                outPerson = personService.updatePerson(personId, name, role);
            }

            if (outPerson == null) {
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Person.Upsert");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("personId", outPerson.personId());
            outPayload.put("name", outPerson.name());
            outPayload.put("role", outPerson.role() != null ? outPerson.role().name() : null);

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Person.Upsert\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Person upsert message", e);
            return null;
        }
    }
    // In:  Person.Delete
    // Out: Person.Deleted
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

            if (!"Person.Delete".equals(type)) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Unsupported type for Person.delete: " + type);
                return null;
            }

            JsonNode payload = root.path("payload");
            int personId = payload.path("personId").asInt(0);
            if (personId <= 0) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Missing or invalid personId in Person.Delete payload: " + jsonPart);
                return null;
            }

            boolean ok = personService.removePerson(personId);
            if (!ok) {
                LoggerHandler.log(LoggerHandler.Level.WARNING,
                        "Failed to delete person " + personId);
                return null;
            }

            ObjectNode outRoot = JSON.createObjectNode();
            outRoot.put("type", "Person.Deleted");

            ObjectNode outPayload = outRoot.putObject("payload");
            outPayload.put("personId", personId);
            outPayload.put("deleted", true);

            String outboundJson = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(outRoot);

            return "Person.Deleted\n" + outboundJson;

        } catch (Exception e) {
            LoggerHandler.log(LoggerHandler.Level.ERROR,
                    "Failed to handle inbound Person.Delete message", e);
            return null;
        }
    }
    private static PersonRole parseRole(String s) {
        if (s == null) return null;
        try {
            return PersonRole.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

