package com.realestate.dao;

import java.sql.*;
import java.util.*;

import com.realestate.database.SupabaseClient;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.exceptions.PropertyNotFoundException;
import com.realestate.exceptions.UnauthorizedAccessException;
import com.realestate.model.property.*;
import com.realestate.util.JsonUtil;

/**
 * Data Access Object for Properties with Table-per-Type (TPT) polymorphic persistence.
 * Supports Supabase PostgREST and MySQL JDBC.
 */
public class PropertyDAO extends BaseDAO {

    private static final String SUPABASE_PROPERTY_SELECT =
        "select=*,property_categories(category_code,category_name)," +
        "users!properties_owner_id_fkey(full_name,email,mobile_number,is_verified)," +
        "residential_details(*),commercial_details(*),land_details(*)," +
        "farmhouse_details(*),plot_details(*),building_details(*)," +
        "property_images(image_url,is_primary),property_amenities(amenity_name)";

    /**
     * Factory method creating the appropriate polymorphic Property subclass.
     */
    public static Property createPropertyInstance(String categoryCode) {
        if (categoryCode == null) return new ResidentialProperty();
        switch (categoryCode.toUpperCase()) {
            case "COMMERCIAL": return new CommercialProperty();
            case "AGRICULTURAL": return new AgriculturalLand();
            case "FARMHOUSE": return new FarmHouse();
            case "PLOT": return new Plot();
            case "BUILDING": return new Building();
            case "RESIDENTIAL":
            default:
                return new ResidentialProperty();
        }
    }

