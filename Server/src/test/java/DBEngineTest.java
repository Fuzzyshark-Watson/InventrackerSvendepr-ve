import Fuzzcode.security.AuthContext;
import Fuzzcode.security.JwtAuthenticator;
import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.websocketServer.WebSocketServer;
import Fuzzcode.db.ConnectionManager;
import Fuzzcode.db.DatabaseInitializer;
import Fuzzcode.model.*;
import Fuzzcode.model.Order;
import Fuzzcode.service.*;


import com.nimbusds.jose.JOSEException;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Session;


import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

public class DBEngineTest {
    ItemReadService itemReadService = new ItemReadService();
    ItemService itemService = new ItemService();
    OrderItemService orderItemService = new OrderItemService();
    OrderService orderService = new OrderService();
    PersonService personService = new PersonService();
    UserService userService = new UserService();

    private final boolean outputLogs = true;
    // Note to self: The test setup is made "In Memory"

    // Nested, so I can make a setup "BeforeEach" that doesn't clash between tests.
    @Nested
    class LoginTests {
        @AfterEach
        void teardown() {
            if (outputLogs) {
                LoggerHandler.outputReport();
                LoggerHandler.clear();
            }
        }
        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init(
                    "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "admin",
                    "root"
            );
            DatabaseInitializer.initSchema();
            userService.register("admin", "root", UserRole.ADMIN);
            LoggerHandler.log("=== END setup ===");
        }
        @Test
        void testLoginRightUserWrongPw() {
            LoggerHandler.log("Testing === testLoginRightUserWrongPw");
            boolean result = userService.login("admin", "wrongpassword");
            assertFalse(result, "Login should fail with incorrect credentials");
        }
        @Test
        void testLoginWrongUserRightPw() {
            LoggerHandler.log("Testing === testLoginWrongUserRightPw");
            boolean result = userService.login("wrongUser", "root");
            assertFalse(result, "Login should fail with incorrect credentials");
        }
        @Test
        void testLoginWrongUserWrongPw() {
            LoggerHandler.log("Testing === testLoginWrongUserWrongPw");
            boolean result = userService.login("wrongUser", "wrongpassword");
            assertFalse(result, "Login should fail with incorrect credentials");
        }
        @Test
        void testLoginRightUserRightPw() {
            LoggerHandler.log("Testing === testLoginRightUserRightPw");
            boolean result = userService.login("admin", "root");
            assertTrue(result, "Login should succeed with correct credentials");
        }
    }
    @Nested
    class DBSetupTests {
        @AfterEach
        void teardown() {
            if (outputLogs) {
                LoggerHandler.outputReport();
                LoggerHandler.clear();
            }
        }
        @Test
        void InitializeDBFail() {
            LoggerHandler.log("=== START InitializeDBFail ===");

            ConnectionManager.resetForTests();
            String badUrl = "jdbc:h2:file:./target/tmp/does_not_exist;IFEXISTS=TRUE";
            String user = "sa";
            String pass = "";

            assertThrows(RuntimeException.class, () -> {
                ConnectionManager.init(badUrl, user, pass);
            });

            LoggerHandler.log("=== END InitializeDBFail ===");
        }
        @Test
        void InitializeDBSuccess() {
            LoggerHandler.log("=== START InitializeDBSuccess ===");
            String url = "jdbc:h2:mem:testdb_success;DB_CLOSE_DELAY=-1";
            String user = "admin";
            String pass = "root";
            assertDoesNotThrow(() -> {
                ConnectionManager.init(url, user, pass);
                DatabaseInitializer.initSchema();
            });
            LoggerHandler.log("Database Init did not Throw");
            assertTrue(ConnectionManager.isInitialized(), "DB should be initialized");

            assertDoesNotThrow(() -> {
                try (var c = ConnectionManager.getConnection();
                     var rs = c.getMetaData().getTables(null, null, "PEOPLE", null)) {

                    assertTrue(rs.next(), "Expected table PEOPLE to exist after schema initialization");
                }
            });
            LoggerHandler.log("Database Tables Created");
            LoggerHandler.log("=== END InitializeDBSuccess ===");
        }
    }
    @Nested
    class PersonTests {
        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init("jdbc:h2:mem:test_person;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();
            personService = new PersonService();
            personService.createPerson("TestUser", PersonRole.CUSTOMER);
            LoggerHandler.log("=== END setup ===");
        }

        @AfterEach
        void teardown() {
            if (outputLogs) LoggerHandler.outputReport();
            LoggerHandler.clear();
            ConnectionManager.close();
        }

        @Test
        void testCreateAndReadPerson() {
            LoggerHandler.log("=== START testCreateAndReadPerson ===");
            Person testPerson = personService.createPerson("Alice", PersonRole.DRIVER);
            int id = testPerson.personId();
            assertTrue(id > 0);
            LoggerHandler.log(testPerson.toString());

            assertEquals("Alice", testPerson.name());
            assertEquals(PersonRole.DRIVER, testPerson.role());
            assertFalse(testPerson.deleted());
            LoggerHandler.log("Read Succesful!");
            LoggerHandler.log("=== END testCreateAndReadPerson ===");
        }

        @Test
        void testUpdateRole() {
            LoggerHandler.log("=== START testUpdateRole ===");
            Person testPerson = personService.getPerson(1);
            assertNotNull(testPerson);
            LoggerHandler.log(testPerson.toString());

            assertTrue(personService.updateRole(testPerson.personId(), PersonRole.USER));

            Person updated = personService.getPerson(testPerson.personId(), false);

            assertEquals(PersonRole.USER, updated.role());
            LoggerHandler.log(updated.toString());
            LoggerHandler.log("=== END testUpdateRole ===");
        }

        @Test
        void testSoftDeletePerson() {
            LoggerHandler.log("=== START testSoftDeletePerson ===");
            Person testPerson = personService.getPerson(1);
            assertNotNull(testPerson);
            LoggerHandler.log("Person before Soft Delete - PersonID: " + testPerson.personId() +
                    " - Name: " + testPerson.name() +
                    " - Role: " + testPerson.role() +
                    " - Deleted: " + testPerson.deleted());
            assertTrue(personService.removePerson(testPerson.personId()));
            Person updated = personService.getPerson(1, true);

            assertNull(personService.getPerson(updated.personId(), false));
            assertTrue(personService.getPerson(updated.personId(), true).deleted());
            LoggerHandler.log("Person after Soft Delete - PersonID: " + updated.personId() +
                    " - Name: " + updated.name() +
                    " - Role: " + updated.role() +
                    " - Deleted: " + updated.deleted());
            LoggerHandler.log("=== END testSoftDeletePerson ===");
        }
    }
    @Nested
    class OrderTests {
        Person OrderCustomer;
        Person OrderLogger;
        Person DeletedCustomer;
        Person DeletedLogger;
        Order order;

        @BeforeEach
        void setup()  {
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init("jdbc:h2:mem:test_person;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();
            LoggerHandler.log("=== END setup ===");
        }
        @AfterEach
        public void teardown() {
            if (outputLogs) {
                LoggerHandler.outputReport();
            }
            LoggerHandler.clear();
        }
        @Test
        public void testCreateOrder() {
            LoggerHandler.log("=== START testCreateOrder ===");
            OrderCustomer = personService.createPerson("OrderCustomer", PersonRole.CUSTOMER);
            LoggerHandler.log("Person Created: " + OrderCustomer.toString());
            OrderLogger = personService.createPerson("OrderLogger", PersonRole.USER);
            LoggerHandler.log("Person Created: " + OrderLogger.toString());

            order= orderService.createOrder(LocalDate.now().plusDays(1), OrderCustomer.personId(), OrderLogger.personId());
            LoggerHandler.log("Order Created: " + order.toString());
            LoggerHandler.log("=== END testCreateOrder ===");
        }
        @Test
        public void testReadOrderById() {
            LoggerHandler.log("=== START testReadOrderById ===");
            OrderCustomer = personService.createPerson("OrderCustomer", PersonRole.CUSTOMER);
            LoggerHandler.log("Person Created: " + OrderCustomer.toString());
            OrderLogger = personService.createPerson("OrderLogger", PersonRole.USER);
            LoggerHandler.log("Person Created: " + OrderLogger.toString());
            order = orderService.createOrder(LocalDate.now().plusDays(1), OrderCustomer.personId(),
                    OrderLogger.personId());
            LoggerHandler.log("Order Created: " + order.toString());
            Order getOrder = orderService.getOrder(order.orderId());
            LoggerHandler.log("order.orderId returns - " + getOrder.toString());
            LoggerHandler.log("=== END testReadOrderById ===");
        }
        @Test
        void testUpdateOrderDates() {
            LoggerHandler.log("=== START testUpdateOrderDates ===");

            var customer = personService.createPerson("OrderCustomer", PersonRole.CUSTOMER);
            LoggerHandler.log("Person Created: " + customer);

            var LoggerHandlerPerson = personService.createPerson("OrderLogger", PersonRole.USER);
            LoggerHandler.log("Person Created: " + LoggerHandlerPerson);

            LocalDate today = LocalDate.now();
            var order = orderService.createOrder(today, customer.personId(), LoggerHandlerPerson.personId());
            LoggerHandler.log("Order Created: " + order);

            LocalDate threeDaysAgo = today.minusDays(3);
            LocalDate nextWeek = today.plusDays(7);

            assertTrue(orderService.updateOrderStartDate(order.orderId(), threeDaysAgo),
                    "Should update start date");

            var afterStart = orderService.getOrder(order.orderId(), false);
            assertNotNull(afterStart.startDate(), "Start date should be set");
            assertEquals(threeDaysAgo, afterStart.startDate(), "Start date should be 3 days ago");
            LoggerHandler.log("Order afterStart: " + afterStart);
            assertTrue(orderService.updateOrderEndDate(order.orderId(), nextWeek),
                    "Should update end date");

            var afterEnd = orderService.getOrder(order.orderId(), false);
            assertNotNull(afterEnd.endDate(), "End date should be set");
            assertEquals(nextWeek, afterEnd.endDate(), "End date should be next week");
            LoggerHandler.log("Order afterEnd: " + afterEnd);

            assertTrue(orderService.updateOrderDates(order.orderId(), threeDaysAgo, nextWeek),
                    "Should update both dates");
            var afterBoth = orderService.getOrder(order.orderId(), false);
            assertEquals(threeDaysAgo, afterBoth.startDate());
            assertEquals(nextWeek, afterBoth.endDate());
            LoggerHandler.log("Order afterBoth: " + afterBoth);

            LoggerHandler.log("=== END testUpdateOrderDates ===");
        }
        @Test
        public void testSoftDeleteOrder() throws SQLException {
            LoggerHandler.log("=== START testSoftDeleteOrder ===");

            LoggerHandler.log("=== END testSoftDeleteOrder ===");
        }
        @Test
        public void testReadDeletedOrder() throws SQLException {
            LoggerHandler.log("=== START testReadDeletedOrder ===");

            LoggerHandler.log("=== END testReadDeletedOrder ===");
        }
        @Test
        public void testInsertOrderWithInvalidCustomerFails() {
            LoggerHandler.log("=== START testInsertOrderWithInvalidCustomerFails ===");

            LoggerHandler.log("=== END testInsertOrderWithInvalidCustomerFails ===");
        }
    }
    @Nested
    class ItemTests {
        @AfterEach
        public void OutputLogs() {
            if (outputLogs) {
                LoggerHandler.outputReport();
            }
            LoggerHandler.clear();
        }

        @BeforeEach
        void setup() {
            LoggerHandler.clear();
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init("jdbc:h2:mem:test_person;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();
            LoggerHandler.log("=== END setup ===");
        }
        @Test
        public void testCreateAndReadItem() {
            LoggerHandler.log("=== START testCreateAndReadItem ===");
            itemService.createItem("CreateTAG-001", 3, false);
            LoggerHandler.log("Item Created : " + itemService.getItemByTag("CreateTAG-001", false).toString());
            assertTrue(itemService.getItemByTag("CreateTAG-001", false).itemId() != -1);
            LoggerHandler.log("Read Item by Tag: " + itemService.getItemByTag("CreateTAG-001", false).toString());
            LoggerHandler.log("=== END testCreateAndReadItem ===");
        }
        @Test
        public void testUpdateItem() {
            LoggerHandler.log("=== START testUpdateItem ===");
            itemService.createItem("TAG-UPDATE", 3, false);
            assertTrue((itemService.getItemByTag("TAG-UPDATE", false).itemId() > -1), "Dummy item TAG-UPDATE should exist");
            LoggerHandler.log("Tag to be updated" + itemService.getItemByTag("TAG-UPDATE", false).toString());
            int itemId = itemService.getItemByTag("TAG-UPDATE", false).itemId();
            itemService.changeTag("TAG-UPDATED", itemId);
            assertNull(itemService.getItemByTag("TAG-UPDATE", false),
                    "Old tag should no longer resolve");
            Item oldTagExists = itemService.getItemByTag("TAG-UPDATE", false);
            LoggerHandler.log("Tag TAG-UPDATE, no longer exists: " + (oldTagExists == null ? "not found" : oldTagExists)
                    + "\nTag named TAG-UPDATED, should: " + itemService.getItemByTag("TAG-UPDATED", false).itemId());
            itemService.markOverdue(itemId, true);
            LoggerHandler.log("Tag should be marked overdue: " + itemService.getItemById(itemId, false).toString());
            itemService.moveItem(itemService.getItemById(itemId, false).itemId(), 1);
            LoggerHandler.log("Tag should be moved to position 1: " + itemService.getItemById(itemId, false).toString());
            LoggerHandler.log("=== START testUpdateItem ===");
        }
        @Test
        public void testSoftDeleteAndReadItem() {
            LoggerHandler.log("=== START testSoftDeleteAndReadItem ===");
            Item TagExists = itemService.createItem("TAG-SOFTDELETE", 3, false);
            LoggerHandler.log("Dummy item TAG-SOFTDELETE should exist: " + (TagExists == null ? "not found" :
                    TagExists));
            assertNotNull((itemService.getItemByTag("TAG-SOFTDELETE", false)), "Dummy item TAG-SOFTDELETE should exist");
            itemService.deleteItem(TagExists != null ? TagExists.itemId() : 0);
            LoggerHandler.log("Soft-deleted ItemID " + TagExists.toString());
            assertNull((itemService.getItemById(TagExists.itemId(), false)), "Item should not be retrievable after soft delete");
            LoggerHandler.log("Verified ItemID " + (itemService.getItemByTag("TAG-SOFTDELETE", true).itemId()) + " is no " +
                    "longer " +
                    "retrievable (soft-deleted)");
            assertNotNull((itemService.getItemById(TagExists.itemId(), true)), "Data should still be present in Item " +
                    "Table");
            LoggerHandler.log("Verified ItemID " + (itemService.getItemByTag("TAG-SOFTDELETE", true).itemId()) + " is still " +
                    "in" +
                    " DB");

            LoggerHandler.log("=== END testSoftDeleteAndReadItem ===");
        }
    }
    @Nested
    class OrderItemTests {
        Person orderCustomer;
        Person orderLogger;
        Order orderOne;
        Order orderTwo;
        Item itemOne;
        Item itemTwo;
        OrderItemService orderItemservice;
        @AfterEach
        public void OutputLogs() {
            if (outputLogs) {
                LoggerHandler.outputReport();
            }
            LoggerHandler.clear();
        }
        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init("jdbc:h2:mem:test_person;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();
            orderCustomer = personService.createPerson("OrderCustomer", PersonRole.CUSTOMER);
            orderLogger = personService.createPerson("OrderLogger", PersonRole.USER);
            LoggerHandler.log("Created people: \n1 : " + orderCustomer.toString() + "\n2 : " + orderLogger.toString());
            orderOne = orderService.createOrder(LocalDate.now().plusDays(1), orderCustomer.personId(),
                    orderCustomer.personId());
            orderTwo = orderService.createOrder(LocalDate.now().plusDays(1), orderCustomer.personId(),
                    orderCustomer.personId());
            LoggerHandler.log("Created Orders \n1 : " + orderOne.toString() +"\n2 : "+ orderTwo.toString());
            itemOne = itemService.createItem("TAG-001", 3, false);
            itemTwo = itemService.createItem("TAG-001", 5, false);
            LoggerHandler.log("Created Item \n1 : " + itemOne.toString() +"\n2 : "+ itemTwo.toString());
            LoggerHandler.log("=== END setup ===");
        }
        @Test
        public void testAttachItemToOrder() {
            LoggerHandler.log("=== START testAttachItemToOrder ===");
            OrderItem attached = orderItemService.assignItemToOrder(itemOne.itemId(), orderOne.orderId());
            assertNotNull(attached);
            assertEquals(orderOne.orderId(), attached.orderId());
            assertEquals(itemOne.itemId(), attached.itemId());
            assertFalse(attached.deleted());
            LoggerHandler.log("OrderItem : " + attached.toString());
            LoggerHandler.log("=== END testAttachItemToOrder ===");
        }
        @Test
        public void testReadAllItemsInOrder() throws SQLException {
            LoggerHandler.log("=== START testReadAllItemsInOrder ===");
            OrderItem attachedOne = orderItemService.assignItemToOrder(itemOne.itemId(), orderOne.orderId());
            assertNotNull(attachedOne);
            assertEquals(orderOne.orderId(), attachedOne.orderId());
            assertEquals(itemOne.itemId(), attachedOne.itemId());
            assertFalse(attachedOne.deleted());
            OrderItem attachedTwo = orderItemService.assignItemToOrder(itemTwo.itemId(), orderOne.orderId());
            assertNotNull(attachedTwo);
            assertEquals(orderOne.orderId(), attachedTwo.orderId());
            assertEquals(itemTwo.itemId(), attachedTwo.itemId());
            assertFalse(attachedTwo.deleted());
            List<OrderItem> items = orderItemService.getItemsInOrder(orderOne.orderId(), false);
            assertEquals(2, items.size(), "Order should contain exactly 2 items");
            LoggerHandler.log("=== END testReadAllItemsInOrder ===");
        }
        @Test
        void testDeleteItemInOrder() {
            LoggerHandler.log("=== START testDeleteItemInOrder ===");

            var item = itemService.createItem("TAG-SOFTDELETE", 1, false);
            var order = orderService.createOrder(LocalDate.now(), orderCustomer.personId(), orderLogger.personId());

            var rel = orderItemService.assignItemToOrder(item.itemId(), order.orderId());
            assertNotNull(rel);
            assertFalse(rel.deleted());

            assertTrue(orderItemService.detachItemFromOrder(item.itemId(), order.orderId()));

            var active = orderItemService.getItemsInOrder(order.orderId(), false);
            assertTrue(active.stream().noneMatch(oi -> oi.itemId() == item.itemId()),
                    "OrderItem should not be retrievable after soft delete");

            // but it still exists when including deleted
            var all = orderItemService.getItemsInOrder(order.orderId(), true);
            assertTrue(all.stream().anyMatch(oi -> oi.itemId() == item.itemId() && oi.deleted()),
                    "Soft-deleted OrderItem should be present (deleted=true)");

            LoggerHandler.log("=== END testDeleteItemInOrder ===");
        }
        @Test
        void testMoveItemToAnotherOrder() {
            LoggerHandler.log("=== START MoveItemToAnotherOrder ===");

            var item = itemService.createItem("TAG-MOVE", 1, false);

            orderItemService.assignItemToOrder(item.itemId(), orderOne.orderId());
            assertTrue(orderItemService.isAttached(orderOne.orderId(), item.itemId()));
            assertTrue(orderItemService.moveItemToAnotherOrder(item.itemId(), orderOne.orderId(),
                    orderTwo.orderId()));

            assertFalse(orderItemService.isAttached(orderOne.orderId(), item.itemId()), "Should no longer be attached to 'from'");
            assertTrue(orderItemService.isAttached(orderTwo.orderId(), item.itemId()), "Should be attached to 'to'");

            var fromAll = orderItemService.getItemsInOrder(orderOne.orderId(), true);
            assertTrue(fromAll.stream().anyMatch(oi -> oi.itemId() == item.itemId() && oi.deleted()));

            LoggerHandler.log("=== END MoveItemToAnotherOrder ===");
        }

    }
    @Nested
    class BrokerTests  {
        private Vertx vertx;
        private MqttServer server;
        private int port;
        private final String host = "127.0.0.1";

        private ItemService itemService;
        private ItemReadService itemReadService;
        public static class WsProbe extends Session.Listener.Abstract implements Session.Listener.AutoDemanding {
            final CountDownLatch openLatch    = new CountDownLatch(1);
            final CountDownLatch messageLatch = new CountDownLatch(1);
            volatile String lastText;
            volatile Session session;

            @Override
            public void onWebSocketOpen(Session session) {
                super.onWebSocketOpen(session);
                this.session = session;
                openLatch.countDown();
            }
            @Override
            public void onWebSocketText(String message) {
                this.lastText = message;
                messageLatch.countDown();
            }
            public Session session() { return session; }
        }
        private final java.util.concurrent.ConcurrentMap<String, java.util.Set<MqttEndpoint>> subs =
                new java.util.concurrent.ConcurrentHashMap<>();

        private java.util.Set<MqttEndpoint> subscribersFor(String topic) {
            return subs.getOrDefault(topic, java.util.Set.of());
        }

        @AfterEach
        public void baseTeardown() {
            if (outputLogs) {
                LoggerHandler.outputReport();
            }
            LoggerHandler.clear();
            ConnectionManager.close();
        }

        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START setup ===");
            vertx = Vertx.vertx();
            server = MqttServer.create(vertx, new MqttServerOptions().setHost(host).setPort(0));

            server.endpointHandler(BrokerTests.this::onClient);
            server.listen().toCompletionStage().toCompletableFuture().join();
            port = server.actualPort();

            LoggerHandler.log("✅ Vert.x MQTT broker started on tcp://" + host + ":" + port);

            ConnectionManager.init("jdbc:h2:mem:int_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();

            itemService = new ItemService();
            itemReadService = new ItemReadService();

            LoggerHandler.log("=== END setup ===");
        }
        private void onClient(MqttEndpoint endpoint) {
            LoggerHandler.log("MQTT CONNECT clientId=" + endpoint.clientIdentifier());
            endpoint.accept(false);

            endpoint.subscribeHandler(sub -> {
                sub.topicSubscriptions().forEach(ts -> {
                    LoggerHandler.log("📡 client sub " + ts.topicName());
                    subs.computeIfAbsent(ts.topicName(),
                            k -> new java.util.concurrent.CopyOnWriteArraySet<>()).add(endpoint);
                });

                var granted = sub.topicSubscriptions().stream()
                        .map(ts -> io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE)
                        .toList();
                endpoint.subscribeAcknowledge(sub.messageId(), granted);
            });

            endpoint.unsubscribeHandler(unsub -> {
                unsub.topics().forEach(topic -> {
                    var set = subs.get(topic);
                    if (set != null) set.remove(endpoint);
                });
                endpoint.unsubscribeAcknowledge(unsub.messageId());
            });

            endpoint.publishHandler(msg -> {
                String topic = msg.topicName();
                byte[] bytes = msg.payload().getBytes();
                LoggerHandler.log("📥 Broker got publish " + topic + " | " + new String(bytes));

                // Route to all connected subscribers of that topic
                for (var ep : subscribersFor(topic)) {
                    if (ep.isConnected()) {
                        ep.publish(
                                topic,
                                io.vertx.core.buffer.Buffer.buffer(bytes),
                                msg.qosLevel(), // Vert.x MqttQoS from the inbound message
                                false,
                                false
                        );
                    }
                }
            });

            endpoint.disconnectHandler(v -> {
                LoggerHandler.log("MQTT DISCONNECT " + endpoint.clientIdentifier());
                // remove this endpoint from all topic sets
                subs.values().forEach(set -> set.remove(endpoint));
            });
        }

        @AfterEach
        void stopBroker() {
            try {
                if (server != null) server.close().toCompletionStage().toCompletableFuture().join();
            } catch (Throwable ignore) {}
            try {
                if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture().join();
            } catch (Throwable ignore) {}
            LoggerHandler.log("Vert.x MQTT broker stopped");
        }
        @Test
        void broker_allows_publish_and_subscribe_roundtrip() throws Exception {
            String topic = "test/topic";
            String payload = "hello-mqtt";

            // subscriber
            MqttClient sub = new MqttClient("tcp://" + host + ":" + port, "sub", null);
            CountDownLatch got = new CountDownLatch(1);
            final String[] seen = new String[1];

            sub.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) { }

                @Override
                public void messageArrived(String t, MqttMessage m)  { // ← add throws Exception
                    seen[0] = new String(m.getPayload(), StandardCharsets.UTF_8);
                    got.countDown();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) { }
            });
            sub.connect();
            sub.subscribe(topic, 0);
            Thread.sleep(100);

            MqttClient pub = new MqttClient("tcp://" + host + ":" + port, "pub", null);
            pub.connect();
            pub.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 0, false);

            assertTrue(got.await(3, TimeUnit.SECONDS), "Subscriber should receive message");
            assertEquals(payload, seen[0]);

            pub.disconnect();
            sub.disconnect();
            pub.close(); sub.close();
        }
        @Test
        void recordScan_parsesAndPersists() {
            String tag = "FXR90CBBF41";
            itemService.createItem(tag, 1, false);

            String ts = "2025-11-11T10:15:30.123Z";
            itemReadService.recordScan(tag, ts);

            var reads = itemReadService.getRecentReads(tag, 5);
            assertFalse(reads.isEmpty());
            assertEquals(tag, reads.get(0).tagId());
        }
    }
    @Nested
    class WebsocketTests {
        private Server server;
        private ServerConnector connector;   // <— use the field
        private int port;
        private WebSocketClient client;
        private JwtAuthenticator authenticator;

        private static final String ISS = "test-issuer";
        private static final String AUD = "ws-service";
        static final byte[] SECRET =
                "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);

        /** Build a valid HS256 token for tests. */
        String FakeJWTToken() throws JOSEException {
            var now = new java.util.Date();
            var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                    .issuer(ISS)
                    .audience(AUD)
                    .subject("alice")
                    .issueTime(now)
                    .expirationTime(new java.util.Date(now.getTime() + 3600_000)) // +1h
                    .claim("scope", "ws:connect")
                    .build();

            var jwt = new com.nimbusds.jwt.SignedJWT(
                    new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                    claims
            );
            jwt.sign(new com.nimbusds.jose.crypto.MACSigner(SECRET));
            return jwt.serialize();
        }

        private URI wsUri(String token) {
            String q = (token == null || token.isBlank()) ? "" :
                    "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            return URI.create("ws://localhost:" + port + "/ws" + q);
        }

        /** Probe that doesn’t hang on errors. */
        public static class WsProbe extends Session.Listener.Abstract implements Session.Listener.AutoDemanding {
            final CountDownLatch openLatch    = new CountDownLatch(1);
            final CountDownLatch messageLatch = new CountDownLatch(1);
            volatile String lastText;
            volatile Session session;

            @Override
            public void onWebSocketOpen(Session session) {
                super.onWebSocketOpen(session);
                this.session = session;
                openLatch.countDown();
            }

            @Override
            public void onWebSocketText(String message) {
                this.lastText = message;
                messageLatch.countDown();
            }

            @Override
            public void onWebSocketError(Throwable cause) {
                // fail fast to avoid timeouts
                openLatch.countDown();
                messageLatch.countDown();
            }

            public Session session() { return session; }
        }

        @BeforeEach
        void setUp() throws Exception {
            LoggerHandler.log("=== START setup ===");
            authenticator = JwtAuthenticator.buildHmacForTests(ISS, AUD, SECRET);

            server = new Server();
            connector = new ServerConnector(server);  // <— assign field
            connector.setPort(0);
            server.addConnector(connector);

            var context = new org.eclipse.jetty.ee10.servlet.ServletContextHandler();
            context.setContextPath("/");
            server.setHandler(context);

            org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer.configure(
                    context,
                    (sc, container) -> container.addMapping("/ws", (req, res) -> {
                        String token = java.util.Optional.ofNullable(req.getParameterMap().get("token"))
                                .flatMap(list -> list.stream().findFirst())
                                .orElse("");

                        if (token.isBlank()) { try { res.sendForbidden("Missing token"); } catch (Exception ignore) {} return null; }

                        try {
                            AuthContext auth = authenticator.verify(token);
                            return new WebSocketServer(auth); // your @WebSocket endpoint that greets on open
                        } catch (Exception e) {
                            try { res.sendForbidden("Invalid token"); } catch (Exception ignore) {}
                            return null;
                        }
                    })
            );

            server.start();
            port = connector.getLocalPort();  // <— read from field; no array cast needed

            client = new WebSocketClient();
            client.start();
            LoggerHandler.log("=== END setup ===");
        }

        @AfterEach
        void tearDown()  {
            try { if (client != null) client.stop(); } catch (Throwable ignore) {}
            try { if (server != null) server.stop(); } catch (Throwable ignore) {}
        }

        @Test
        void handshake_success_with_valid_token() throws Exception {
            String token = FakeJWTToken();
            WsProbe probe = new WsProbe();

            client.connect(probe, wsUri(token)).get(5, TimeUnit.SECONDS);

            assertTrue(probe.openLatch.await(2, TimeUnit.SECONDS), "WebSocket did not open");
            assertTrue(probe.messageLatch.await(2, TimeUnit.SECONDS), "Server did not send greeting");
            assertEquals("hello alice", probe.lastText);

            if (probe.session() != null && probe.session().isOpen()) probe.session().close();
        }

        @Test
        void handshake_rejected_when_token_missing() {
            WsProbe probe = new WsProbe();
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> client.connect(probe, wsUri(null)).get(5, TimeUnit.SECONDS));
            String msg = String.valueOf(ex.getCause());
            assertTrue(msg.contains("403") || msg.toLowerCase().contains("forbidden"),
                    "Expected 403/Forbidden in cause but was: " + msg);
        }

        @Test
        void handshake_rejected_when_token_invalid() throws Exception {
            byte[] wrongSecret = "00000000000000000000000000000000".getBytes(StandardCharsets.UTF_8);
            var now = new java.util.Date();
            var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                    .issuer(ISS).audience(AUD).subject("alice")
                    .issueTime(now).expirationTime(new java.util.Date(now.getTime() + 3600_000))
                    .build();
            var badJwt = new com.nimbusds.jwt.SignedJWT(
                    new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), claims);
            badJwt.sign(new com.nimbusds.jose.crypto.MACSigner(wrongSecret));
            String badToken = badJwt.serialize();

            WsProbe probe = new WsProbe();
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> client.connect(probe, wsUri(badToken)).get(5, TimeUnit.SECONDS));
            String msg = String.valueOf(ex.getCause());
            assertTrue(msg.contains("403") || msg.toLowerCase().contains("forbidden"),
                    "Expected 403/Forbidden in cause but was: " + msg);
        }
    }

    //@Nested
    //class UserSecurityTests {}
}


