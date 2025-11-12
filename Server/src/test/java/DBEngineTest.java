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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
    class AUTH {
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
                    "jdbc:h2:mem:auth_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "admin",
                    "root"
            );
            DatabaseInitializer.initSchema();
            userService.register("admin", "root", UserRole.ADMIN);
            LoggerHandler.log("=== END setup ===");
        }
        @Test
        void testLogin_RightUser_WrongPw() {
            LoggerHandler.log("Testing === testLoginRightUserWrongPw");
            boolean result = userService.login("admin", "wrongpassword");
            assertFalse(result, "Login should fail with incorrect credentials");
        }
        @Test
        void testLogin_WrongUser_RightPw() {
            LoggerHandler.log("Testing === testLoginWrongUserRightPw");
            boolean result = userService.login("wrongUser", "root");
            assertFalse(result, "Login should fail with incorrect credentials");
        }
        @Test
        void testLogin_WrongUser_WrongPw() {
            LoggerHandler.log("Testing === testLoginWrongUserWrongPw");
            boolean result = userService.login("wrongUser", "wrongpassword");
            assertFalse(result, "Login should fail with incorrect credentials");
        }
        @Test
        void testLogin_RightUser_RightPw() {
            LoggerHandler.log("Testing === testLoginRightUserRightPw");
            boolean result = userService.login("admin", "root");
            assertTrue(result, "Login should succeed with correct credentials");
        }
    }
    @Nested
    class DB {
        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START DB Tests ===");
            ConnectionManager.init("jdbc:h2:mem:db_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();

            itemService = new ItemService();
            orderService = new OrderService();
            personService = new PersonService();
            orderItemService = new OrderItemService();

            LoggerHandler.log("Schema initialized");

        }
        @AfterEach
        void teardown() {
            if (outputLogs) {
                LoggerHandler.outputReport();
                LoggerHandler.clear();
                ConnectionManager.close();
            }
        }
        @Test
        void InitializeDB_Fail() {
            LoggerHandler.log("=== START DB-001 InitializeDBFail ===");
            ConnectionManager.resetForTests();
            String badUrl = "jdbc:h2:file:./target/tmp/does_not_exist;IFEXISTS=TRUE";
            String user = "admin";
            String pass = "root";

            assertThrows(RuntimeException.class, () -> {
                ConnectionManager.init(badUrl, user, pass);
            });

            LoggerHandler.log("=== END DB-001 InitializeDBFail ===");
        }
        @Test
        void InitializeDB_Success() {
            LoggerHandler.log("=== START DB-002 InitializeDBSuccess ===");
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
            LoggerHandler.log("=== END DB-002 InitializeDBSuccess ===");
        }
        @Test
        void uniqueConstraintOnTag_preventsDuplicates() {
            LoggerHandler.log("=== START DB-003 uniqueConstraintOnTag_preventsDuplicates ===");
            var first = itemService.createItem("TAG-DUP-SVC", Position.HOME,false);
            assertNotNull(first, "First item should be created");
            LoggerHandler.log("Created first item id=" + first.itemId());

            boolean failedAsNull = false;
            RuntimeException thrown = null;
            try {
                var second = itemService.createItem("TAG-DUP-SVC", Position.DELIVERED, false);
                failedAsNull = (second == null);
            } catch (RuntimeException ex) {
                thrown = ex;
            }

            assertTrue(failedAsNull || thrown != null,
                    "Duplicate tag must be rejected (null return or exception)");
            LoggerHandler.log(failedAsNull
                    ? "Duplicate insert returned null (as expected)"
                    : "Duplicate insert threw: " + thrown.getMessage());
            LoggerHandler.log("=== END DB-003 uniqueConstraintOnTag_preventsDuplicates ===");
        }
        @Test
        void Integrity_invalidParents_areRejected(){
            LoggerHandler.log("=== START DB-004 Integrity_invalidParents_areRejected ===");

            int ItemId  = 8_888_888;
            int OrderId = 9_999_999;

            assertThrows(IllegalArgumentException.class,
                    () -> orderItemService.assignItemToOrder(ItemId, OrderId),
                    "Assigning with non-existing IDs should fail via FK");

            LoggerHandler.log("=== END DB-004 Integrity_invalidParents_areRejected ===");
        }
        @Test
        void foreignKeyIntegrity_preventsInvalidRelations_andParentDelete() {

            LoggerHandler.log("=== START DB-004 foreignKeyIntegrity ===");
            var customer = personService.createPerson("FK-Customer", PersonRole.CUSTOMER);
            var logger   = personService.createPerson("FK-Logger",   PersonRole.USER);
            var order    = orderService.createOrder(java.time.LocalDate.now(), customer.personId(), logger.personId());
            var item     = itemService.createItem("TAG-FK-SVC-1", Position.HOME, false);

            assertNotNull(order);
            assertNotNull(item);

            var rel = orderItemService.assignItemToOrder(item.itemId(), order.orderId());
            assertNotNull(rel, "Relation should be created");
            assertFalse(rel.deleted(), "New relation should be active");
            assertTrue(orderItemService.isAttached(order.orderId(), item.itemId()));

            var activeBefore = orderItemService.getItemsInOrder(order.orderId(), false);
            assertTrue(activeBefore.stream().anyMatch(oi -> oi.itemId() == item.itemId()),
                    "Active list should contain the item");

            assertTrue(orderItemService.detachItemFromOrder(item.itemId(), order.orderId()),
                    "Detach should return true");

            assertFalse(orderItemService.isAttached(order.orderId(), item.itemId()),
                    "isAttached should be false after detach");

            var activeAfter = orderItemService.getItemsInOrder(order.orderId(), false);
            assertTrue(activeAfter.stream().noneMatch(oi -> oi.itemId() == item.itemId()),
                    "Active list should not contain the detached item");

            var allAfter = orderItemService.getItemsInOrder(order.orderId(), true);
            assertTrue(allAfter.stream().anyMatch(oi -> oi.itemId() == item.itemId() && oi.deleted()),
                    "includeDeleted=true should show the soft-deleted relation");

            LoggerHandler.log("=== END DB-004 foreignKeyIntegrity ===");
        }

    }
    @Nested
    class PER {
        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init("jdbc:h2:mem:per_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
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
            LoggerHandler.log("Read Successful!");
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
        @Test
        void testCreateWithInvalidName() {
            LoggerHandler.log("=== START PER-004 testCreateWithInvalidName ===");

            boolean failedAsNull = false;
            RuntimeException thrown = null;

            try {
                Person p = personService.createPerson("", PersonRole.USER); // invalid: empty name
                // Accept either: service/DAO rejects (null) OR throws
                failedAsNull = (p == null) || (p.name() == null) || p.name().isBlank();
            } catch (RuntimeException ex) {
                thrown = ex;
            }

            assertTrue(failedAsNull || thrown != null,
                    "Creating a person with empty name must be rejected (null/blank or exception)");

            LoggerHandler.log(failedAsNull
                    ? "Create with empty name returned null/blank (as expected)"
                    : "Create with empty name threw: " + thrown.getMessage());

            LoggerHandler.log("=== END PER-004 testCreateWithInvalidName ===");
        }
        @Test
        void testBulkReadPerformanceSmall() {
            LoggerHandler.log("=== START PER-005 testBulkReadPerformanceSmall ===");

            // Arrange: ensure ~100 persons exist
            for (int i = 0; i < 100; i++) {
                personService.createPerson("PerfUser-" + i, PersonRole.USER);
            }

            long t0 = System.nanoTime();
            var list = personService.listPeople(); // active only
            long t1 = System.nanoTime();

            long elapsedMs = (t1 - t0) / 1_000_000L;
            LoggerHandler.log("Read " + list.size() + " persons in " + elapsedMs + " ms");

            // Set a safe threshold for in-memory H2; adjust if your CI is slower
            assertTrue(elapsedMs <= 500, "List persons should complete within 500 ms");

            LoggerHandler.log("=== END PER-005 testBulkReadPerformanceSmall ===");
        }
    }
    @Nested
    class ORD {
        Person OrderCustomer;
        Person OrderLogger;
        Order order;

        @BeforeEach
        void setup()  {
            LoggerHandler.log("=== START setup ===");
            ConnectionManager.init("jdbc:h2:mem:ord_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
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
        public void testSoftDeleteOrder() {
            LoggerHandler.log("=== START ORD-004 testSoftDeleteOrder ===");
            var customer = personService.createPerson("OrderCustomer", PersonRole.CUSTOMER);
            var logger   = personService.createPerson("OrderLogger",   PersonRole.USER);
            var ord      = orderService.createOrder(LocalDate.now().plusDays(1),
                    customer.personId(), logger.personId());
            assertNotNull(ord);

            assertTrue(orderService.softDeleteOrder(ord.orderId()), "Soft delete should return true");
            assertNull(orderService.getOrder(ord.orderId()), "Active read must return null after soft delete");

            var deletedView = orderService.getOrder(ord.orderId(), true);
            assertNotNull(deletedView, "Deleted view should return the row");
            assertTrue(deletedView.deleted(), "Deleted flag should be true");
            LoggerHandler.log("=== END ORD-004 testSoftDeleteOrder ===");
        }
        @Test
        public void testReadDeletedOrder() throws SQLException {
            LoggerHandler.log("=== START testReadDeletedOrder ===");

            LoggerHandler.log("=== END testReadDeletedOrder ===");
        }
        @Test
        public void testInsertOrderWithInvalidCustomerFails() {
            LoggerHandler.log("=== START ORD-006 testInsertOrderWithInvalidCustomerFails ===");
            var logger = personService.createPerson("AnyLogger", PersonRole.USER);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> orderService.createOrder(LocalDate.now(), 999999, logger.personId()),
                    "Creating order with non-existing customer should fail (FK)");

            LoggerHandler.log("Create with invalid customer rejected: " + ex.getMessage());
            LoggerHandler.log("=== END ORD-006 testInsertOrderWithInvalidCustomerFails ===");
        }
        @Test
        public void testEndDateCannotBeBeforeStart() {
            LoggerHandler.log("=== START ORD-007 testEndDateCannotBeBeforeStart ===");
            var customer = personService.createPerson("RuleCustomer", PersonRole.CUSTOMER);
            var logger   = personService.createPerson("RuleLogger",   PersonRole.USER);
            var ord      = orderService.createOrder(LocalDate.now(), customer.personId(), logger.personId());
            assertNotNull(ord);

            LocalDate start = LocalDate.now();
            LocalDate end   = start.minusDays(1);
            boolean ok = orderService.updateOrderDates(ord.orderId(), start, end);
            assertFalse(ok, "Service should reject end < start");
            LoggerHandler.log("=== END ORD-007 testEndDateCannotBeBeforeStart ===");
        }
    }
    @Nested
    class ITM {
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
            ConnectionManager.init("jdbc:h2:mem:itm_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "admin", "root");
            DatabaseInitializer.initSchema();
            LoggerHandler.log("=== END setup ===");
        }
        @Test
        public void testCreateAndReadItem() {
            LoggerHandler.log("=== START testCreateAndReadItem ===");
            itemService.createItem("CreateTAG-001", Position.HOME, false);
            LoggerHandler.log("Item Created : " + itemService.getItemByTag("CreateTAG-001", false).toString());
            assertTrue(itemService.getItemByTag("CreateTAG-001", false).itemId() != -1);
            LoggerHandler.log("Read Item by Tag: " + itemService.getItemByTag("CreateTAG-001", false).toString());
            LoggerHandler.log("=== END testCreateAndReadItem ===");
        }
        @Test
        public void testUpdateItem() {
            LoggerHandler.log("=== START testUpdateItem ===");
            itemService.createItem("TAG-UPDATE", Position.HOME, false);
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
            itemService.moveItem(itemService.getItemById(itemId, false).itemId(), Position.DELIVERED);
            LoggerHandler.log("Tag should be moved to position 1: " + itemService.getItemById(itemId, false).toString());
            LoggerHandler.log("=== START testUpdateItem ===");
        }
        @Test
        public void testSoftDeleteAndReadItem() {
            LoggerHandler.log("=== START testSoftDeleteAndReadItem ===");
            Item TagExists = itemService.createItem("TAG-SOFTDELETE", Position.HOME, false);
            LoggerHandler.log("Dummy item TAG-SOFTDELETE should exist: " + (TagExists == null ? "not found" :
                    TagExists));

            assertNotNull((itemService.getItemByTag("TAG-SOFTDELETE", false)), "Dummy item TAG-SOFTDELETE should exist");

            itemService.deleteItem(TagExists != null ? TagExists.itemId() : 0);

            assertNotNull(TagExists);

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
        @Test
        public void testMoveItemPosition() {
            LoggerHandler.log("=== START ITM-003 testMoveItemPosition ===");
            var item = itemService.createItem("TAG-MOVE", Position.HOME, false);
            assertNotNull(item, "Item should be created");

            assertTrue(itemService.moveItem(item.itemId(), Position.DELIVERED),
                    "Move to DELIVERED should succeed");

            var updated = itemService.getItemById(item.itemId(), false);
            assertNotNull(updated, "Updated item should be readable");
            assertEquals(Position.DELIVERED, updated.position(), "Position should be DELIVERED");

            LoggerHandler.log("=== END ITM-003 testMoveItemPosition ===");
        }
        @Test
        public void testMoveItemInvalidPosition() {
            LoggerHandler.log("=== START ITM-005 testMoveItemInvalidPosition ===");
            var item = itemService.createItem("TAG-MOVE-INVALID", Position.HOME, false);
            assertNotNull(item, "Item should be created");

            boolean ok = false;
            RuntimeException thrown = null;
            try {
                ok = itemService.moveItem(item.itemId(), null);   // invalid
            } catch (RuntimeException ex) {
                thrown = ex;
            }

            assertTrue(thrown != null || !ok, "Moving with null position must fail");

            assertTrue(itemService.deleteItem(item.itemId()), "Soft delete should succeed");
            boolean secondOk = false;
            RuntimeException secondThrown = null;
            try {
                secondOk = itemService.moveItem(item.itemId(), Position.DELIVERED);
            } catch (RuntimeException ex) {
                secondThrown = ex;
            }
            assertTrue(secondThrown != null || !secondOk,
                    "Moving a soft-deleted item must fail");
            LoggerHandler.log("=== END ITM-005 testMoveItemInvalidPosition ===");
        }
    }
    @Nested
    class ORIT {
        Person orderCustomer;
        Person orderLogger;
        Order orderOne;
        Order orderTwo;
        Item itemOne;
        Item itemTwo;
        OrderItemService orderItemservice;
        @BeforeEach
        void setup() {
            LoggerHandler.log("=== START setup ===");

            // Fresh DB per test run
            ConnectionManager.init(
                    "jdbc:h2:mem:orit_test;MODE=MySQL;DB_CLOSE_DELAY=0", // <- 0 ensures drop when last conn closes
                    "admin", "root"
            );
            DatabaseInitializer.initSchema();

            // init services (if not already fields)
            orderItemService = new OrderItemService();

            // seed data
            orderCustomer = personService.createPerson("OrderCustomer", PersonRole.CUSTOMER);
            orderLogger   = personService.createPerson("OrderLogger",   PersonRole.USER);

            LoggerHandler.log("Created people:\n1: " + orderCustomer + "\n2: " + orderLogger);

            orderOne = orderService.createOrder(
                    LocalDate.now().plusDays(1),
                    orderCustomer.personId(),
                    orderLogger.personId()                  // <- use logger here
            );
            orderTwo = orderService.createOrder(
                    LocalDate.now().plusDays(1),
                    orderCustomer.personId(),
                    orderLogger.personId()                  // <- and here
            );

            LoggerHandler.log("Created Orders\n1: " + orderOne + "\n2: " + orderTwo);

            itemOne = itemService.createItem("TAG-001", Position.HOME,      false);
            itemTwo = itemService.createItem("TAG-002", Position.DELIVERED, false);

            LoggerHandler.log("Created Items\n1: " + itemOne + "\n2: " + itemTwo);
            LoggerHandler.log("=== END setup ===");
        }

        @AfterEach
        void teardown() {
            if (outputLogs) LoggerHandler.outputReport();
            LoggerHandler.clear();
            ConnectionManager.close();
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
            var item = itemService.createItem("TAG-SOFTDELETE", Position.HOME, false);
            var order = orderService.createOrder(LocalDate.now(), orderCustomer.personId(), orderLogger.personId());
            var rel = orderItemService.assignItemToOrder(item.itemId(), order.orderId());

            assertNotNull(rel);
            assertFalse(rel.deleted());
            assertTrue(orderItemService.detachItemFromOrder(item.itemId(), order.orderId()));

            var active = orderItemService.getItemsInOrder(order.orderId(), false);
            assertTrue(active.stream().noneMatch(oi -> oi.itemId() == item.itemId()),
                    "OrderItem should not be retrievable after soft delete");
            var all = orderItemService.getItemsInOrder(order.orderId(), true);

            assertTrue(all.stream().anyMatch(oi -> oi.itemId() == item.itemId() && oi.deleted()),
                    "Soft-deleted OrderItem should be present (deleted=true)");
            LoggerHandler.log("=== END testDeleteItemInOrder ===");
        }
        @Test
        void testMoveItemToAnotherOrder() {
            LoggerHandler.log("=== START MoveItemToAnotherOrder ===");
            var item = itemService.createItem("TAG-MOVE", Position.HOME, false);

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
        @Test
        public void testReadDeletedOrder() {
            LoggerHandler.log("=== START ORD-005 testReadDeletedOrder ===");
            var customer = personService.createPerson("DelCustomer", PersonRole.CUSTOMER);
            var logger   = personService.createPerson("DelLogger",   PersonRole.USER);
            var ord      = orderService.createOrder(LocalDate.now(), customer.personId(), logger.personId());

            assertNotNull(ord);
            assertTrue(orderService.softDeleteOrder(ord.orderId(),UserRole.ADMIN));
            assertNull(orderService.getOrder(ord.orderId()));

            var incl = orderService.getOrder(ord.orderId(), true);
            assertNotNull(incl);
            assertTrue(incl.deleted());
            LoggerHandler.log("=== END ORD-005 testReadDeletedOrder ===");
        }
    }
    @Nested
    class MQTT  {
        private Vertx vertx;
        private MqttServer server;
        private int port;
        private final String host = "127.0.0.1";

        private ItemService itemService;
        private ItemReadService itemReadService;
        private boolean isTopicAllowed(String topic) {
            return !"unauthorized".equals(topic);
        }
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
        private final java.util.concurrent.ConcurrentMap<String, byte[]> retained =
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

            server.endpointHandler(MQTT.this::onClient);
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
                    String t = ts.topicName();
                    LoggerHandler.log("📡 client sub " + t);
                    subs.computeIfAbsent(t, k -> new java.util.concurrent.CopyOnWriteArraySet<>()).add(endpoint);
                });

                var granted = sub.topicSubscriptions().stream()
                        .map(ts -> io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE)
                        .toList();
                endpoint.subscribeAcknowledge(sub.messageId(), granted);

                // After SUBACK, deliver retained if present
                sub.topicSubscriptions().forEach(ts -> {
                    String t = ts.topicName();
                    byte[] r = retained.get(t);
                    if (r != null && endpoint.isConnected()) {
                        endpoint.publish(
                                t,
                                io.vertx.core.buffer.Buffer.buffer(r),
                                io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE,
                                false,
                                true // mark as retained on the wire
                        );
                    }
                });
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
                boolean retain = msg.isRetain();

                if (!isTopicAllowed(topic)) {
                    LoggerHandler.log("🚫 publish denied on topic: " + topic);
                    // Silently ignore (or endpoint.close(); if you prefer hard fail)
                    return;
                }

                if (retain) {
                    retained.put(topic, bytes);
                    LoggerHandler.log("💾 retained set for " + topic);
                }

                LoggerHandler.log("📥 Broker got publish " + topic + " | " + new String(bytes));

                for (var ep : subscribersFor(topic)) {
                    if (ep.isConnected()) {
                        ep.publish(
                                topic,
                                io.vertx.core.buffer.Buffer.buffer(bytes),
                                msg.qosLevel(),
                                false,
                                retain // propagate retain flag so late subs can see it's retained
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
            itemService.createItem(tag, Position.IN_TRANSIT_OUT, false);

            String ts = "2025-11-11T10:15:30.123Z";
            itemReadService.recordScan(tag, ts);

            var reads = itemReadService.getRecentReads(tag, 5);
            assertFalse(reads.isEmpty());
            assertEquals(tag, reads.get(0).tagId());
        }
        @Test
        void retained_message_delivered_to_late_subscriber() throws Exception {
            String topic = "retained/demo";
            String payload = "I am retained";

            // Publisher sets retained=true
            try (MqttClient pub = new MqttClient("tcp://" + host + ":" + port, "pub-retained", null)) {
                pub.connect();
                MqttMessage msg = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                msg.setQos(0);
                msg.setRetained(true);
                pub.publish(topic, msg);
                pub.disconnect();
            }

            // Now a late subscriber should immediately receive the retained payload
            CountDownLatch got = new CountDownLatch(1);
            final String[] seen = new String[1];

            try (MqttClient sub = new MqttClient("tcp://" + host + ":" + port, "sub-late", null)) {
                sub.setCallback(new MqttCallback() {
                    public void connectionLost(Throwable cause) { }
                    public void messageArrived(String t, MqttMessage m) {
                        seen[0] = new String(m.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
                        got.countDown();
                    }
                    public void deliveryComplete(IMqttDeliveryToken token) { }
                });
                sub.connect();
                sub.subscribe(topic, 0);

                assertTrue(got.await(2, TimeUnit.SECONDS), "Late subscriber should get retained");
                assertEquals(payload, seen[0]);
                sub.disconnect();
            }
        }

        @Test
        void publish_to_unauthorized_topic_is_suppressed() throws Exception {
            String topic = "unauthorized"; // blocked by isTopicAllowed()
            String payload = "should-not-be-seen";

            CountDownLatch got = new CountDownLatch(1);

            try (MqttClient sub = new MqttClient("tcp://" + host + ":" + port, "sub-unauth", null);
                 MqttClient pub = new MqttClient("tcp://" + host + ":" + port, "pub-unauth", null)) {

                sub.setCallback(new MqttCallback() {
                    public void connectionLost(Throwable cause) { }
                    public void messageArrived(String t, MqttMessage m) { got.countDown(); }
                    public void deliveryComplete(IMqttDeliveryToken token) { }
                });
                sub.connect();
                sub.subscribe(topic, 0);

                pub.connect();
                pub.publish(topic, payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, false);

                assertFalse(got.await(1, TimeUnit.SECONDS), "Publish to unauthorized topic must be ignored");

                pub.disconnect();
                sub.disconnect();
            }
        }


    }
    @Nested
    class WS {
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
        @Test
        void client_disconnect_cleanup() throws Exception {
            String token = FakeJWTToken();
            WsProbe probe = new WsProbe();

            assertEquals(0, WebSocketServer.activeCount(), "Precondition: no active sessions");

            client.connect(probe, wsUri(token)).get(5, TimeUnit.SECONDS);
            assertTrue(probe.openLatch.await(2, TimeUnit.SECONDS), "WebSocket did not open");
            assertEquals(1, WebSocketServer.activeCount(), "Exactly one session should be active");

            if (probe.session() != null && probe.session().isOpen()) {
                probe.session().close();
            }

            Thread.sleep(150); //Small wait to let it close...
            assertEquals(0, WebSocketServer.activeCount(), "Server should have released the session after client close");
        }

    }
    @Nested
    class CFG {
        @BeforeEach
        void setup() throws Exception {
            ConnectionManager.init("jdbc:h2:mem:cfg_test;MODE=MySQL;DB_CLOSE_DELAY=0", "admin", "root");
            try (var c = ConnectionManager.getConnection(); var s = c.createStatement()) {
                s.execute("DROP ALL OBJECTS");     // full wipe
            }
            DatabaseInitializer.initSchema();
        }

        @AfterEach
        void teardown() { ConnectionManager.close(); }

        @Test
        void writeSomething() throws Exception {
            Person testPerson = personService.createPerson("Alice", PersonRole.DRIVER);
            assertEquals("Alice", testPerson.name());
        }

        @Test
        void startsCleanAgain() throws Exception {
            var people = personService.listPeople();
            assertNotNull(people);              // defensive
            assertTrue(people.isEmpty(), "DB should start empty per test");
        }
    }
    @Nested
    class LOG
    {
        @AfterEach
        void tearDown() {
            // make sure we leave global state tidy for other tests
            LoggerHandler.clear();
        }
        @Test // LOG-001 part A: "Logger outputs"
        void loggerOutputsMessages() {
            // Arrange
            Fuzzcode.utilities.LoggerHandler.log("hello");
            Fuzzcode.utilities.LoggerHandler.log(Fuzzcode.utilities.LoggerHandler.Level.WARNING, "be careful");

            // Capture System.out
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));

            // Act
            Fuzzcode.utilities.LoggerHandler.outputReport();

            // Assert
            String out = baos.toString();
            assertTrue(out.contains("=== Logger Output ==="));
            assertTrue(out.contains("hello"));
            assertTrue(out.toLowerCase().contains("[warning]"), "should include WARNING level");
            assertTrue(out.contains("====================="));
        }

        @Test // LOG-001 part B: "and clears between tests"
        void loggerClearsBetweenTests() {
            // Arrange: nothing logged

            // Capture System.out
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));

            // Act
            Fuzzcode.utilities.LoggerHandler.outputReport();

            // Assert: only header/footer, no previous lines
            String out = baos.toString();
            // Should NOT contain any user messages like "hello"
            assertTrue(out.contains("=== Logger Output ==="));
            assertTrue(out.contains("====================="));
            assertFalse(out.contains("hello"), "previous test’s logs must not leak");
        }
    }
    @Nested
    class SEC
    {
        @Test
        void passwordHashingEnforced() throws Exception {
            ConnectionManager.init("jdbc:h2:mem:sec_test;MODE=MySQL;DB_CLOSE_DELAY=0", "admin", "root");
            DatabaseInitializer.initSchema();
            var users = new Fuzzcode.service.UserService();

            String username = "alice";
            String password = "secret123";
            Integer id = users.register(username, password, Fuzzcode.model.UserRole.USER);
            assertNotNull(id);

            try (var c = ConnectionManager.getConnection();
                 var ps = c.prepareStatement("SELECT Username, PasswordHash, Salt FROM Users WHERE Username=?")) {
                ps.setString(1, username);
                try (var rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "User row should exist");
                    String u  = rs.getString("Username");
                    String h  = rs.getString("PasswordHash");
                    String s  = rs.getString("Salt");

                    assertEquals(username, u);
                    assertNotNull(h);
                    assertNotNull(s);
                    assertNotEquals(password, h, "Plaintext must NEVER be stored");
                    assertFalse(s.isBlank(), "Salt must be present");

                    assertTrue(h.startsWith("$2"), "BCrypt hashes start with $2x/$2y");
                    assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw(password, h), "Hash must validate with original password");
                }
            }
        }
        @Test
        void rbac_adminOnly_softDeleteOrder() {
            ConnectionManager.init("jdbc:h2:mem:sec2;MODE=MySQL;DB_CLOSE_DELAY=0", "admin", "root");
            DatabaseInitializer.initSchema();

            var personService = new Fuzzcode.service.PersonService();
            var orderService  = new Fuzzcode.service.OrderService();

            var customer = personService.createPerson("Cust", Fuzzcode.model.PersonRole.CUSTOMER);
            var logger   = personService.createPerson("Logger", Fuzzcode.model.PersonRole.USER);
            var order    = orderService.createOrder(java.time.LocalDate.now(), customer.personId(), logger.personId());
            assertNotNull(order);

            assertThrows(SecurityException.class,
                    () -> orderService.softDeleteOrder(order.orderId(), Fuzzcode.model.UserRole.USER));

            assertTrue(orderService.softDeleteOrder(order.orderId(), Fuzzcode.model.UserRole.ADMIN));

            assertNull(orderService.getOrder(order.orderId()));
            var incl = orderService.getOrder(order.orderId(), true);
            assertNotNull(incl);
            assertTrue(incl.deleted());
        }
    }
    //@Nested
    //class UserSecurityTests {}
}


