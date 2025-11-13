package Fuzzcode.Server.db;

import Fuzzcode.Server.utilities.LoggerHandler;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseInitializer {
    public static void initSchema() {
        try (Connection c = ConnectionManager.getConnection();
             Statement stmt = c.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS People (
                  PersonID INT AUTO_INCREMENT PRIMARY KEY,
                  Name VARCHAR(255) NOT NULL,
                  Role VARCHAR(32) NOT NULL CHECK (Role IN ('ADMIN','USER','DRIVER','CUSTOMER')),
                  Deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Users (
                  UserID INT AUTO_INCREMENT PRIMARY KEY,
                  Username VARCHAR(255) UNIQUE NOT NULL,
                  PasswordHash VARCHAR(255) NOT NULL,
                  Salt VARCHAR(255) NOT NULL,
                  Role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (Role IN ('ADMIN','USER')),
                  CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  Deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Orders (
                  OrderID INT AUTO_INCREMENT PRIMARY KEY,
                  CreatedDate DATE NOT NULL,
                  StartDate DATE,
                  EndDate DATE,
                  CustomerID INT,
                  LoggedByID INT,
                  Deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  FOREIGN KEY (CustomerID) REFERENCES People(PersonID),
                  FOREIGN KEY (LoggedByID) REFERENCES People(PersonID)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Items (
                  ItemID INT AUTO_INCREMENT PRIMARY KEY,
                  TagID VARCHAR(255) NOT NULL,
                  Position VARCHAR(32) NOT NULL CHECK (
                    Position IN ('HOME', 'IN_TRANSIT_OUT', 'DELIVERED', 'IN_TRANSIT_RETURN')
                  ),
                  IsOverdue BOOLEAN,
                  Deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
            """);
            stmt.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS IDX_Items_Tag ON Items(TagID)
            """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS IDX_Items_Position ON Items(Position)
            """);


            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS OrderItems (
                      OrderID INT NOT NULL,
                      ItemID  INT NOT NULL,
                      Deleted BOOLEAN NOT NULL DEFAULT FALSE,
                      PRIMARY KEY (OrderID, ItemID),
                      FOREIGN KEY (OrderID) REFERENCES Orders(OrderID),
                      FOREIGN KEY (ItemID)  REFERENCES Items(ItemID)
                    )
                """);

                // Drop any legacy single-column unique indexes on ItemID
                stmt.execute("DROP INDEX IF EXISTS UQ_ORDERITEMS_ITEM");
                stmt.execute("DROP INDEX IF EXISTS UQ_OrderItems_Item");

                // Helpful FKs indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS IDX_OrderItems_Order ON OrderItems(OrderID)");
                stmt.execute("CREATE INDEX IF NOT EXISTS IDX_OrderItems_Item  ON OrderItems(ItemID)");

                // Enforce: at most ONE ACTIVE relation per item (Deleted = FALSE)
                stmt.execute("""
                  CREATE UNIQUE INDEX IF NOT EXISTS UQ_OrderItems_Item_Active
                  ON OrderItems(ItemID, Deleted)
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ItemRead (
                  ReadID INT AUTO_INCREMENT PRIMARY KEY,
                  TagID VARCHAR(255) NOT NULL,
                  Deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  ReadTime TIMESTAMP NOT NULL
                )
            """);


            LoggerHandler.log("Database schema initialized.");
        } catch (SQLException e) {
            LoggerHandler.log("Schema init failed");
            LoggerHandler.log(e);
            throw new RuntimeException(e);
        }
    }
    private DatabaseInitializer() {}
}
