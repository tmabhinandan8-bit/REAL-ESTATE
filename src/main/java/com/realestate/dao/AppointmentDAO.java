package com.realestate.dao;

import java.sql.*;
import java.util.*;
import com.realestate.model.appointment.PropertyVisit;
import com.realestate.util.JsonUtil;

/**
 * Data Access Object for Property Visit Appointments.
 * Supports Supabase PostgREST and MySQL JDBC.
 */
public class AppointmentDAO extends BaseDAO {

    private static final String SUPABASE_SELECT = "select=*,properties(title,city,owner_id),users(full_name,email)";

    public PropertyVisit scheduleVisit(PropertyVisit visit) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("property_id", visit.getPropertyId());
            payload.put("user_id", visit.getUserId());
            payload.put("visit_date", visit.getVisitDate().toString());
            payload.put("visit_time", visit.getVisitTime().toString());
            payload.put("contact_number", visit.getContactNumber());
            payload.put("notes", visit.getNotes());
            payload.put("appointment_status", "PENDING");

            Map<String, Object> created = supabase().post("property_visits", payload);
            visit.setVisitId(JsonUtil.getInt(created, "visit_id"));
            return visit;
        }

        String sql = "INSERT INTO property_visits (property_id, user_id, visit_date, visit_time, contact_number, notes, appointment_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'PENDING')";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, visit.getPropertyId());
            stmt.setInt(2, visit.getUserId());
            stmt.setDate(3, visit.getVisitDate());
            stmt.setTime(4, visit.getVisitTime());
            stmt.setString(5, visit.getContactNumber());
            stmt.setString(6, visit.getNotes());

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    visit.setVisitId(rs.getInt(1));
                }
            }
        }
        return visit;
    }

    public List<PropertyVisit> getVisitsByUser(int userId) throws SQLException {
        List<PropertyVisit> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "property_visits?" + SUPABASE_SELECT + "&user_id=eq." + userId + "&order=visit_date.desc,visit_time.desc"
            );
            for (Map<String, Object> row : rows) list.add(mapVisitFromMap(row));
            return list;
        }

        String sql = "SELECT pv.*, p.title as property_title, p.city as property_city, u.full_name as user_name, u.email as user_email " +
                     "FROM property_visits pv " +
                     "JOIN properties p ON pv.property_id = p.property_id " +
                     "JOIN users u ON pv.user_id = u.user_id " +
                     "WHERE pv.user_id = ? ORDER BY pv.visit_date DESC, pv.visit_time DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapVisit(rs));
                }
            }
        }
        return list;
    }

    public List<PropertyVisit> getVisitsByOwner(int ownerId) throws SQLException {
        List<PropertyVisit> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "property_visits?select=*,properties!inner(title,city,owner_id),users(full_name,email)&properties.owner_id=eq." + ownerId + "&order=visit_date.desc,visit_time.desc"
            );
            for (Map<String, Object> row : rows) list.add(mapVisitFromMap(row));
            return list;
        }

        String sql = "SELECT pv.*, p.title as property_title, p.city as property_city, u.full_name as user_name, u.email as user_email " +
                     "FROM property_visits pv " +
                     "JOIN properties p ON pv.property_id = p.property_id " +
                     "JOIN users u ON pv.user_id = u.user_id " +
                     "WHERE p.owner_id = ? ORDER BY pv.visit_date DESC, pv.visit_time DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapVisit(rs));
                }
            }
        }
        return list;
    }

    public List<PropertyVisit> getAllVisits() throws SQLException {
        List<PropertyVisit> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "property_visits?" + SUPABASE_SELECT + "&order=created_at.desc"
            );
            for (Map<String, Object> row : rows) list.add(mapVisitFromMap(row));
            return list;
        }

        String sql = "SELECT pv.*, p.title as property_title, p.city as property_city, u.full_name as user_name, u.email as user_email " +
                     "FROM property_visits pv " +
                     "JOIN properties p ON pv.property_id = p.property_id " +
                     "JOIN users u ON pv.user_id = u.user_id " +
                     "ORDER BY pv.created_at DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapVisit(rs));
            }
        }
        return list;
    }

    public boolean updateVisitStatus(int visitId, String status) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> body = new HashMap<>();
            body.put("appointment_status", status);
            return !supabase().patch("property_visits?visit_id=eq." + visitId, body).isEmpty();
        }

        String sql = "UPDATE property_visits SET appointment_status = ? WHERE visit_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, visitId);
            return stmt.executeUpdate() > 0;
        }
    }

    private PropertyVisit mapVisitFromMap(Map<String, Object> row) {
        PropertyVisit pv = new PropertyVisit();
        pv.setVisitId(JsonUtil.getInt(row, "visit_id"));
        pv.setPropertyId(JsonUtil.getInt(row, "property_id"));
        Map<String, Object> prop = JsonUtil.getMap(row, "properties");
        if (prop != null) {
            pv.setPropertyTitle(JsonUtil.getString(prop, "title"));
            pv.setPropertyCity(JsonUtil.getString(prop, "city"));
        }
        pv.setUserId(JsonUtil.getInt(row, "user_id"));
        Map<String, Object> usr = JsonUtil.getMap(row, "users");
        if (usr != null) {
            pv.setUserName(JsonUtil.getString(usr, "full_name"));
            pv.setUserEmail(JsonUtil.getString(usr, "email"));
        }
        pv.setVisitDate(JsonUtil.getDate(row, "visit_date"));
        pv.setVisitTime(JsonUtil.getTime(row, "visit_time"));
        pv.setContactNumber(JsonUtil.getString(row, "contact_number"));
        pv.setNotes(JsonUtil.getString(row, "notes"));
        pv.setAppointmentStatus(JsonUtil.getString(row, "appointment_status"));
        pv.setCreatedAt(JsonUtil.getTimestamp(row, "created_at"));
        return pv;
    }

    private PropertyVisit mapVisit(ResultSet rs) throws SQLException {
        PropertyVisit pv = new PropertyVisit();
        pv.setVisitId(rs.getInt("visit_id"));
        pv.setPropertyId(rs.getInt("property_id"));
        pv.setPropertyTitle(rs.getString("property_title"));
        pv.setPropertyCity(rs.getString("property_city"));
        pv.setUserId(rs.getInt("user_id"));
        pv.setUserName(rs.getString("user_name"));
        pv.setUserEmail(rs.getString("user_email"));
        pv.setVisitDate(rs.getDate("visit_date"));
        pv.setVisitTime(rs.getTime("visit_time"));
        pv.setContactNumber(rs.getString("contact_number"));
        pv.setNotes(rs.getString("notes"));
        pv.setAppointmentStatus(rs.getString("appointment_status"));
        pv.setCreatedAt(rs.getTimestamp("created_at"));
        return pv;
    }
}
