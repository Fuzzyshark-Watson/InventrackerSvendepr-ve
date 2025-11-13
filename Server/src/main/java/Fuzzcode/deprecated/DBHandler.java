package Fuzzcode.deprecated;
import java.sql.*;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import Fuzzcode.Server.utilities.LoggerHandler;
import org.mindrot.jbcrypt.BCrypt;


public class DBHandler {
    private final String jdbcUrl;
    private String dbUser;
    private String dbPassword;
    private static DBHandler instance; //Singleton for Use elsewhere.
    private Connection connection;

    private DBHandler() {
        this.jdbcUrl = System.getProperty("db.url",
                "jdbc:h2:mem:fuzzdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        this.dbUser = System.getProperty("db.user", "sa");
        this.dbPassword = System.getProperty("db.pass", "");

        openConnection();
        initializeDatabase();
    }
    public static DBHandler getInstance() {
        if (instance == null) {
            instance = new DBHandler();
        }
        return instance;
    }

    private void openConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                LoggerHandler.log("DB connection opened: " + jdbcUrl);
            }
        } catch (SQLException e) {
            LoggerHandler.log("Failed to open DB connection");
            LoggerHandler.log(e);
            throw new RuntimeException(e);
        }
    }
    private void ensureConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                openConnection();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public void _resetForTests() {
        instance = null;
    }
    //=== MetaData ===
    public Boolean initializeDatabase() {
        ensureConnection();
        try (Statement stmt = this.connection.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS People (" +
                    "PersonID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "Name VARCHAR(255) NOT NULL, " +
                    "Role ENUM('User', 'Driver', 'Customer') NOT NULL, " +
                    "Deleted BOOLEAN DEFAULT 0)");
            LoggerHandler.log("Method initializeDatabase created People!");

            stmt.execute("CREATE TABLE IF NOT EXISTS Users (" +
                    "UserID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "Username VARCHAR(255) UNIQUE NOT NULL, " +
                    "PasswordHash VARCHAR(255) NOT NULL, " +
                    "Salt VARCHAR(255) NOT NULL, " +
                    "Role ENUM('Admin', 'User') DEFAULT 'User', " +
                    "CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            LoggerHandler.log("Method initializeDatabase created Database Users!");

            stmt.execute("CREATE TABLE IF NOT EXISTS Orders (" +
                    "OrderID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "CreatedDate DATE NOT NULL, " +
                    "StartDate DATE, " +
                    "EndDate DATE, " +
                    "CustomerID INT, " +
                    "LoggedByID INT, " +
                    "Deleted BOOLEAN DEFAULT FALSE, " +
                    "FOREIGN KEY (CustomerID) REFERENCES People(PersonID), " +
                    "FOREIGN KEY (LoggedByID) REFERENCES People(PersonID))");
            LoggerHandler.log("Method initializeDatabase created Orders!");

            stmt.execute("CREATE TABLE IF NOT EXISTS Items (" +
                    "ItemID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "TagID VARCHAR(255) NOT NULL, " +
                    "Position INT CHECK (Position BETWEEN 1 AND 6), " +
                    "IsOverdue BOOLEAN, " +
                    "Deleted BOOLEAN DEFAULT FALSE)");
            LoggerHandler.log("Method initializeDatabase created Items!");

            stmt.execute("CREATE TABLE IF NOT EXISTS OrderItems (" +
                    "OrderID INT NOT NULL, " +
                    "ItemID INT NOT NULL, " +
                    "Deleted BOOLEAN DEFAULT FALSE, " +
                    "FOREIGN KEY (OrderID) REFERENCES Orders(OrderID), " +
                    "FOREIGN KEY (ItemID) REFERENCES Items(ItemID), " +
                    "PRIMARY KEY (OrderID, ItemID))");
            LoggerHandler.log("Method initializeDatabase created OrderItems!");

            stmt.execute(                    "CREATE TABLE IF NOT EXISTS ItemRead (" +
                            "ReadID INT AUTO_INCREMENT PRIMARY KEY, " +  // Add a primary key for uniqueness
                            "TagID VARCHAR(255) NOT NULL, " +
                            "Deleted BOOLEAN DEFAULT FALSE, " +
                            "ReadTime TIMESTAMP NOT NULL" +
                            ")");

            return true;
        } catch (SQLException e) {
            LoggerHandler.log("initializeDatabase failed");
            LoggerHandler.log(e);
            return false;
        }
    }

    // === Troubleshoot ===
    public void LogDBMetaData() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        LoggerHandler.log("Database Product Name: " + meta.getDatabaseProductName());
        LoggerHandler.log("Database Product Version: " + meta.getDatabaseProductVersion());
        LoggerHandler.log("Driver Name: " + meta.getDriverName());
        LoggerHandler.log("URL: " + meta.getURL());
        LoggerHandler.log("User Name: " + meta.getUserName());

        try (ResultSet tables = meta.getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                LoggerHandler.log("Found table: " + tableName);
            }
        }
        LoggerHandler.outputReport();
    }
    public Boolean Login(boolean isTest, String _User, String _Password) {
        this.dbUser = _User;
        this.dbPassword = _Password;

        try {
            String DB_URL = isTest
                    ? "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
                    : "jdbc:h2:./data/inventrackerdb";

            connection = DriverManager.getConnection(DB_URL, dbUser, dbPassword);
            LoggerHandler.log("Method Login: Database connection established. Using " + (isTest ? "test" : "production") + " database.");
            return true;
        } catch (SQLException e) {
            LoggerHandler.log("Method Login: Login Failed!");
            LoggerHandler.log(e);
            return false;
        }
    }
    public void Logout() {
        try {
            connection.close();
            LoggerHandler.log("Method Logout: Logout Success!");
        } catch (SQLException e) {
            LoggerHandler.log("Method Logout: Logout Failed!");
            LoggerHandler.log(e);
        }
    }
    //TODO I no longer remember why I have both logout and closeConnection...
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                LoggerHandler.log("Database connection closed.");

                connection.close();
                LoggerHandler.log("Database connection closed.");
            }
        } catch (SQLException e) {
            LoggerHandler.log("Method closeConnection failed!");
            LoggerHandler.log(e);
        }
    }

    // Helper functions
    public ResultSet ExecuteQuery(String query) throws SQLException {
        return connection.createStatement().executeQuery(query);
    }
    public int ExecuteUpdate(String query) throws SQLException {
        return connection.createStatement().executeUpdate(query);
    }

    //=== Security measures for Users ===
    public boolean registerUser(String username, String password, String role) {
        String salt = generateSalt();
        String hashedPassword = hashPassword(password);

        String sql = "INSERT INTO Users (Username, PasswordHash, Salt, Role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, salt);
            stmt.setString(4, role);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            // Check if it's a duplicate username error
            if (e.getSQLState().equals("23505")) { // H2 unique constraint violation
                LoggerHandler.log("Duplicate username detected: " + username);
                return false;
            }
            throw new RuntimeException("Error registering user", e);
        }
    }
    public boolean loginUser(String username, String password) throws SQLException {
        String sql = "SELECT PasswordHash FROM Users WHERE Username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("PasswordHash");
                return BCrypt.checkpw(password, storedHash);
            }
        }
        return false;
    }
    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
    public String generateSalt() {
        return UUID.randomUUID().toString();
    }
    //=== Security measures for Users ===
}
