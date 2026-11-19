package com.realestate.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.realestate.dao.PropertyDAO;
import com.realestate.model.property.*;
import com.realestate.model.user.User;
import com.realestate.services.PropertyService;
import com.realestate.util.JsonUtil;

@WebServlet("/api/properties/*")
public class PropertyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private PropertyService propertyService;

    @Override
    public void init() throws ServletException {
        this.propertyService = new PropertyService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        String pathInfo = req.getPathInfo();
        try {
            if ("/details".equals(pathInfo)) {
                String idParam = req.getParameter("id");
                if (idParam == null || idParam.isEmpty()) {
                    out.print(JsonUtil.error("Property ID required", 400));
                    return;
                }
                int propId = Integer.parseInt(idParam);
                Property p = propertyService.getPropertyDetails(propId);
                out.print(JsonUtil.success("Property details loaded", serializeProperty(p, true)));

            } else if ("/my".equals(pathInfo)) {
                HttpSession session = req.getSession(false);
                if (session == null || session.getAttribute("userId") == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print(JsonUtil.error("Please login to view your listings", 401));
                    return;
                }
                int userId = (Integer) session.getAttribute("userId");
                List<Property> myList = propertyService.getPropertiesByOwner(userId);
                out.print(JsonUtil.success("My properties loaded", serializePropertyList(myList)));

            } else {
                // Search & Filter List
                String isFeatured = req.getParameter("featured");
                if ("true".equalsIgnoreCase(isFeatured)) {
                    List<Property> featured = propertyService.getFeaturedProperties(6);
                    out.print(JsonUtil.success("Featured properties loaded", serializePropertyList(featured)));
                    return;
                }

                Map<String, String> filters = new HashMap<>();
                filters.put("city", req.getParameter("city"));
                filters.put("category", req.getParameter("category"));
                filters.put("purpose", req.getParameter("purpose"));
                filters.put("minPrice", req.getParameter("minPrice"));
                filters.put("maxPrice", req.getParameter("maxPrice"));
                filters.put("keyword", req.getParameter("keyword"));

                String sortBy = req.getParameter("sortBy");
                List<Property> list = propertyService.searchProperties(filters, sortBy);
                out.print(JsonUtil.success("Properties found", serializePropertyList(list)));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(JsonUtil.error(e.getMessage(), 500));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(JsonUtil.error("Authentication required to perform this action.", 401));
            return;
        }

        User currentUser = (User) session.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        try {
            if ("/delete".equals(pathInfo)) {
                int propId = Integer.parseInt(req.getParameter("propertyId"));
                boolean isAdmin = currentUser.isAdministrator();
                boolean deleted = propertyService.deleteProperty(propId, currentUser.getUserId(), isAdmin);
                if (deleted) {
                    out.print(JsonUtil.success("Property deleted successfully", null));
                } else {
                    out.print(JsonUtil.error("Failed to delete property", 400));
                }
            } else {
                // Post / Create Property
                String categoryCode = req.getParameter("categoryCode");
                if (categoryCode == null || categoryCode.trim().isEmpty()) {
                    categoryCode = "RESIDENTIAL";
                }

                Property p = PropertyDAO.createPropertyInstance(categoryCode);
                p.setOwnerId(currentUser.getUserId());
                p.setTitle(req.getParameter("title"));
                p.setDescription(req.getParameter("description"));
                p.setPurpose(req.getParameter("purpose") != null ? req.getParameter("purpose").toUpperCase() : "BUY");

                try { p.setPrice(Double.parseDouble(req.getParameter("price"))); } catch (Exception e) { p.setPrice(0.0); }
                try { p.setPropertyArea(Double.parseDouble(req.getParameter("propertyArea"))); } catch (Exception e) { p.setPropertyArea(0.0); }
                p.setAreaUnit(req.getParameter("areaUnit") != null ? req.getParameter("areaUnit") : "Sq. Ft.");

                p.setState(req.getParameter("state") != null ? req.getParameter("state") : "Karnataka");
                p.setCity(req.getParameter("city"));
                p.setLocality(req.getParameter("locality") != null ? req.getParameter("locality") : req.getParameter("city"));
                p.setAddress(req.getParameter("address"));
                p.setPincode(req.getParameter("pincode"));

                // Category IDs
                int catId = 1;
                switch (categoryCode.toUpperCase()) {
                    case "COMMERCIAL": catId = 2; break;
                    case "AGRICULTURAL": catId = 3; break;
                    case "FARMHOUSE": catId = 4; break;
                    case "PLOT": catId = 5; break;
                    case "BUILDING": catId = 6; break;
                }
                p.setCategoryId(catId);

                // Populate Subclass attributes
                populateCategoryDetailsFromRequest(req, p);

                // Images
                String imagesCsv = req.getParameter("imageUrls");
                if (imagesCsv != null && !imagesCsv.trim().isEmpty()) {
                    String[] arr = imagesCsv.split("[,;\\n]");
                    List<String> list = new ArrayList<>();
                    for (String img : arr) {
                        if (!img.trim().isEmpty()) list.add(img.trim());
                    }
                    p.setImageUrls(list);
                } else {
                    // Default fallback professional placeholder
                    p.setImageUrls(Collections.singletonList("https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80"));
                }

                // Amenities
                String amenitiesCsv = req.getParameter("amenities");
                if (amenitiesCsv != null && !amenitiesCsv.trim().isEmpty()) {
                    String[] arr = amenitiesCsv.split("[,;\\n]");
                    List<String> list = new ArrayList<>();
                    for (String a : arr) {
                        if (!a.trim().isEmpty()) list.add(a.trim());
                    }
                    p.setAmenities(list);
                }

                Property saved = propertyService.postProperty(p);
                out.print(JsonUtil.success("Property posted successfully and is pending admin approval.", "{\"propertyId\":" + saved.getPropertyId() + "}"));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(JsonUtil.error(e.getMessage(), 400));
        }
    }

    private void populateCategoryDetailsFromRequest(HttpServletRequest req, Property p) {
        if (p instanceof ResidentialProperty) {
            ResidentialProperty rp = (ResidentialProperty) p;
            rp.setResidentialType(req.getParameter("residentialType") != null ? req.getParameter("residentialType") : "APARTMENT");
            try { rp.setBedrooms(Integer.parseInt(req.getParameter("bedrooms"))); } catch (Exception e) { rp.setBedrooms(2); }
            try { rp.setBathrooms(Integer.parseInt(req.getParameter("bathrooms"))); } catch (Exception e) { rp.setBathrooms(2); }
            try { rp.setBuiltUpArea(Double.parseDouble(req.getParameter("builtUpArea"))); } catch (Exception e) { rp.setBuiltUpArea(p.getPropertyArea()); }
            rp.setFurnishingStatus(req.getParameter("furnishingStatus") != null ? req.getParameter("furnishingStatus") : "SEMI_FURNISHED");
        } else if (p instanceof CommercialProperty) {
            CommercialProperty cp = (CommercialProperty) p;
            cp.setCommercialType(req.getParameter("commercialType") != null ? req.getParameter("commercialType") : "OFFICE_SPACE");
            try { cp.setCarpetArea(Double.parseDouble(req.getParameter("carpetArea"))); } catch (Exception e) { cp.setCarpetArea(p.getPropertyArea()); }
            cp.setCommercialUsage(req.getParameter("commercialUsage"));
        } else if (p instanceof AgriculturalLand) {
            AgriculturalLand al = (AgriculturalLand) p;
            al.setLandType(req.getParameter("landType") != null ? req.getParameter("landType") : "FARMLAND");
            try { al.setTotalLandArea(Double.parseDouble(req.getParameter("totalLandArea"))); } catch (Exception e) { al.setTotalLandArea(p.getPropertyArea()); }
            al.setSoilType(req.getParameter("soilType"));
            al.setWaterAvailability(req.getParameter("waterAvailability"));
            al.setHasBorewell("true".equalsIgnoreCase(req.getParameter("hasBorewell")));
        } else if (p instanceof FarmHouse) {
            FarmHouse fh = (FarmHouse) p;
            try { fh.setLandArea(Double.parseDouble(req.getParameter("landArea"))); } catch (Exception e) { fh.setLandArea(p.getPropertyArea()); }
            try { fh.setBedrooms(Integer.parseInt(req.getParameter("bedrooms"))); } catch (Exception e) { fh.setBedrooms(3); }
            fh.setHasGarden(true);
            fh.setHasSwimmingPool("true".equalsIgnoreCase(req.getParameter("hasSwimmingPool")));
        } else if (p instanceof Plot) {
            Plot pl = (Plot) p;
            pl.setPlotType(req.getParameter("plotType") != null ? req.getParameter("plotType") : "RESIDENTIAL_PLOT");
            pl.setPlotDimensions(req.getParameter("plotDimensions"));
            pl.setFacingDirection(req.getParameter("facingDirection") != null ? req.getParameter("facingDirection") : "EAST");
            pl.setApprovalAuthority(req.getParameter("approvalAuthority"));
        } else if (p instanceof Building) {
            Building b = (Building) p;
            b.setBuildingType(req.getParameter("buildingType") != null ? req.getParameter("buildingType") : "APARTMENT_BUILDING");
            try { b.setTotalUnits(Integer.parseInt(req.getParameter("totalUnits"))); } catch (Exception e) { b.setTotalUnits(10); }
            b.setBuilderName(req.getParameter("builderName"));
            b.setConstructionStatus(req.getParameter("constructionStatus") != null ? req.getParameter("constructionStatus") : "READY_TO_MOVE");
        }
    }

    public static String serializePropertyList(List<Property> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeProperty(list.get(i), false));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String serializeProperty(Property p, boolean includeDetails) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"propertyId\":").append(p.getPropertyId());
        sb.append(",\"ownerId\":").append(p.getOwnerId());
        sb.append(",\"categoryId\":").append(p.getCategoryId());
        sb.append(",\"categoryCode\":").append(JsonUtil.escape(p.getCategoryCode()));
        sb.append(",\"categoryName\":").append(JsonUtil.escape(p.getCategoryName()));
        sb.append(",\"title\":").append(JsonUtil.escape(p.getTitle()));
        sb.append(",\"description\":").append(JsonUtil.escape(p.getDescription()));
        sb.append(",\"purpose\":").append(JsonUtil.escape(p.getPurpose()));
        sb.append(",\"price\":").append(p.getPrice());
        sb.append(",\"propertyArea\":").append(p.getPropertyArea());
        sb.append(",\"areaUnit\":").append(JsonUtil.escape(p.getAreaUnit()));
        sb.append(",\"city\":").append(JsonUtil.escape(p.getCity()));
        sb.append(",\"state\":").append(JsonUtil.escape(p.getState()));
        sb.append(",\"locality\":").append(JsonUtil.escape(p.getLocality()));
        sb.append(",\"address\":").append(JsonUtil.escape(p.getAddress()));
        sb.append(",\"pincode\":").append(JsonUtil.escape(p.getPincode()));
        sb.append(",\"approvalStatus\":").append(JsonUtil.escape(p.getApprovalStatus()));
        sb.append(",\"listingStatus\":").append(JsonUtil.escape(p.getListingStatus()));
        sb.append(",\"isFeatured\":").append(p.isFeatured());
        sb.append(",\"primaryImageUrl\":").append(JsonUtil.escape(p.getPrimaryImageUrl()));
        sb.append(",\"typeDetails\":").append(JsonUtil.escape(p.getPropertyTypeDetails()));
        sb.append(",\"estMaintenance\":").append(p.calculateEstimatedMaintenanceCost());
        sb.append(",\"estRegistration\":").append(p.calculateEstimatedRegistrationFee());
        sb.append(",\"ownerName\":").append(JsonUtil.escape(p.getOwnerName()));
        sb.append(",\"ownerPhone\":").append(JsonUtil.escape(p.getOwnerPhone()));
        sb.append(",\"ownerEmail\":").append(JsonUtil.escape(p.getOwnerEmail()));
        sb.append(",\"ownerVerified\":").append(p.isOwnerVerified());

        if (p instanceof ResidentialProperty) {
            ResidentialProperty rp = (ResidentialProperty) p;
            sb.append(",\"bedrooms\":").append(rp.getBedrooms());
            sb.append(",\"bathrooms\":").append(rp.getBathrooms());
            sb.append(",\"furnishingStatus\":").append(JsonUtil.escape(rp.getFurnishingStatus()));
        } else {
            sb.append(",\"bedrooms\":0");
            sb.append(",\"bathrooms\":0");
            sb.append(",\"furnishingStatus\":\"N/A\"");
        }

        if (includeDetails) {
            // Images array
            sb.append(",\"images\":[");
            if (p.getImageUrls() != null) {
                for (int i = 0; i < p.getImageUrls().size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonUtil.escape(p.getImageUrls().get(i)));
                }
            }
            sb.append("]");

            // Amenities array
            sb.append(",\"amenities\":[");
            if (p.getAmenities() != null) {
                for (int i = 0; i < p.getAmenities().size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonUtil.escape(p.getAmenities().get(i)));
                }
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }
}
