package com.realestate.dao;

import java.sql.Connection;
import java.sql.SQLException;
import com.realestate.database.DBConnection;
import com.realestate.database.SupabaseClient;

/**
 * Abstract Base DAO providing connection management and resource safety.
 * Supports both Supabase PostgREST (when configured via .env/env vars) and MySQL JDBC fallback.
 */
public abstract class BaseDAO {

    protected boolean useSupabase() {
        return SupabaseClient.getInstance().isConfigured();
    }

    protected SupabaseClient supabase() {
        return SupabaseClient.getInstance();
    }

    /**
     * Obtains an active connection from the Singleton DBConnection pool.
     */
    protected Connection getConnection() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    /**
     * Safely closes one or more closeable resources (ResultSets, Statements, Connections).
     */
    protected void closeQuietly(AutoCloseable... closeables) {
        if (closeables == null) return;
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
