package com.realestate.dao;

import java.sql.*;
import java.util.*;
import com.realestate.model.purchase.PurchaseTransaction;
import com.realestate.util.JsonUtil;

/**
 * Data Access Object for Purchase Transactions.
 * Guarantees property status transition to SOLD.
 * Supports Supabase PostgREST and MySQL JDBC.
 */
public class PurchaseDAO extends BaseDAO {

    private static final String SUPABASE_SELECT =
        "select=*,properties(title),buyer:users!purchase_transactions_buyer_id_fkey(full_name),seller:users!purchase_transactions_seller_id_fkey(full_name)";

    public PurchaseTransaction recordPurchaseTransaction(PurchaseTransaction txn) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("property_id", txn.getPropertyId());
            payload.put("buyer_id", txn.getBuyerId());
            payload.put("seller_id", txn.getSellerId());
            payload.put("purchase_price", txn.getPurchasePrice());
            payload.put("payment_status", txn.getPaymentStatus());
            payload.put("transaction_reference", txn.getTransactionReference());
            payload.put("remarks", txn.getRemarks());

            Map<String, Object> created = supabase().post("purchase_transactions", payload);
            txn.setTransactionId(JsonUtil.getInt(created, "transaction_id"));

            Map<String, Object> propPatch = new HashMap<>();
            propPatch.put("listing_status", "SOLD");
            supabase().patch("properties?property_id=eq." + txn.getPropertyId(), propPatch);
            return txn;
        }

        String insertSql = "INSERT INTO purchase_transactions (property_id, buyer_id, seller_id, purchase_price, " +
                           "payment_status, transaction_reference, remarks) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String updatePropSql = "UPDATE properties SET listing_status = 'SOLD' WHERE property_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, txn.getPropertyId());
                stmt.setInt(2, txn.getBuyerId());
                stmt.setInt(3, txn.getSellerId());
                stmt.setDouble(4, txn.getPurchasePrice());
                stmt.setString(5, txn.getPaymentStatus());
                stmt.setString(6, txn.getTransactionReference());
                stmt.setString(7, txn.getRemarks());

                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        txn.setTransactionId(rs.getInt(1));
                    }
                }
            }

            try (PreparedStatement propStmt = conn.prepareStatement(updatePropSql)) {
                propStmt.setInt(1, txn.getPropertyId());
                propStmt.executeUpdate();
            }

            conn.commit();
            return txn;
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

    public List<PurchaseTransaction> getPurchasesByUser(int userId) throws SQLException {
        List<PurchaseTransaction> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "purchase_transactions?" + SUPABASE_SELECT + "&or=(buyer_id.eq." + userId + ",seller_id.eq." + userId + ")&order=transaction_date.desc"
            );
            for (Map<String, Object> row : rows) list.add(mapTransactionFromMap(row));
            return list;
        }

        String sql = "SELECT pt.*, p.title as property_title, b.full_name as buyer_name, s.full_name as seller_name " +
                     "FROM purchase_transactions pt " +
                     "JOIN properties p ON pt.property_id = p.property_id " +
                     "JOIN users b ON pt.buyer_id = b.user_id " +
                     "JOIN users s ON pt.seller_id = s.user_id " +
                     "WHERE pt.buyer_id = ? OR pt.seller_id = ? " +
                     "ORDER BY pt.transaction_date DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTransaction(rs));
                }
            }
        }
        return list;
    }

    public List<PurchaseTransaction> getAllPurchaseTransactions() throws SQLException {
        List<PurchaseTransaction> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "purchase_transactions?" + SUPABASE_SELECT + "&order=transaction_date.desc"
            );
            for (Map<String, Object> row : rows) list.add(mapTransactionFromMap(row));
            return list;
        }

        String sql = "SELECT pt.*, p.title as property_title, b.full_name as buyer_name, s.full_name as seller_name " +
                     "FROM purchase_transactions pt " +
                     "JOIN properties p ON pt.property_id = p.property_id " +
                     "JOIN users b ON pt.buyer_id = b.user_id " +
                     "JOIN users s ON pt.seller_id = s.user_id " +
                     "ORDER BY pt.transaction_date DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapTransaction(rs));
            }
        }
        return list;
    }

    private PurchaseTransaction mapTransactionFromMap(Map<String, Object> row) {
        PurchaseTransaction pt = new PurchaseTransaction();
        pt.setTransactionId(JsonUtil.getInt(row, "transaction_id"));
        pt.setPropertyId(JsonUtil.getInt(row, "property_id"));
        Map<String, Object> prop = JsonUtil.getMap(row, "properties");
        if (prop != null) pt.setPropertyTitle(JsonUtil.getString(prop, "title"));
        pt.setBuyerId(JsonUtil.getInt(row, "buyer_id"));
        Map<String, Object> buyer = JsonUtil.getMap(row, "buyer");
        if (buyer != null) pt.setBuyerName(JsonUtil.getString(buyer, "full_name"));
        pt.setSellerId(JsonUtil.getInt(row, "seller_id"));
        Map<String, Object> seller = JsonUtil.getMap(row, "seller");
        if (seller != null) pt.setSellerName(JsonUtil.getString(seller, "full_name"));
        pt.setPurchasePrice(JsonUtil.getDouble(row, "purchase_price"));
        pt.setTransactionDate(JsonUtil.getTimestamp(row, "transaction_date"));
        pt.setPaymentStatus(JsonUtil.getString(row, "payment_status"));
        pt.setTransactionReference(JsonUtil.getString(row, "transaction_reference"));
        pt.setRemarks(JsonUtil.getString(row, "remarks"));
        return pt;
    }

    private PurchaseTransaction mapTransaction(ResultSet rs) throws SQLException {
        PurchaseTransaction pt = new PurchaseTransaction();
        pt.setTransactionId(rs.getInt("transaction_id"));
        pt.setPropertyId(rs.getInt("property_id"));
        pt.setPropertyTitle(rs.getString("property_title"));
        pt.setBuyerId(rs.getInt("buyer_id"));
        pt.setBuyerName(rs.getString("buyer_name"));
        pt.setSellerId(rs.getInt("seller_id"));
        pt.setSellerName(rs.getString("seller_name"));
        pt.setPurchasePrice(rs.getDouble("purchase_price"));
        pt.setTransactionDate(rs.getTimestamp("transaction_date"));
        pt.setPaymentStatus(rs.getString("payment_status"));
        pt.setTransactionReference(rs.getString("transaction_reference"));
        pt.setRemarks(rs.getString("remarks"));
        return pt;
    }
}
