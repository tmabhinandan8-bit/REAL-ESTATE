package com.realestate.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import com.realestate.database.SupabaseClient;
import com.realestate.exceptions.AuthenticationException;
import com.realestate.model.user.*;
import com.realestate.util.JsonUtil;
import com.realestate.util.PasswordUtil;

/**
 * Data Access Object for User Authentication, Registration, and Management.
 * Supports Supabase PostgREST and MySQL JDBC.
 */
public class UserDAO extends BaseDAO {

    /**
     * Factory method instantiating the appropriate polymorphic User subclass based on role.
     */
    public static User createUserInstance(String role) {
        if (role == null) return new Buyer();
        switch (role.toUpperCase()) {
            case "ADMIN": return new Admin();
            case "SELLER": return new Seller();
            case "TENANT": return new Tenant();
            case "PROPERTY_OWNER": return new PropertyOwner();
            case "AGENT": return new Agent();
            case "BUYER":
            default:
                return new Buyer();
        }
    }

    /**
     * Registers a new user with salt and SHA-256 hash.
     */
    public User registerUser(User user, String rawPassword) throws SQLException, AuthenticationException {
        if (useSupabase()) {
            List<Map<String, Object>> existing = supabase().get(
                "users?select=user_id&email=eq." + SupabaseClient.encode(user.getEmail())
            );
            if (!existing.isEmpty()) {
                throw new AuthenticationException("An account with this email address already exists.");
            }

            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(rawPassword, salt);
            String role = user.getUserRole() != null ? user.getUserRole().toUpperCase() : "BUYER";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("full_name", user.getFullName());
            payload.put("email", user.getEmail());
            payload.put("mobile_number", user.getMobileNumber());
            payload.put("password_hash", hash);
            payload.put("password_salt", salt);
            payload.put("user_role", role);
            payload.put("is_verified", "ADMIN".equalsIgnoreCase(role));
            payload.put("is_active", true);

            Map<String, Object> created = supabase().post("users", payload);
            user.setUserId(JsonUtil.getInt(created, "user_id"));
            user.setPasswordSalt(salt);
            user.setPasswordHash(hash);
            user.setVerified(JsonUtil.getBoolean(created, "is_verified"));
            user.setActive(true);
            return user;
        }

        String checkSql = "SELECT user_id FROM users WHERE email = ?";
        String insertSql = "INSERT INTO users (full_name, email, mobile_number, password_hash, password_salt, user_role, is_verified, is_active) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, user.getEmail());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        throw new AuthenticationException("An account with this email address already exists.");
                    }
                }
            }

            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(rawPassword, salt);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, user.getFullName());
                insertStmt.setString(2, user.getEmail());
                insertStmt.setString(3, user.getMobileNumber());
                insertStmt.setString(4, hash);
                insertStmt.setString(5, salt);
                insertStmt.setString(6, user.getUserRole() != null ? user.getUserRole().toUpperCase() : "BUYER");
                insertStmt.setBoolean(7, "ADMIN".equalsIgnoreCase(user.getUserRole()));
                insertStmt.setBoolean(8, true);

                insertStmt.executeUpdate();

                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setUserId(generatedKeys.getInt(1));
                        user.setPasswordSalt(salt);
                        user.setPasswordHash(hash);
                    }
                }
            }
        }
        return user;
    }

    /**
     * Authenticates a user using email and password.
     */
    public User authenticate(String email, String rawPassword) throws SQLException, AuthenticationException {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "users?select=*&email=eq." + SupabaseClient.encode(normalizedEmail) + "&is_active=eq.true&limit=1"
            );
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                String storedSalt = JsonUtil.getString(row, "password_salt");
                String storedHash = JsonUtil.getString(row, "password_hash");
                if (PasswordUtil.verifyPassword(rawPassword, storedSalt, storedHash)) {
                    User user = createUserInstance(JsonUtil.getString(row, "user_role"));
                    mapUserFromMap(row, user);
                    return user;
                }
            }
            throw new AuthenticationException("Invalid email or password.");
        }

        String sql = "SELECT * FROM users WHERE email = ? AND is_active = TRUE";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedEmail);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedSalt = rs.getString("password_salt");
                    String storedHash = rs.getString("password_hash");

                    if (PasswordUtil.verifyPassword(rawPassword, storedSalt, storedHash)) {
                        String role = rs.getString("user_role");
                        User user = createUserInstance(role);
                        mapUserFromResultSet(rs, user);
                        return user;
                    }
                }
            }
        }
        throw new AuthenticationException("Invalid email or password.");
    }

    /**
     * Retrieves user by ID.
     */
    public User getUserById(int userId) throws SQLException {
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get("users?select=*&user_id=eq." + userId + "&limit=1");
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                User user = createUserInstance(JsonUtil.getString(row, "user_role"));
                mapUserFromMap(row, user);
                return user;
            }
            return null;
        }

        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("user_role");
                    User user = createUserInstance(role);
                    mapUserFromResultSet(rs, user);
                    return user;
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all registered users for Admin panel.
     */
    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get("users?select=*&order=created_at.desc");
            for (Map<String, Object> row : rows) {
                User user = createUserInstance(JsonUtil.getString(row, "user_role"));
                mapUserFromMap(row, user);
                list.add(user);
            }
            return list;
        }

        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                User user = createUserInstance(rs.getString("user_role"));
                mapUserFromResultSet(rs, user);
                list.add(user);
            }
        }
        return list;
    }

    /**
     * Retrieves user by email address.
     */
    public User findByEmail(String email) throws SQLException {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "users?select=*&email=eq." + SupabaseClient.encode(normalizedEmail) + "&limit=1"
            );
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                User user = createUserInstance(JsonUtil.getString(row, "user_role"));
                mapUserFromMap(row, user);
                return user;
            }
            return null;
        }

        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedEmail);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = createUserInstance(rs.getString("user_role"));
                    mapUserFromResultSet(rs, user);
                    return user;
                }
            }
        }
        return null;
    }

    /**
     * Updates a user's salted SHA-256 password hash by email address.
     */
    public boolean updatePasswordByEmail(String email, String newRawPassword) throws SQLException {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(newRawPassword, salt);

        if (useSupabase()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("password_hash", hash);
            body.put("password_salt", salt);
            return !supabase().patch("users?email=eq." + SupabaseClient.encode(normalizedEmail), body).isEmpty();
        }

        String sql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash);
            stmt.setString(2, salt);
            stmt.setString(3, normalizedEmail);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates verification status.
     */
    public boolean updateVerificationStatus(int userId, boolean isVerified) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> body = new HashMap<>();
            body.put("is_verified", isVerified);
            return !supabase().patch("users?user_id=eq." + userId, body).isEmpty();
        }

        String sql = "UPDATE users SET is_verified = ? WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isVerified);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    private void mapUserFromMap(Map<String, Object> row, User user) {
        user.setUserId(JsonUtil.getInt(row, "user_id"));
        user.setFullName(JsonUtil.getString(row, "full_name"));
        user.setEmail(JsonUtil.getString(row, "email"));
        user.setMobileNumber(JsonUtil.getString(row, "mobile_number"));
        user.setUserRole(JsonUtil.getString(row, "user_role"));
        user.setVerified(JsonUtil.getBoolean(row, "is_verified"));
        user.setActive(JsonUtil.getBoolean(row, "is_active"));
        user.setCreatedAt(JsonUtil.getTimestamp(row, "created_at"));
    }

    private void mapUserFromResultSet(ResultSet rs, User user) throws SQLException {
        user.setUserId(rs.getInt("user_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setMobileNumber(rs.getString("mobile_number"));
        user.setUserRole(rs.getString("user_role"));
        user.setVerified(rs.getBoolean("is_verified"));
        user.setActive(rs.getBoolean("is_active"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
    }
}
