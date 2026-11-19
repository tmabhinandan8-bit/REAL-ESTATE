package com.realestate.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton Database Connection Manager demonstrating 'static' and 'final' keywords.
 * Manages JDBC connections to MySQL database with thread safety and configurable credentials.
 */
public final class DBConnection {

    // Final constants for standard connection parameters
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_DB = "real_estate_management";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "";

    // Driver class name
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    // Singleton volatile instance for Double-Checked Locking
    private static volatile DBConnection instance;

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPass;

    // Private constructor loading driver and initializing credentials
    private DBConnection() {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] Warning: MySQL JDBC Driver class not found: " + e.getMessage());
        }

        Map<String, String> dotEnv = loadDotEnv();
        String host = getConfig("DB_HOST", dotEnv, DEFAULT_HOST);
        String port = getConfig("DB_PORT", dotEnv, DEFAULT_PORT);
        String db = getConfig("DB_NAME", dotEnv, DEFAULT_DB);
        
        this.dbUser = getConfig("DB_USER", dotEnv, DEFAULT_USER);
        this.dbPass = getConfig("DB_PASS", dotEnv, DEFAULT_PASS);
        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db + 
                       "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    private static String getConfig(String key, Map<String, String> dotEnv, String defaultValue) {
        String envVal = System.getenv(key);
        if (envVal != null) return envVal;
        if (dotEnv.containsKey(key)) return dotEnv.get(key);
        return defaultValue;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        File envFile = new File(".env");
        if (!envFile.exists() || !envFile.isFile()) return map;
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    map.put(k, v);
                }
            }
        } catch (IOException ignored) {}
        return map;
    }

    /**
     * Thread-Safe Singleton Getter utilizing Double-Checked Locking.
     */
    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Obtains a fresh active database connection.
     * Uses try-with-resources caller pattern for proper cleanup.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(this.jdbcUrl, this.dbUser, this.dbPass);
    }

    /**
     * Quick health-check utility for server startup verification.
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DBConnection] Test Connection Failed: " + e.getMessage());
            return false;
        }
    }
}