    /**
     * Inserts a property with its category-specific details.
     */
    public Property createProperty(Property property) throws SQLException, InvalidPropertyDataException {
        property.validateCategorySpecifics();

        if (useSupabase()) {
            return createPropertySupabase(property);
        }

        String baseSql = "INSERT INTO properties (owner_id, category_id, title, description, purpose, price, " +
                         "property_area, area_unit, state, city, locality, address, pincode, approval_status, listing_status, is_featured) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'AVAILABLE', ?)";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            int propertyId = 0;
            try (PreparedStatement stmt = conn.prepareStatement(baseSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, property.getOwnerId());
                stmt.setInt(2, property.getCategoryId());
                stmt.setString(3, property.getTitle());
                stmt.setString(4, property.getDescription());
                stmt.setString(5, property.getPurpose());
                stmt.setDouble(6, property.getPrice());
                stmt.setDouble(7, property.getPropertyArea());
                stmt.setString(8, property.getAreaUnit() != null ? property.getAreaUnit() : "Sq. Ft.");
                stmt.setString(9, property.getState());
                stmt.setString(10, property.getCity());
                stmt.setString(11, property.getLocality());
                stmt.setString(12, property.getAddress());
                stmt.setString(13, property.getPincode());
                stmt.setBoolean(14, property.isFeatured());

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        propertyId = rs.getInt(1);
                        property.setPropertyId(propertyId);
                    }
                }
            }

            insertCategoryDetails(conn, property);

            if (property.getImageUrls() != null && !property.getImageUrls().isEmpty()) {
                String imgSql = "INSERT INTO property_images (property_id, image_url, is_primary) VALUES (?, ?, ?)";
                try (PreparedStatement imgStmt = conn.prepareStatement(imgSql)) {
                    boolean isFirst = true;
                    for (String url : property.getImageUrls()) {
                        imgStmt.setInt(1, propertyId);
                        imgStmt.setString(2, url);
                        imgStmt.setBoolean(3, isFirst);
                        imgStmt.addBatch();
                        isFirst = false;
                    }
                    imgStmt.executeBatch();
                }
            }

            if (property.getAmenities() != null && !property.getAmenities().isEmpty()) {
                String amenSql = "INSERT INTO property_amenities (property_id, amenity_name) VALUES (?, ?)";
                try (PreparedStatement amenStmt = conn.prepareStatement(amenSql)) {
                    for (String amen : property.getAmenities()) {
                        amenStmt.setInt(1, propertyId);
                        amenStmt.setString(2, amen);
                        amenStmt.addBatch();
                    }
                    amenStmt.executeBatch();
                }
            }

            conn.commit();
            return property;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignored */ }
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private Property createPropertySupabase(Property property) throws SQLException {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("owner_id", property.getOwnerId());
        base.put("category_id", property.getCategoryId());
        base.put("title", property.getTitle());
        base.put("description", property.getDescription() != null ? property.getDescription() : property.getTitle());
        base.put("purpose", property.getPurpose());
        base.put("price", property.getPrice());
        base.put("property_area", property.getPropertyArea());
        base.put("area_unit", property.getAreaUnit() != null ? property.getAreaUnit() : "Sq. Ft.");
        base.put("state", property.getState() != null ? property.getState() : "Karnataka");
        base.put("city", property.getCity());
        base.put("locality", property.getLocality() != null ? property.getLocality() : property.getCity());
        base.put("address", property.getAddress() != null ? property.getAddress() : property.getCity());
        base.put("pincode", property.getPincode());
        base.put("approval_status", "PENDING");
        base.put("listing_status", "AVAILABLE");
        base.put("is_featured", property.isFeatured());
        if (property.getImageUrls() != null && !property.getImageUrls().isEmpty()) {
            base.put("primary_image_url", property.getImageUrls().get(0));
        }

        Map<String, Object> created = supabase().post("properties", base);
        int pid = JsonUtil.getInt(created, "property_id");
        property.setPropertyId(pid);

        try {
            if (property instanceof ResidentialProperty) {
                ResidentialProperty rp = (ResidentialProperty) property;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("residential_type", rp.getResidentialType() != null ? rp.getResidentialType() : "APARTMENT");
                d.put("bedrooms", rp.getBedrooms());
                d.put("bathrooms", rp.getBathrooms());
                d.put("built_up_area", rp.getBuiltUpArea() > 0 ? rp.getBuiltUpArea() : rp.getPropertyArea());
                d.put("carpet_area", rp.getCarpetArea() > 0 ? rp.getCarpetArea() : rp.getPropertyArea());
                d.put("parking_slots", rp.getParkingSlots());
                d.put("furnishing_status", rp.getFurnishingStatus() != null ? rp.getFurnishingStatus() : "SEMI_FURNISHED");
                d.put("floor_number", rp.getFloorNumber());
                d.put("total_floors", Math.max(1, rp.getTotalFloors()));
                d.put("property_age_years", rp.getPropertyAgeYears());
                supabase().post("residential_details", d);
            } else if (property instanceof CommercialProperty) {
                CommercialProperty cp = (CommercialProperty) property;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("commercial_type", cp.getCommercialType() != null ? cp.getCommercialType() : "OFFICE_SPACE");
                d.put("carpet_area", cp.getCarpetArea() > 0 ? cp.getCarpetArea() : cp.getPropertyArea());
                d.put("number_of_floors", Math.max(1, cp.getNumberOfFloors()));
                d.put("parking_spaces", cp.getParkingSpaces());
                d.put("building_type", cp.getBuildingType());
                d.put("commercial_usage", cp.getCommercialUsage());
                d.put("power_backup", cp.isPowerBackup());
                supabase().post("commercial_details", d);
            } else if (property instanceof AgriculturalLand) {
                AgriculturalLand al = (AgriculturalLand) property;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("land_type", al.getLandType() != null ? al.getLandType() : "FARMLAND");
                d.put("total_land_area", al.getTotalLandArea() > 0 ? al.getTotalLandArea() : al.getPropertyArea());
                d.put("land_unit", al.getLandUnit() != null ? al.getLandUnit().toUpperCase() : "ACRES");
                d.put("soil_type", al.getSoilType());
                d.put("water_availability", al.getWaterAvailability());
                d.put("has_borewell", al.isHasBorewell());
                d.put("road_connectivity", al.getRoadConnectivity());
                d.put("has_electricity", al.isHasElectricity());
                d.put("land_document_type", al.getLandDocumentType());
                d.put("survey_number", al.getSurveyNumber());
                supabase().post("land_details", d);
            } else if (property instanceof FarmHouse) {
                FarmHouse fh = (FarmHouse) property;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("land_area", fh.getLandArea() > 0 ? fh.getLandArea() : fh.getPropertyArea());
                d.put("built_up_area", fh.getBuiltUpArea() > 0 ? fh.getBuiltUpArea() : fh.getPropertyArea());
                d.put("bedrooms", Math.max(1, fh.getBedrooms()));
                d.put("bathrooms", Math.max(1, fh.getBathrooms()));
                d.put("has_garden", fh.isHasGarden());
                d.put("has_swimming_pool", fh.isHasSwimmingPool());
                d.put("parking_capacity", fh.getParkingCapacity());
                d.put("water_facilities", fh.getWaterFacilities());
                supabase().post("farmhouse_details", d);
            } else if (property instanceof Plot) {
                Plot pl = (Plot) property;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("plot_type", pl.getPlotType() != null ? pl.getPlotType() : "RESIDENTIAL_PLOT");
                d.put("plot_dimensions", pl.getPlotDimensions());
                d.put("total_area", pl.getTotalArea() > 0 ? pl.getTotalArea() : pl.getPropertyArea());
                d.put("facing_direction", pl.getFacingDirection() != null ? pl.getFacingDirection() : "EAST");
                d.put("road_width_feet", pl.getRoadWidthFeet() > 0 ? pl.getRoadWidthFeet() : 30);
                d.put("is_approved_layout", pl.isApprovedLayout());
                d.put("approval_authority", pl.getApprovalAuthority());
                supabase().post("plot_details", d);
            } else if (property instanceof Building) {
                Building b = (Building) property;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("building_type", b.getBuildingType() != null ? b.getBuildingType() : "APARTMENT_BUILDING");
                d.put("number_of_floors", Math.max(1, b.getNumberOfFloors()));
                d.put("total_units", Math.max(1, b.getTotalUnits()));
                d.put("construction_status", b.getConstructionStatus() != null ? b.getConstructionStatus() : "READY_TO_MOVE");
                d.put("builder_name", b.getBuilderName());
                if (b.getProjectCompletionDate() != null) d.put("project_completion_date", b.getProjectCompletionDate().toString());
                d.put("rera_number", b.getReraNumber());
                supabase().post("building_details", d);
            }

            if (property.getImageUrls() != null && !property.getImageUrls().isEmpty()) {
                List<Map<String, Object>> imgs = new ArrayList<>();
                boolean first = true;
                for (String url : property.getImageUrls()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("property_id", pid);
                    row.put("image_url", url);
                    row.put("is_primary", first);
                    imgs.add(row);
                    first = false;
                }
                supabase().postBatch("property_images", imgs);
            }

            if (property.getAmenities() != null && !property.getAmenities().isEmpty()) {
                List<Map<String, Object>> amens = new ArrayList<>();
                for (String a : property.getAmenities()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("property_id", pid);
                    row.put("amenity_name", a);
                    amens.add(row);
                }
                supabase().postBatch("property_amenities", amens);
            }
        } catch (SQLException e) {
            // Compensating rollback: clean up base row if child insert fails
            try { supabase().delete("properties?property_id=eq." + pid); } catch (SQLException ignored) {}
            throw e;
        }
        return property;
    }

    /**
     * Retrieves a complete polymorphic property by ID including images and amenities.
     */
    public Property getPropertyById(int propertyId) throws SQLException, PropertyNotFoundException {
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "properties?" + SUPABASE_PROPERTY_SELECT + "&property_id=eq." + propertyId + "&limit=1"
            );
            if (!rows.isEmpty()) {
                return mapPropertyFromSupabase(rows.get(0));
            }
            throw new PropertyNotFoundException(propertyId);
        }

        String sql = "SELECT p.*, c.category_name, c.category_code, u.full_name as owner_name, u.email as owner_email, " +
                     "u.mobile_number as owner_phone, u.is_verified as owner_verified " +
                     "FROM properties p " +
                     "JOIN property_categories c ON p.category_id = c.category_id " +
                     "JOIN users u ON p.owner_id = u.user_id " +
                     "WHERE p.property_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, propertyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String catCode = rs.getString("category_code");
                    Property property = createPropertyInstance(catCode);
                    mapBaseProperty(rs, property);
                    loadCategoryDetails(conn, property);
                    loadImagesAndAmenities(conn, property);
                    return property;
                }
            }
        }
        throw new PropertyNotFoundException(propertyId);
    }

    /**
     * Executes safe, dynamic parameterized search and filtering across properties.
     */
    public List<Property> searchProperties(Map<String, String> filters, String sortBy) throws SQLException {
        if (useSupabase()) {
            StringBuilder q = new StringBuilder("properties?");
            q.append(SUPABASE_PROPERTY_SELECT);
            q.append("&approval_status=eq.APPROVED&listing_status=eq.AVAILABLE");

            String catFilter = null;
            if (filters != null) {
                String city = filters.get("city");
                if (city != null && !city.trim().isEmpty() && !"ALL".equalsIgnoreCase(city)) {
                    q.append("&city=ilike.").append(SupabaseClient.encode(city.trim()));
                }
                String purpose = filters.get("purpose");
                if (purpose != null && !purpose.trim().isEmpty() && !"ALL".equalsIgnoreCase(purpose)) {
                    q.append("&purpose=eq.").append(SupabaseClient.encode(purpose.trim().toUpperCase()));
                }
                String minPrice = filters.get("minPrice");
                if (minPrice != null && !minPrice.trim().isEmpty()) {
                    try {
                        double min = Double.parseDouble(minPrice.trim());
                        if (min > 0) q.append("&price=gte.").append(min);
                    } catch (NumberFormatException ignored) {}
                }
                String maxPrice = filters.get("maxPrice");
                if (maxPrice != null && !maxPrice.trim().isEmpty()) {
                    try {
                        double max = Double.parseDouble(maxPrice.trim());
                        if (max > 0) q.append("&price=lte.").append(max);
                    } catch (NumberFormatException ignored) {}
                }
                String keyword = filters.get("keyword");
                if (keyword != null && !keyword.trim().isEmpty()) {
                    String kw = "*" + keyword.trim() + "*";
                    String orExpr = "(title.ilike." + kw + ",description.ilike." + kw + ",locality.ilike." + kw + ",city.ilike." + kw + ")";
                    q.append("&or=").append(SupabaseClient.encode(orExpr));
                }
                catFilter = filters.get("category");
                if (catFilter != null && !catFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(catFilter)) {
                    Integer catId = resolveCategoryId(catFilter.trim());
                    if (catId != null) {
                        q.append("&category_id=eq.").append(catId);
                    }
                }
            }

            if ("price_asc".equalsIgnoreCase(sortBy)) {
                q.append("&order=price.asc");
            } else if ("price_desc".equalsIgnoreCase(sortBy)) {
                q.append("&order=price.desc");
            } else if ("area_desc".equalsIgnoreCase(sortBy)) {
                q.append("&order=property_area.desc");
            } else {
                q.append("&order=created_at.desc");
            }

            List<Map<String, Object>> rows = supabase().get(q.toString());
            List<Property> list = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                list.add(mapPropertyFromSupabase(row));
            }
            return list;
        }

        List<Property> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT p.*, c.category_name, c.category_code, u.full_name as owner_name, u.email as owner_email, " +
            "u.mobile_number as owner_phone, u.is_verified as owner_verified, " +
            "(SELECT image_url FROM property_images pi WHERE pi.property_id = p.property_id AND pi.is_primary = TRUE LIMIT 1) as primary_img " +
            "FROM properties p " +
            "JOIN property_categories c ON p.category_id = c.category_id " +
            "JOIN users u ON p.owner_id = u.user_id " +
            "WHERE p.approval_status = 'APPROVED' AND p.listing_status = 'AVAILABLE' "
        );

        List<Object> params = new ArrayList<>();

        if (filters != null) {
            String city = filters.get("city");
            if (city != null && !city.trim().isEmpty() && !"ALL".equalsIgnoreCase(city)) {
                sql.append(" AND LOWER(p.city) = LOWER(?)");
                params.add(city.trim());
            }

            String category = filters.get("category");
            if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
                sql.append(" AND (LOWER(c.category_code) = LOWER(?) OR c.category_id = ?)");
                params.add(category.trim());
                int catId = 0;
                try { catId = Integer.parseInt(category.trim()); } catch (Exception ignored) {}
                params.add(catId);
            }

            String purpose = filters.get("purpose");
            if (purpose != null && !purpose.trim().isEmpty() && !"ALL".equalsIgnoreCase(purpose)) {
                sql.append(" AND p.purpose = ?");
                params.add(purpose.trim().toUpperCase());
            }

            String minPrice = filters.get("minPrice");
            if (minPrice != null && !minPrice.trim().isEmpty()) {
                try {
                    double min = Double.parseDouble(minPrice);
                    if (min > 0) {
                        sql.append(" AND p.price >= ?");
                        params.add(min);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String maxPrice = filters.get("maxPrice");
            if (maxPrice != null && !maxPrice.trim().isEmpty()) {
                try {
                    double max = Double.parseDouble(maxPrice);
                    if (max > 0) {
                        sql.append(" AND p.price <= ?");
                        params.add(max);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String keyword = filters.get("keyword");
            if (keyword != null && !keyword.trim().isEmpty()) {
                sql.append(" AND (LOWER(p.title) LIKE ? OR LOWER(p.description) LIKE ? OR LOWER(p.locality) LIKE ?)");
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
        }

        if ("price_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY p.price ASC");
        } else if ("price_desc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY p.price DESC");
        } else if ("area_desc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY p.property_area DESC");
        } else {
            sql.append(" ORDER BY p.created_at DESC");
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String catCode = rs.getString("category_code");
                    Property p = createPropertyInstance(catCode);
                    mapBaseProperty(rs, p);
                    p.setPrimaryImageUrl(rs.getString("primary_img"));
                    loadCategoryDetails(conn, p);
                    list.add(p);
                }
            }
        }
        return list;
    }

    /**
     * Gets featured properties for Homepage.
     */
    public List<Property> getFeaturedProperties(int limit) throws SQLException {
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "properties?" + SUPABASE_PROPERTY_SELECT + "&approval_status=eq.APPROVED&listing_status=eq.AVAILABLE&is_featured=eq.true&order=created_at.desc&limit=" + limit
            );
            List<Property> list = new ArrayList<>();
            for (Map<String, Object> row : rows) list.add(mapPropertyFromSupabase(row));
            return list;
        }

        Map<String, String> filters = new HashMap<>();
        List<Property> all = searchProperties(filters, "newest");
        List<Property> featured = new ArrayList<>();
        for (Property p : all) {
            if (p.isFeatured()) {
                featured.add(p);
                if (featured.size() >= limit) break;
            }
        }
        if (featured.isEmpty() && !all.isEmpty()) {
            return all.subList(0, Math.min(limit, all.size()));
        }
        return featured;
    }

    /**
     * Gets all pending properties awaiting Admin review.
     */
    public List<Property> getPendingProperties() throws SQLException {
        List<Property> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "properties?" + SUPABASE_PROPERTY_SELECT + "&approval_status=eq.PENDING&order=created_at.asc"
            );
            for (Map<String, Object> row : rows) list.add(mapPropertyFromSupabase(row));
            return list;
        }

        String sql = "SELECT p.*, c.category_name, c.category_code, u.full_name as owner_name, u.email as owner_email, " +
                     "u.mobile_number as owner_phone, u.is_verified as owner_verified " +
                     "FROM properties p " +
                     "JOIN property_categories c ON p.category_id = c.category_id " +
                     "JOIN users u ON p.owner_id = u.user_id " +
                     "WHERE p.approval_status = 'PENDING' ORDER BY p.created_at ASC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String catCode = rs.getString("category_code");
                Property p = createPropertyInstance(catCode);
                mapBaseProperty(rs, p);
                loadImagesAndAmenities(conn, p);
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Gets properties belonging to a specific owner.
     */
    public List<Property> getPropertiesByOwner(int ownerId) throws SQLException {
        List<Property> list = new ArrayList<>();
        if (useSupabase()) {
            List<Map<String, Object>> rows = supabase().get(
                "properties?" + SUPABASE_PROPERTY_SELECT + "&owner_id=eq." + ownerId + "&order=created_at.desc"
            );
            for (Map<String, Object> row : rows) list.add(mapPropertyFromSupabase(row));
            return list;
        }

        String sql = "SELECT p.*, c.category_name, c.category_code, u.full_name as owner_name, u.email as owner_email, " +
                     "u.mobile_number as owner_phone, u.is_verified as owner_verified " +
                     "FROM properties p " +
                     "JOIN property_categories c ON p.category_id = c.category_id " +
                     "JOIN users u ON p.owner_id = u.user_id " +
                     "WHERE p.owner_id = ? ORDER BY p.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String catCode = rs.getString("category_code");
                    Property p = createPropertyInstance(catCode);
                    mapBaseProperty(rs, p);
                    loadImagesAndAmenities(conn, p);
                    list.add(p);
                }
            }
        }
        return list;
    }

    /**
     * Admin approval or rejection of a property listing.
     */
    public boolean updateApprovalStatus(int propertyId, String status, String reason) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("approval_status", status);
            patch.put("rejection_reason", reason);
            return !supabase().patch("properties?property_id=eq." + propertyId, patch).isEmpty();
        }

        String sql = "UPDATE properties SET approval_status = ?, rejection_reason = ? WHERE property_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, reason);
            stmt.setInt(3, propertyId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates listing status (AVAILABLE, UNDER_OFFER, SOLD, RENTED, INACTIVE).
     */
    public boolean updateListingStatus(int propertyId, String status) throws SQLException {
        if (useSupabase()) {
            Map<String, Object> patch = new HashMap<>();
            patch.put("listing_status", status);
            return !supabase().patch("properties?property_id=eq." + propertyId, patch).isEmpty();
        }

        String sql = "UPDATE properties SET listing_status = ? WHERE property_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, propertyId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a property with IDOR authorization guard.
     */
    public boolean deleteProperty(int propertyId, int currentUserId, boolean isAdmin)
            throws SQLException, UnauthorizedAccessException, PropertyNotFoundException {
        Property p = getPropertyById(propertyId);
        if (!isAdmin && p.getOwnerId() != currentUserId) {
            throw new UnauthorizedAccessException("You are not authorized to delete this property listing.");
        }

        if (useSupabase()) {
            return supabase().delete("properties?property_id=eq." + propertyId);
        }

        String sql = "DELETE FROM properties WHERE property_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, propertyId);
            return stmt.executeUpdate() > 0;
        }
    }

    private static Integer resolveCategoryId(String category) {
        try { return Integer.parseInt(category); } catch (NumberFormatException ignored) {}
        switch (category.toUpperCase()) {
            case "RESIDENTIAL": return 1;
            case "COMMERCIAL": return 2;
            case "AGRICULTURAL": return 3;
            case "FARMHOUSE": return 4;
            case "PLOT": return 5;
            case "BUILDING": return 6;
            default: return null;
        }
    }

    private Property mapPropertyFromSupabase(Map<String, Object> row) {
        Map<String, Object> cat = JsonUtil.getMap(row, "property_categories");
        String catCode = cat != null ? JsonUtil.getString(cat, "category_code") : "RESIDENTIAL";
        String catName = cat != null ? JsonUtil.getString(cat, "category_name") : "Residential Properties";

        Property p = createPropertyInstance(catCode);
        p.setPropertyId(JsonUtil.getInt(row, "property_id"));
        p.setOwnerId(JsonUtil.getInt(row, "owner_id"));
        p.setCategoryId(JsonUtil.getInt(row, "category_id"));
        p.setCategoryCode(catCode);
        p.setCategoryName(catName);
        p.setTitle(JsonUtil.getString(row, "title"));
        p.setDescription(JsonUtil.getString(row, "description"));
        p.setPurpose(JsonUtil.getString(row, "purpose"));
        p.setPrice(JsonUtil.getDouble(row, "price"));
        p.setPropertyArea(JsonUtil.getDouble(row, "property_area"));
        p.setAreaUnit(JsonUtil.getString(row, "area_unit"));
        p.setState(JsonUtil.getString(row, "state"));
        p.setCity(JsonUtil.getString(row, "city"));
        p.setLocality(JsonUtil.getString(row, "locality"));
        p.setAddress(JsonUtil.getString(row, "address"));
        p.setPincode(JsonUtil.getString(row, "pincode"));
        p.setApprovalStatus(JsonUtil.getString(row, "approval_status"));
        p.setListingStatus(JsonUtil.getString(row, "listing_status"));
        p.setFeatured(JsonUtil.getBoolean(row, "is_featured"));
        p.setRejectionReason(JsonUtil.getString(row, "rejection_reason"));
        p.setPrimaryImageUrl(JsonUtil.getString(row, "primary_image_url"));
        p.setCreatedAt(JsonUtil.getTimestamp(row, "created_at"));
        p.setUpdatedAt(JsonUtil.getTimestamp(row, "updated_at"));

        Map<String, Object> owner = JsonUtil.getMap(row, "users");
        if (owner != null) {
            p.setOwnerName(JsonUtil.getString(owner, "full_name"));
            p.setOwnerEmail(JsonUtil.getString(owner, "email"));
            p.setOwnerPhone(JsonUtil.getString(owner, "mobile_number"));
            p.setOwnerVerified(JsonUtil.getBoolean(owner, "is_verified"));
        }

        // Images
        List<Map<String, Object>> imgRows = JsonUtil.getList(row, "property_images");
        List<String> imgUrls = new ArrayList<>();
        for (Map<String, Object> img : imgRows) {
            String url = JsonUtil.getString(img, "image_url");
            if (url != null && !url.isEmpty()) {
                imgUrls.add(url);
                if (JsonUtil.getBoolean(img, "is_primary") && (p.getPrimaryImageUrl() == null || p.getPrimaryImageUrl().isEmpty())) {
                    p.setPrimaryImageUrl(url);
                }
            }
        }
        if ((p.getPrimaryImageUrl() == null || p.getPrimaryImageUrl().isEmpty()) && !imgUrls.isEmpty()) {
            p.setPrimaryImageUrl(imgUrls.get(0));
        }
        if (imgUrls.isEmpty() && p.getPrimaryImageUrl() != null && !p.getPrimaryImageUrl().isEmpty()) {
            imgUrls.add(p.getPrimaryImageUrl());
        }
        p.setImageUrls(imgUrls);

        // Amenities
        List<Map<String, Object>> amenRows = JsonUtil.getList(row, "property_amenities");
        List<String> amenities = new ArrayList<>();
        for (Map<String, Object> a : amenRows) {
            String name = JsonUtil.getString(a, "amenity_name");
            if (name != null) amenities.add(name);
        }
        p.setAmenities(amenities);

        // Sub-table polymorphic details
        if (p instanceof ResidentialProperty) {
            Map<String, Object> d = JsonUtil.getMap(row, "residential_details");
            if (d != null) {
                ResidentialProperty rp = (ResidentialProperty) p;
                rp.setResidentialType(JsonUtil.getString(d, "residential_type"));
                rp.setBedrooms(JsonUtil.getInt(d, "bedrooms"));
                rp.setBathrooms(JsonUtil.getInt(d, "bathrooms"));
                rp.setBuiltUpArea(JsonUtil.getDouble(d, "built_up_area"));
                rp.setCarpetArea(JsonUtil.getDouble(d, "carpet_area"));
                rp.setParkingSlots(JsonUtil.getInt(d, "parking_slots"));
                rp.setFurnishingStatus(JsonUtil.getString(d, "furnishing_status"));
                rp.setFloorNumber(JsonUtil.getInt(d, "floor_number"));
                rp.setTotalFloors(JsonUtil.getInt(d, "total_floors"));
                rp.setPropertyAgeYears(JsonUtil.getInt(d, "property_age_years"));
            }
        } else if (p instanceof CommercialProperty) {
            Map<String, Object> d = JsonUtil.getMap(row, "commercial_details");
            if (d != null) {
                CommercialProperty cp = (CommercialProperty) p;
                cp.setCommercialType(JsonUtil.getString(d, "commercial_type"));
                cp.setCarpetArea(JsonUtil.getDouble(d, "carpet_area"));
                cp.setNumberOfFloors(JsonUtil.getInt(d, "number_of_floors"));
                cp.setParkingSpaces(JsonUtil.getInt(d, "parking_spaces"));
                cp.setBuildingType(JsonUtil.getString(d, "building_type"));
                cp.setCommercialUsage(JsonUtil.getString(d, "commercial_usage"));
                cp.setPowerBackup(JsonUtil.getBoolean(d, "power_backup"));
            }
        } else if (p instanceof AgriculturalLand) {
            Map<String, Object> d = JsonUtil.getMap(row, "land_details");
            if (d != null) {
                AgriculturalLand al = (AgriculturalLand) p;
                al.setLandType(JsonUtil.getString(d, "land_type"));
                al.setTotalLandArea(JsonUtil.getDouble(d, "total_land_area"));
                al.setLandUnit(JsonUtil.getString(d, "land_unit"));
                al.setSoilType(JsonUtil.getString(d, "soil_type"));
                al.setWaterAvailability(JsonUtil.getString(d, "water_availability"));
                al.setHasBorewell(JsonUtil.getBoolean(d, "has_borewell"));
                al.setRoadConnectivity(JsonUtil.getString(d, "road_connectivity"));
                al.setHasElectricity(JsonUtil.getBoolean(d, "has_electricity"));
                al.setLandDocumentType(JsonUtil.getString(d, "land_document_type"));
                al.setSurveyNumber(JsonUtil.getString(d, "survey_number"));
            }
        } else if (p instanceof FarmHouse) {
            Map<String, Object> d = JsonUtil.getMap(row, "farmhouse_details");
            if (d != null) {
                FarmHouse fh = (FarmHouse) p;
                fh.setLandArea(JsonUtil.getDouble(d, "land_area"));
                fh.setBuiltUpArea(JsonUtil.getDouble(d, "built_up_area"));
                fh.setBedrooms(JsonUtil.getInt(d, "bedrooms"));
                fh.setBathrooms(JsonUtil.getInt(d, "bathrooms"));
                fh.setHasGarden(JsonUtil.getBoolean(d, "has_garden"));
                fh.setHasSwimmingPool(JsonUtil.getBoolean(d, "has_swimming_pool"));
                fh.setParkingCapacity(JsonUtil.getInt(d, "parking_capacity"));
                fh.setWaterFacilities(JsonUtil.getString(d, "water_facilities"));
            }
        } else if (p instanceof Plot) {
            Map<String, Object> d = JsonUtil.getMap(row, "plot_details");
            if (d != null) {
                Plot pl = (Plot) p;
                pl.setPlotType(JsonUtil.getString(d, "plot_type"));
                pl.setPlotDimensions(JsonUtil.getString(d, "plot_dimensions"));
                pl.setTotalArea(JsonUtil.getDouble(d, "total_area"));
                pl.setFacingDirection(JsonUtil.getString(d, "facing_direction"));
                pl.setRoadWidthFeet(JsonUtil.getInt(d, "road_width_feet"));
                pl.setApprovedLayout(JsonUtil.getBoolean(d, "is_approved_layout"));
                pl.setApprovalAuthority(JsonUtil.getString(d, "approval_authority"));
            }
        } else if (p instanceof Building) {
            Map<String, Object> d = JsonUtil.getMap(row, "building_details");
            if (d != null) {
                Building b = (Building) p;
                b.setBuildingType(JsonUtil.getString(d, "building_type"));
                b.setNumberOfFloors(JsonUtil.getInt(d, "number_of_floors"));
                b.setTotalUnits(JsonUtil.getInt(d, "total_units"));
                b.setConstructionStatus(JsonUtil.getString(d, "construction_status"));
                b.setBuilderName(JsonUtil.getString(d, "builder_name"));
                b.setProjectCompletionDate(JsonUtil.getDate(d, "project_completion_date"));
                b.setReraNumber(JsonUtil.getString(d, "rera_number"));
            }
        }

        return p;
    }

    private void insertCategoryDetails(Connection conn, Property p) throws SQLException {
        if (p instanceof ResidentialProperty) {
            ResidentialProperty rp = (ResidentialProperty) p;
            String sql = "INSERT INTO residential_details (property_id, residential_type, bedrooms, bathrooms, built_up_area, carpet_area, parking_slots, furnishing_status, floor_number, total_floors, property_age_years) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setInt(1, rp.getPropertyId());
                st.setString(2, rp.getResidentialType() != null ? rp.getResidentialType() : "APARTMENT");
                st.setInt(3, rp.getBedrooms());
                st.setInt(4, rp.getBathrooms());
                st.setDouble(5, rp.getBuiltUpArea());
                st.setDouble(6, rp.getCarpetArea());
                st.setInt(7, rp.getParkingSlots());
                st.setString(8, rp.getFurnishingStatus() != null ? rp.getFurnishingStatus() : "SEMI_FURNISHED");
                st.setInt(9, rp.getFloorNumber());
                st.setInt(10, rp.getTotalFloors());
                st.setInt(11, rp.getPropertyAgeYears());
                st.executeUpdate();
            }
        } else if (p instanceof CommercialProperty) {
            CommercialProperty cp = (CommercialProperty) p;
            String sql = "INSERT INTO commercial_details (property_id, commercial_type, carpet_area, number_of_floors, parking_spaces, building_type, commercial_usage, power_backup) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setInt(1, cp.getPropertyId());
                st.setString(2, cp.getCommercialType() != null ? cp.getCommercialType() : "OFFICE_SPACE");
                st.setDouble(3, cp.getCarpetArea() > 0 ? cp.getCarpetArea() : cp.getPropertyArea());
                st.setInt(4, cp.getNumberOfFloors());
                st.setInt(5, cp.getParkingSpaces());
                st.setString(6, cp.getBuildingType());
                st.setString(7, cp.getCommercialUsage());
                st.setBoolean(8, cp.isPowerBackup());
                st.executeUpdate();
            }
        } else if (p instanceof AgriculturalLand) {
            AgriculturalLand al = (AgriculturalLand) p;
            String sql = "INSERT INTO land_details (property_id, land_type, total_land_area, land_unit, soil_type, water_availability, has_borewell, road_connectivity, has_electricity, land_document_type, survey_number) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setInt(1, al.getPropertyId());
                st.setString(2, al.getLandType() != null ? al.getLandType() : "FARMLAND");
                st.setDouble(3, al.getTotalLandArea() > 0 ? al.getTotalLandArea() : al.getPropertyArea());
                st.setString(4, al.getLandUnit() != null ? al.getLandUnit() : "ACRES");
                st.setString(5, al.getSoilType());
                st.setString(6, al.getWaterAvailability());
                st.setBoolean(7, al.isHasBorewell());
                st.setString(8, al.getRoadConnectivity());
                st.setBoolean(9, al.isHasElectricity());
                st.setString(10, al.getLandDocumentType());
                st.setString(11, al.getSurveyNumber());
                st.executeUpdate();
            }
        } else if (p instanceof FarmHouse) {
            FarmHouse fh = (FarmHouse) p;
            String sql = "INSERT INTO farmhouse_details (property_id, land_area, built_up_area, bedrooms, bathrooms, has_garden, has_swimming_pool, parking_capacity, water_facilities) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setInt(1, fh.getPropertyId());
                st.setDouble(2, fh.getLandArea() > 0 ? fh.getLandArea() : fh.getPropertyArea());
                st.setDouble(3, fh.getBuiltUpArea());
                st.setInt(4, fh.getBedrooms());
                st.setInt(5, fh.getBathrooms());
                st.setBoolean(6, fh.isHasGarden());
                st.setBoolean(7, fh.isHasSwimmingPool());
                st.setInt(8, fh.getParkingCapacity());
                st.setString(9, fh.getWaterFacilities());
                st.executeUpdate();
            }
        } else if (p instanceof Plot) {
            Plot pl = (Plot) p;
            String sql = "INSERT INTO plot_details (property_id, plot_type, plot_dimensions, total_area, facing_direction, road_width_feet, is_approved_layout, approval_authority) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setInt(1, pl.getPropertyId());
                st.setString(2, pl.getPlotType() != null ? pl.getPlotType() : "RESIDENTIAL_PLOT");
                st.setString(3, pl.getPlotDimensions());
                st.setDouble(4, pl.getTotalArea() > 0 ? pl.getTotalArea() : pl.getPropertyArea());
                st.setString(5, pl.getFacingDirection());
                st.setInt(6, pl.getRoadWidthFeet());
                st.setBoolean(7, pl.isApprovedLayout());
                st.setString(8, pl.getApprovalAuthority());
                st.executeUpdate();
            }
        } else if (p instanceof Building) {
            Building b = (Building) p;
            String sql = "INSERT INTO building_details (property_id, building_type, number_of_floors, total_units, construction_status, builder_name, project_completion_date, rera_number) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setInt(1, b.getPropertyId());
                st.setString(2, b.getBuildingType() != null ? b.getBuildingType() : "APARTMENT_BUILDING");
                st.setInt(3, b.getNumberOfFloors());
                st.setInt(4, b.getTotalUnits());
                st.setString(5, b.getConstructionStatus() != null ? b.getConstructionStatus() : "READY_TO_MOVE");
                st.setString(6, b.getBuilderName());
                st.setDate(7, b.getProjectCompletionDate());
                st.setString(8, b.getReraNumber());
                st.executeUpdate();
            }
        }
    }

    private void mapBaseProperty(ResultSet rs, Property p) throws SQLException {
        p.setPropertyId(rs.getInt("property_id"));
        p.setOwnerId(rs.getInt("owner_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setCategoryCode(rs.getString("category_code"));
        p.setTitle(rs.getString("title"));
        p.setDescription(rs.getString("description"));
        p.setPurpose(rs.getString("purpose"));
        p.setPrice(rs.getDouble("price"));
        p.setPropertyArea(rs.getDouble("property_area"));
        p.setAreaUnit(rs.getString("area_unit"));
        p.setState(rs.getString("state"));
        p.setCity(rs.getString("city"));
        p.setLocality(rs.getString("locality"));
        p.setAddress(rs.getString("address"));
        p.setPincode(rs.getString("pincode"));
        p.setApprovalStatus(rs.getString("approval_status"));
        p.setListingStatus(rs.getString("listing_status"));
        p.setFeatured(rs.getBoolean("is_featured"));
        p.setRejectionReason(rs.getString("rejection_reason"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setOwnerName(rs.getString("owner_name"));
        p.setOwnerEmail(rs.getString("owner_email"));
        p.setOwnerPhone(rs.getString("owner_phone"));
        p.setOwnerVerified(rs.getBoolean("owner_verified"));
    }

    private void loadCategoryDetails(Connection conn, Property p) {
        try {
            if (p instanceof ResidentialProperty) {
                ResidentialProperty rp = (ResidentialProperty) p;
                String q = "SELECT * FROM residential_details WHERE property_id = ?";
                try (PreparedStatement st = conn.prepareStatement(q)) {
                    st.setInt(1, p.getPropertyId());
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            rp.setResidentialType(r.getString("residential_type"));
                            rp.setBedrooms(r.getInt("bedrooms"));
                            rp.setBathrooms(r.getInt("bathrooms"));
                            rp.setBuiltUpArea(r.getDouble("built_up_area"));
                            rp.setCarpetArea(r.getDouble("carpet_area"));
                            rp.setParkingSlots(r.getInt("parking_slots"));
                            rp.setFurnishingStatus(r.getString("furnishing_status"));
                            rp.setFloorNumber(r.getInt("floor_number"));
                            rp.setTotalFloors(r.getInt("total_floors"));
                            rp.setPropertyAgeYears(r.getInt("property_age_years"));
                        }
                    }
                }
            } else if (p instanceof CommercialProperty) {
                CommercialProperty cp = (CommercialProperty) p;
                String q = "SELECT * FROM commercial_details WHERE property_id = ?";
                try (PreparedStatement st = conn.prepareStatement(q)) {
                    st.setInt(1, p.getPropertyId());
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            cp.setCommercialType(r.getString("commercial_type"));
                            cp.setCarpetArea(r.getDouble("carpet_area"));
                            cp.setNumberOfFloors(r.getInt("number_of_floors"));
                            cp.setParkingSpaces(r.getInt("parking_spaces"));
                            cp.setBuildingType(r.getString("building_type"));
                            cp.setCommercialUsage(r.getString("commercial_usage"));
                            cp.setPowerBackup(r.getBoolean("power_backup"));
                        }
                    }
                }
            } else if (p instanceof AgriculturalLand) {
                AgriculturalLand al = (AgriculturalLand) p;
                String q = "SELECT * FROM land_details WHERE property_id = ?";
                try (PreparedStatement st = conn.prepareStatement(q)) {
                    st.setInt(1, p.getPropertyId());
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            al.setLandType(r.getString("land_type"));
                            al.setTotalLandArea(r.getDouble("total_land_area"));
                            al.setLandUnit(r.getString("land_unit"));
                            al.setSoilType(r.getString("soil_type"));
                            al.setWaterAvailability(r.getString("water_availability"));
                            al.setHasBorewell(r.getBoolean("has_borewell"));
                            al.setRoadConnectivity(r.getString("road_connectivity"));
                            al.setHasElectricity(r.getBoolean("has_electricity"));
                            al.setLandDocumentType(r.getString("land_document_type"));
                            al.setSurveyNumber(r.getString("survey_number"));
                        }
                    }
                }
            } else if (p instanceof FarmHouse) {
                FarmHouse fh = (FarmHouse) p;
                String q = "SELECT * FROM farmhouse_details WHERE property_id = ?";
                try (PreparedStatement st = conn.prepareStatement(q)) {
                    st.setInt(1, p.getPropertyId());
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            fh.setLandArea(r.getDouble("land_area"));
                            fh.setBuiltUpArea(r.getDouble("built_up_area"));
                            fh.setBedrooms(r.getInt("bedrooms"));
                            fh.setBathrooms(r.getInt("bathrooms"));
                            fh.setHasGarden(r.getBoolean("has_garden"));
                            fh.setHasSwimmingPool(r.getBoolean("has_swimming_pool"));
                            fh.setParkingCapacity(r.getInt("parking_capacity"));
                            fh.setWaterFacilities(r.getString("water_facilities"));
                        }
                    }
                }
            } else if (p instanceof Plot) {
                Plot pl = (Plot) p;
                String q = "SELECT * FROM plot_details WHERE property_id = ?";
                try (PreparedStatement st = conn.prepareStatement(q)) {
                    st.setInt(1, p.getPropertyId());
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            pl.setPlotType(r.getString("plot_type"));
                            pl.setPlotDimensions(r.getString("plot_dimensions"));
                            pl.setTotalArea(r.getDouble("total_area"));
                            pl.setFacingDirection(r.getString("facing_direction"));
                            pl.setRoadWidthFeet(r.getInt("road_width_feet"));
                            pl.setApprovedLayout(r.getBoolean("is_approved_layout"));
                            pl.setApprovalAuthority(r.getString("approval_authority"));
                        }
                    }
                }
            } else if (p instanceof Building) {
                Building b = (Building) p;
                String q = "SELECT * FROM building_details WHERE property_id = ?";
                try (PreparedStatement st = conn.prepareStatement(q)) {
                    st.setInt(1, p.getPropertyId());
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            b.setBuildingType(r.getString("building_type"));
                            b.setNumberOfFloors(r.getInt("number_of_floors"));
                            b.setTotalUnits(r.getInt("total_units"));
                            b.setConstructionStatus(r.getString("construction_status"));
                            b.setBuilderName(r.getString("builder_name"));
                            b.setProjectCompletionDate(r.getDate("project_completion_date"));
                            b.setReraNumber(r.getString("rera_number"));
                        }
                    }
                }
            }
        } catch (SQLException ignored) {}
    }

    private void loadImagesAndAmenities(Connection conn, Property p) {
        try {
            String imgSql = "SELECT image_url, is_primary FROM property_images WHERE property_id = ? ORDER BY is_primary DESC, image_id ASC";
            try (PreparedStatement st = conn.prepareStatement(imgSql)) {
                st.setInt(1, p.getPropertyId());
                try (ResultSet rs = st.executeQuery()) {
                    List<String> imgs = new ArrayList<>();
                    while (rs.next()) {
                        String url = rs.getString("image_url");
                        imgs.add(url);
                        if (rs.getBoolean("is_primary") && p.getPrimaryImageUrl() == null) {
                            p.setPrimaryImageUrl(url);
                        }
                    }
                    p.setImageUrls(imgs);
                    if (p.getPrimaryImageUrl() == null && !imgs.isEmpty()) {
                        p.setPrimaryImageUrl(imgs.get(0));
                    }
                }
            }

            String amenSql = "SELECT amenity_name FROM property_amenities WHERE property_id = ?";
            try (PreparedStatement st = conn.prepareStatement(amenSql)) {
                st.setInt(1, p.getPropertyId());
                try (ResultSet rs = st.executeQuery()) {
                    List<String> amens = new ArrayList<>();
                    while (rs.next()) {
                        amens.add(rs.getString("amenity_name"));
                    }
                    p.setAmenities(amens);
                }
            }
        } catch (SQLException ignored) {}
    }
}
