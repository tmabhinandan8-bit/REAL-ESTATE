package com.realestate.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.realestate.model.verification.OwnerVerification;
import com.realestate.util.JsonUtil;

/**
 * Data Access Object for Owner Document Verification.
 * Supports Supabase PostgREST and MySQL JDBC.
 */
public class VerificationDAO extends BaseDAO {

    private static final String SUPABASE_SELECT =
        "select=*,user:users!owner_verifications_user_id_fkey(full_name,email)";

    public OwnerVerification submitVerification(OwnerVerification v) throws SQLException {
        String normalizedProofType = normalizeProofType(v.getIdentityProofType());
        v.setIdentityProofType(normalizedProofType);

        if (useSupabase()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("user_id", v.getUserId());
            payload.put("identity_proof_type", normalizedProofType);
            payload.put("identity_proof_number", v.getIdentityProofNumber());
            payload.put("document_path", v.getDocumentPath());
            payload.put("property_address", v.getPropertyAddress());
            payload.put("verification_status", "PENDING");

            Map<String, Object> created = supabase().post("owner_verifications", payload);
            v.setVerificationId(JsonUtil.getInt(created, "verification_id"));
            return v;
        }

        String sql = "INSERT INTO owner_verifications (user_id, identity_proof_type, identity_proof_number, " +
                     "document_path, property_address, verification_status) VALUES (?, ?, ?, ?, ?, 'PENDING')";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, v.getUserId());
            stmt.setString(2, normalizedProofType);
            stmt.setString(3, v.getIdentityProofNumber());
            stmt.setString(4, v.getDocumentPath());
            stmt.setString(5, v.getPropertyAddress());

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    v.setVerificationId(rs.getInt(1));
                }
            }
        }
        return v;
    }

    public List<OwnerVerification> getPendingVerifications() throws SQLException {
        List<OwnerVerification> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "owner_verifications?" + SUPABASE_SELECT + "&verification_status=eq.PENDING&order=submitted_at.asc"
            );
            for (Map<String, Object> row : rows) list.add(mapVerificationFromMap(row));
            return list;
        }

        String sql = "SELECT ov.*, u.full_name as user_name, u.email as user_email " +
                     "FROM owner_verifications ov " +
                     "JOIN users u ON ov.user_id = u.user_id " +
                     "WHERE ov.verification_status = 'PENDING' ORDER BY ov.submitted_at ASC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapVerification(rs));
            }
        }
        return list;
    }

    public boolean updateVerificationStatus(int verificationId, String status, String adminNotes) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("verification_status", status);
            patch.put("admin_notes", adminNotes);
            patch.put("verified_at", Instant.now().toString());

            List<Map<String, Object>> updated = supabase().patch(
                "owner_verifications?verification_id=eq." + verificationId, patch
            );
            if (updated.isEmpty()) return false;
            int userId = JsonUtil.getInt(updated.get(0), "user_id");
            if (userId > 0 && "APPROVED".equalsIgnoreCase(status)) {
                Map<String, Object> uPatch = new HashMap<>();
                uPatch.put("is_verified", true);
                supabase().patch("users?user_id=eq." + userId, uPatch);
            }
            return true;
        }

        String fetchUserSql = "SELECT user_id FROM owner_verifications WHERE verification_id = ?";
        String updateVerSql = "UPDATE owner_verifications SET verification_status = ?, admin_notes = ?, verified_at = NOW() WHERE verification_id = ?";
        String updateUserSql = "UPDATE users SET is_verified = ? WHERE user_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            int userId = 0;
            try (PreparedStatement fetchStmt = conn.prepareStatement(fetchUserSql)) {
                fetchStmt.setInt(1, verificationId);
                try (ResultSet rs = fetchStmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                    }
                }
            }

            try (PreparedStatement verStmt = conn.prepareStatement(updateVerSql)) {
                verStmt.setString(1, status);
                verStmt.setString(2, adminNotes);
                verStmt.setInt(3, verificationId);
                verStmt.executeUpdate();
            }

            if (userId > 0 && "APPROVED".equalsIgnoreCase(status)) {
                try (PreparedStatement uStmt = conn.prepareStatement(updateUserSql)) {
                    uStmt.setBoolean(1, true);
                    uStmt.setInt(2, userId);
                    uStmt.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public OwnerVerification getVerificationByUserId(int userId) throws SQLException {
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "owner_verifications?" + SUPABASE_SELECT + "&user_id=eq." + userId + "&order=submitted_at.desc&limit=1"
            );
            return rows.isEmpty() ? null : mapVerificationFromMap(rows.get(0));
        }

        String sql = "SELECT ov.*, u.full_name as user_name, u.email as user_email " +
                     "FROM owner_verifications ov " +
                     "JOIN users u ON ov.user_id = u.user_id " +
                     "WHERE ov.user_id = ? ORDER BY ov.submitted_at DESC LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapVerification(rs);
                }
            }
        }
        return null;
    }

    private static String normalizeProofType(String proofType) {
        if (proofType == null || proofType.trim().isEmpty()) return "AADHAAR";
        String p = proofType.trim().toUpperCase();
        if (p.equals("AADHAAR") || p.equals("PAN") || p.equals("PASSPORT") || p.equals("DRIVING_LICENSE") || p.equals("RERA_CERT")) {
            return p;
        }
        if (p.contains("AADHAAR")) return "AADHAAR";
        if (p.contains("PAN")) return "PAN";
        if (p.contains("PASSPORT")) return "PASSPORT";
        if (p.contains("DRIV")) return "DRIVING_LICENSE";
        if (p.contains("RERA")) return "RERA_CERT";
        return "AADHAAR";
    }

    private OwnerVerification mapVerificationFromMap(Map<String, Object> row) {
        OwnerVerification ov = new OwnerVerification();
        ov.setVerificationId(JsonUtil.getInt(row, "verification_id"));
        ov.setUserId(JsonUtil.getInt(row, "user_id"));
        Map<String, Object> usr = JsonUtil.getMap(row, "user");
        if (usr != null) {
            ov.setUserName(JsonUtil.getString(usr, "full_name"));
            ov.setUserEmail(JsonUtil.getString(usr, "email"));
        }
        ov.setIdentityProofType(JsonUtil.getString(row, "identity_proof_type"));
        ov.setIdentityProofNumber(JsonUtil.getString(row, "identity_proof_number"));
        ov.setDocumentPath(JsonUtil.getString(row, "document_path"));
        ov.setPropertyAddress(JsonUtil.getString(row, "property_address"));
        ov.setVerificationStatus(JsonUtil.getString(row, "verification_status"));
        ov.setAdminNotes(JsonUtil.getString(row, "admin_notes"));
        ov.setSubmittedAt(JsonUtil.getTimestamp(row, "submitted_at"));
        ov.setVerifiedAt(JsonUtil.getTimestamp(row, "verified_at"));
        return ov;
    }

    private OwnerVerification mapVerification(ResultSet rs) throws SQLException {
        OwnerVerification ov = new OwnerVerification();
        ov.setVerificationId(rs.getInt("verification_id"));
        ov.setUserId(rs.getInt("user_id"));
        ov.setUserName(rs.getString("user_name"));
        ov.setUserEmail(rs.getString("user_email"));
        ov.setIdentityProofType(rs.getString("identity_proof_type"));
        ov.setIdentityProofNumber(rs.getString("identity_proof_number"));
        ov.setDocumentPath(rs.getString("document_path"));
        ov.setPropertyAddress(rs.getString("property_address"));
        ov.setVerificationStatus(rs.getString("verification_status"));
        ov.setAdminNotes(rs.getString("admin_notes"));
        ov.setSubmittedAt(rs.getTimestamp("submitted_at"));
        ov.setVerifiedAt(rs.getTimestamp("verified_at"));
        return ov;
    }
}
