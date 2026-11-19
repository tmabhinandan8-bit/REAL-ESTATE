package com.realestate.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.realestate.util.JsonUtil;

/**
 * Data Access Object for Admin Analytics and Dashboard KPIs.
 * Supports Supabase PostgREST (vw_admin_dashboard_kpis) and MySQL JDBC.
 */
public class AnalyticsDAO extends BaseDAO {

    public Map<String, Integer> getAdminDashboardStats() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();

        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get("vw_admin_dashboard_kpis?select=*&limit=1");
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                stats.put("totalUsers", JsonUtil.getInt(r, "total_users"));
                stats.put("verifiedOwners", JsonUtil.getInt(r, "verified_owners"));
                stats.put("totalProperties", JsonUtil.getInt(r, "total_properties"));
                stats.put("propertiesForSale", JsonUtil.getInt(r, "properties_for_sale"));
                stats.put("propertiesForRent", JsonUtil.getInt(r, "properties_for_rent_lease"));
                stats.put("pendingApprovals", JsonUtil.getInt(r, "pending_property_approvals"));
                stats.put("totalAppointments", JsonUtil.getInt(r, "total_appointments"));
                stats.put("purchaseTransactions", JsonUtil.getInt(r, "total_purchase_transactions"));
            }
            List<Map<String, Object>> pendingVer = supabase().get("owner_verifications?select=verification_id&verification_status=eq.PENDING");
            stats.put("pendingVerifications", pendingVer.size());
            return stats;
        }

        String sql = "SELECT " +
                     "(SELECT COUNT(*) FROM users) as total_users, " +
                     "(SELECT COUNT(*) FROM users WHERE is_verified = TRUE AND user_role IN ('PROPERTY_OWNER', 'SELLER')) as verified_owners, " +
                     "(SELECT COUNT(*) FROM properties) as total_properties, " +
                     "(SELECT COUNT(*) FROM properties WHERE purpose = 'BUY') as properties_for_sale, " +
                     "(SELECT COUNT(*) FROM properties WHERE purpose IN ('RENT', 'LEASE')) as properties_for_rent, " +
                     "(SELECT COUNT(*) FROM properties WHERE approval_status = 'PENDING') as pending_approvals, " +
                     "(SELECT COUNT(*) FROM property_visits) as total_appointments, " +
                     "(SELECT COUNT(*) FROM purchase_transactions) as purchase_transactions, " +
                     "(SELECT COUNT(*) FROM owner_verifications WHERE verification_status = 'PENDING') as pending_verifications";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                stats.put("totalUsers", rs.getInt("total_users"));
                stats.put("verifiedOwners", rs.getInt("verified_owners"));
                stats.put("totalProperties", rs.getInt("total_properties"));
                stats.put("propertiesForSale", rs.getInt("properties_for_sale"));
                stats.put("propertiesForRent", rs.getInt("properties_for_rent"));
                stats.put("pendingApprovals", rs.getInt("pending_approvals"));
                stats.put("totalAppointments", rs.getInt("total_appointments"));
                stats.put("purchaseTransactions", rs.getInt("purchase_transactions"));
                stats.put("pendingVerifications", rs.getInt("pending_verifications"));
            }
        }
        return stats;
    }
}
