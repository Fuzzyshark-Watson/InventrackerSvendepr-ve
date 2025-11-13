package Fuzzcode.Server.db;
import Fuzzcode.Server.utilities.LoggerHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ConnectionManager {

    private static volatile boolean initialized = false;
    private static String url;
    private static String user;
    private static String pass;
    private static int loginTimeoutSeconds = 5;

    private static volatile Connection livingConnection; // only used for H2 mem

    private ConnectionManager() {}

    public static synchronized void init(String jdbcUrl, String username, String password) {
        if (initialized) return;
        url  = jdbcUrl;
        user = username;
        pass = password;

        try { DriverManager.setLoginTimeout(loginTimeoutSeconds); } catch (Throwable ignored) {}

        if (isH2Mem(url) && !url.contains("DB_CLOSE_DELAY")) {
            url = url + (url.contains(";") ? "" : ";") + "DB_CLOSE_DELAY=-1";
        }

        try {
            if (isH2Mem(url)) {
                livingConnection = DriverManager.getConnection(url, user, pass);
                validate(livingConnection);
            } else {
                try (Connection c = DriverManager.getConnection(url, user, pass)) {
                    validate(c);
                }
            }
        } catch (SQLException e) {
            LoggerHandler.log("ConnectionManager init failed for url=" + url);
            LoggerHandler.log(e);
            throw new RuntimeException("DB init failed", e);
        }

        initialized = true;
        LoggerHandler.log("ConnectionManager initialized for " + url);
    }

    public static Connection getConnection() throws SQLException {
        ensureInitialized();
        return DriverManager.getConnection(url, user, pass);
    }

    public static synchronized void setLoginTimeoutSeconds(int seconds) {
        if (initialized) return;
        loginTimeoutSeconds = Math.max(1, seconds);
    }

    public static synchronized void close() {
        if (livingConnection != null) {
            try { livingConnection.close(); } catch (SQLException ignored) {}
            livingConnection = null;
        }
        initialized = false;
        LoggerHandler.log("ConnectionManager closed");
    }

    /* --------------- helpers --------------- */

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ConnectionManager not initialized. Call ConnectionManager.init(...) first.");
        }
    }
    public static boolean isInitialized() {
        return initialized;
    }

    public static synchronized void resetForTests() {
        try { close(); } catch (Throwable ignore) {}
        initialized = false;
        url = null;
        user = null;
        pass = null;
        livingConnection = null;
    }

    private static void validate(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            ps.executeQuery().close();
        }
    }

    private static boolean isH2Mem(String u) {
        return u != null && u.startsWith("jdbc:h2:mem:");
    }
}
