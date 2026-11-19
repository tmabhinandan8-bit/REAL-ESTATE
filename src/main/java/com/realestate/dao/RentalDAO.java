package com.realestate.dao;

import java.sql.*;
import java.util.*;
import com.realestate.model.rental.RentalAgreement;
import com.realestate.model.rental.RentPayment;
import com.realestate.util.JsonUtil;

/**
 * Data Access Object for Rental Agreements and Rent Payments.
 * Supports Supabase PostgREST and MySQL JDBC.
 */
public class RentalDAO extends BaseDAO {

    private static final String SUPABASE_SELECT =
        "select=*,properties(title),owner:users!rental_agreements_owner_id_fkey(full_name),tenant:users!rental_agreements_tenant_id_fkey(full_name)";

    public RentalAgreement createRentalAgreement(RentalAgreement agreement) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("property_id", agreement.getPropertyId());
            payload.put("owner_id", agreement.getOwnerId());
            payload.put("tenant_id", agreement.getTenantId());
            payload.put("monthly_rent", agreement.getMonthlyRent());
            payload.put("security_deposit", agreement.getSecurityDeposit());
            payload.put("agreement_start_date", agreement.getAgreementStartDate().toString());
            payload.put("agreement_end_date", agreement.getAgreementEndDate().toString());
            payload.put("agreement_status", "ACTIVE");
            payload.put("terms_and_conditions", agreement.getTermsAndConditions());

            Map<String, Object> created = supabase().post("rental_agreements", payload);
            agreement.setAgreementId(JsonUtil.getInt(created, "agreement_id"));

            Map<String, Object> propPatch = new HashMap<>();
            propPatch.put("listing_status", "RENTED");
            supabase().patch("properties?property_id=eq." + agreement.getPropertyId(), propPatch);
            return agreement;
        }

        String sql = "INSERT INTO rental_agreements (property_id, owner_id, tenant_id, monthly_rent, security_deposit, " +
                     "agreement_start_date, agreement_end_date, agreement_status, terms_and_conditions) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";
        String propSql = "UPDATE properties SET listing_status = 'RENTED' WHERE property_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, agreement.getPropertyId());
                stmt.setInt(2, agreement.getOwnerId());
                stmt.setInt(3, agreement.getTenantId());
                stmt.setDouble(4, agreement.getMonthlyRent());
                stmt.setDouble(5, agreement.getSecurityDeposit());
                stmt.setDate(6, agreement.getAgreementStartDate());
                stmt.setDate(7, agreement.getAgreementEndDate());
                stmt.setString(8, agreement.getTermsAndConditions());

                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        agreement.setAgreementId(rs.getInt(1));
                    }
                }
            }

            try (PreparedStatement propStmt = conn.prepareStatement(propSql)) {
                propStmt.setInt(1, agreement.getPropertyId());
                propStmt.executeUpdate();
            }

            conn.commit();
            return agreement;
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

    public List<RentalAgreement> getAgreementsByUser(int userId) throws SQLException {
        List<RentalAgreement> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "rental_agreements?" + SUPABASE_SELECT + "&or=(tenant_id.eq." + userId + ",owner_id.eq." + userId + ")&order=created_at.desc"
            );
            for (Map<String, Object> row : rows) {
                RentalAgreement ra = new RentalAgreement();
                ra.setAgreementId(JsonUtil.getInt(row, "agreement_id"));
                ra.setPropertyId(JsonUtil.getInt(row, "property_id"));
                Map<String, Object> prop = JsonUtil.getMap(row, "properties");
                if (prop != null) ra.setPropertyTitle(JsonUtil.getString(prop, "title"));
                ra.setOwnerId(JsonUtil.getInt(row, "owner_id"));
                Map<String, Object> owner = JsonUtil.getMap(row, "owner");
                if (owner != null) ra.setOwnerName(JsonUtil.getString(owner, "full_name"));
                ra.setTenantId(JsonUtil.getInt(row, "tenant_id"));
                Map<String, Object> tenant = JsonUtil.getMap(row, "tenant");
                if (tenant != null) ra.setTenantName(JsonUtil.getString(tenant, "full_name"));
                ra.setMonthlyRent(JsonUtil.getDouble(row, "monthly_rent"));
                ra.setSecurityDeposit(JsonUtil.getDouble(row, "security_deposit"));
                ra.setAgreementStartDate(JsonUtil.getDate(row, "agreement_start_date"));
                ra.setAgreementEndDate(JsonUtil.getDate(row, "agreement_end_date"));
                ra.setAgreementStatus(JsonUtil.getString(row, "agreement_status"));
                ra.setTermsAndConditions(JsonUtil.getString(row, "terms_and_conditions"));
                ra.setCreatedAt(JsonUtil.getTimestamp(row, "created_at"));
                list.add(ra);
            }
            return list;
        }

        String sql = "SELECT ra.*, p.title as property_title, o.full_name as owner_name, t.full_name as tenant_name " +
                     "FROM rental_agreements ra " +
                     "JOIN properties p ON ra.property_id = p.property_id " +
                     "JOIN users o ON ra.owner_id = o.user_id " +
                     "JOIN users t ON ra.tenant_id = t.user_id " +
                     "WHERE ra.tenant_id = ? OR ra.owner_id = ? " +
                     "ORDER BY ra.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RentalAgreement ra = new RentalAgreement();
                    ra.setAgreementId(rs.getInt("agreement_id"));
                    ra.setPropertyId(rs.getInt("property_id"));
                    ra.setPropertyTitle(rs.getString("property_title"));
                    ra.setOwnerId(rs.getInt("owner_id"));
                    ra.setOwnerName(rs.getString("owner_name"));
                    ra.setTenantId(rs.getInt("tenant_id"));
                    ra.setTenantName(rs.getString("tenant_name"));
                    ra.setMonthlyRent(rs.getDouble("monthly_rent"));
                    ra.setSecurityDeposit(rs.getDouble("security_deposit"));
                    ra.setAgreementStartDate(rs.getDate("agreement_start_date"));
                    ra.setAgreementEndDate(rs.getDate("agreement_end_date"));
                    ra.setAgreementStatus(rs.getString("agreement_status"));
                    ra.setTermsAndConditions(rs.getString("terms_and_conditions"));
                    ra.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(ra);
                }
            }
        }
        return list;
    }

    public RentPayment recordRentPayment(RentPayment payment) throws SQLException {
        String method = normalizePaymentMethod(payment.getPaymentMethod());
        payment.setPaymentMethod(method);

        if (useSupabase()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agreement_id", payment.getAgreementId());
            payload.put("payment_month", payment.getPaymentMonth());
            payload.put("amount_paid", payment.getAmountPaid());
            payload.put("payment_date", payment.getPaymentDate().toString());
            payload.put("payment_method", method);
            payload.put("transaction_ref", payment.getTransactionRef());
            payload.put("payment_status", payment.getPaymentStatus());

            Map<String, Object> created = supabase().post("rent_payments", payload);
            payment.setRentPaymentId(JsonUtil.getInt(created, "rent_payment_id"));
            return payment;
        }

        String sql = "INSERT INTO rent_payments (agreement_id, payment_month, amount_paid, payment_date, payment_method, transaction_ref, payment_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, payment.getAgreementId());
            stmt.setString(2, payment.getPaymentMonth());
            stmt.setDouble(3, payment.getAmountPaid());
            stmt.setDate(4, payment.getPaymentDate());
            stmt.setString(5, method);
            stmt.setString(6, payment.getTransactionRef());
            stmt.setString(7, payment.getPaymentStatus());

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    payment.setRentPaymentId(rs.getInt(1));
                }
            }
        }
        return payment;
    }

    public List<RentPayment> getRentPaymentsForAgreement(int agreementId) throws SQLException {
        List<RentPayment> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "rent_payments?select=*&agreement_id=eq." + agreementId + "&order=payment_date.desc"
            );
            for (Map<String, Object> row : rows) {
                RentPayment rp = new RentPayment();
                rp.setRentPaymentId(JsonUtil.getInt(row, "rent_payment_id"));
                rp.setAgreementId(JsonUtil.getInt(row, "agreement_id"));
                rp.setPaymentMonth(JsonUtil.getString(row, "payment_month"));
                rp.setAmountPaid(JsonUtil.getDouble(row, "amount_paid"));
                rp.setPaymentDate(JsonUtil.getDate(row, "payment_date"));
                rp.setPaymentMethod(JsonUtil.getString(row, "payment_method"));
                rp.setTransactionRef(JsonUtil.getString(row, "transaction_ref"));
                rp.setPaymentStatus(JsonUtil.getString(row, "payment_status"));
                list.add(rp);
            }
            return list;
        }

        String sql = "SELECT * FROM rent_payments WHERE agreement_id = ? ORDER BY payment_date DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, agreementId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RentPayment rp = new RentPayment();
                    rp.setRentPaymentId(rs.getInt("rent_payment_id"));
                    rp.setAgreementId(rs.getInt("agreement_id"));
                    rp.setPaymentMonth(rs.getString("payment_month"));
                    rp.setAmountPaid(rs.getDouble("amount_paid"));
                    rp.setPaymentDate(rs.getDate("payment_date"));
                    rp.setPaymentMethod(rs.getString("payment_method"));
                    rp.setTransactionRef(rs.getString("transaction_ref"));
                    rp.setPaymentStatus(rs.getString("payment_status"));
                    list.add(rp);
                }
            }
        }
        return list;
    }

    private static String normalizePaymentMethod(String method) {
        if (method == null || method.trim().isEmpty()) return "SIMULATED_UPI";
        String m = method.trim().toUpperCase();
        if ("SIMULATED_UPI".equals(m) || "SIMULATED_NEFT".equals(m) || "SIMULATED_CARD".equals(m) || "BANK_CHEQUE".equals(m)) {
            return m;
        }
        if (m.contains("NET") || m.contains("NEFT") || m.contains("BANK")) return "SIMULATED_NEFT";
        if (m.contains("CARD")) return "SIMULATED_CARD";
        if (m.contains("CHEQUE")) return "BANK_CHEQUE";
        return "SIMULATED_UPI";
    }
}
