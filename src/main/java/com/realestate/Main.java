package com.realestate;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.realestate.controllers.PropertyServlet;
import com.realestate.database.DBConnection;
import com.realestate.database.SupabaseClient;
import com.realestate.dao.AnalyticsDAO;
import com.realestate.dao.PropertyDAO;
import com.realestate.dao.UserDAO;
import com.realestate.model.appointment.PropertyVisit;
import com.realestate.model.property.*;
import com.realestate.model.purchase.PurchaseTransaction;
import com.realestate.model.rental.RentPayment;
import com.realestate.model.rental.RentalAgreement;
import com.realestate.model.user.*;
import com.realestate.model.verification.OwnerVerification;
import com.realestate.services.*;
import com.realestate.util.JsonUtil;

/**
 * Main Application Runner.
 * 1. Demonstrates Java OOP Concepts execution (Polymorphism, Inheritance, Interfaces, Multithreading).
 * 2. Tests Database Connection.
 * 3. Starts an embedded HTTP server on port 8080 serving both the clean modern frontend
 *    and the REST APIs, making it 100% runnable out-of-the-box without requiring Tomcat setup!
 */
public class Main {

    private static final int PORT = parsePort();

    private static int parsePort() {
        String p = System.getenv("PORT");
        if (p != null && !p.trim().isEmpty()) {
            try { return Integer.parseInt(p.trim()); } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }
    private static final UserService userService = new UserService();
    private static final PropertyService propertyService = new PropertyService();
    private static final AppointmentService appointmentService = new AppointmentService();
    private static final RentalService rentalService = new RentalService();
    private static final PurchaseService purchaseService = new PurchaseService();
    private static final VerificationService verificationService = new VerificationService();
    private static final AnalyticsDAO analyticsDAO = new AnalyticsDAO();

    // In-memory thread-safe session store for embedded server (token -> User)
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("   REAL ESTATE MANAGEMENT SYSTEM - STARTING APPLICATION");
        System.out.println("   Tagline: \"Find. Buy. Sell. Rent. Build.\"");
        System.out.println("=================================================================");

        // 1. Demonstrate OOP Principles in Console
        demonstrateOOPConcepts();

        // 2. Test Database Connection (Supabase primary, MySQL JDBC fallback)
        SupabaseClient sb = SupabaseClient.getInstance();
        if (sb.isConfigured()) {
            System.out.println("\n[1/3] Testing Supabase Database Connection (" + sb.getBaseUrl() + ")...");
            boolean sbOk = sb.testConnection();
            if (sbOk) {
                System.out.println(">>> Supabase Database Connection: [ONLINE & READY]");
            } else {
                System.out.println(">>> Supabase Database Connection: [FAILED - CHECK SUPABASE_URL / SUPABASE_KEY IN .env]");
            }
        } else {
            System.out.println("\n[1/3] Testing MySQL Database Connection...");
            boolean dbOk = DBConnection.getInstance().testConnection();
            if (dbOk) {
                System.out.println(">>> MySQL Database Connection: [SUCCESSFUL]");
            } else {
                System.out.println(">>> MySQL Database: [OFFLINE or NOT CONFIGURED YET]");
                System.out.println("    (Configure SUPABASE_URL & SUPABASE_KEY in .env, or import database/schema.sql into MySQL)");
            }
        }

        // 3. Start Embedded HTTP Web Server
        try {
            System.out.println("\n[2/3] Initializing HTTP Web Server on port " + PORT + "...");
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Health check endpoint for cloud platforms
            server.createContext("/health", ex -> sendJson(ex, 200, "{\"status\":\"ok\",\"service\":\"real-estate-backend\"}"));

            // API Handlers
            server.createContext("/api/auth", new AuthHttpHandler());
            server.createContext("/api/properties", new PropertyHttpHandler());
            server.createContext("/api/appointments", new AppointmentHttpHandler());
            server.createContext("/api/rentals", new RentalHttpHandler());
            server.createContext("/api/purchases", new PurchaseHttpHandler());
            server.createContext("/api/verifications", new VerificationHttpHandler());
            server.createContext("/api/admin", new AdminHttpHandler());

            // Static Frontend Handler (serves webapp folder)
            server.createContext("/", new StaticFileHttpHandler());

            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("\n[3/3] SERVER STARTED SUCCESSFULLY!");
            System.out.println("=================================================================");
            System.out.println(">>> Access the Web Application at: http://localhost:" + PORT);
            System.out.println(">>> Default Admin Account: admin@realestate.com / Admin@123");
            System.out.println(">>> Default Owner Account: rajesh.owner@example.com / Password@123");
            System.out.println(">>> Default Buyer Account: priya.buyer@example.com / Password@123");
            System.out.println("=================================================================\n");

        } catch (Exception e) {
            System.err.println("Failed to start embedded server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demonstrates core Java OOP requirements requested in the prompt.
     */
    private static void demonstrateOOPConcepts() {
        System.out.println("\n--- DEMONSTRATING JAVA OOP PRINCIPLES ---");

        // 1. Encapsulation & Inheritance: Subclass extending Abstract Superclass
        ResidentialProperty villa = new ResidentialProperty(101, 2, "Mysuru Garden Villa", "BUY", 18500000.0, 3200.0, "Karnataka", "Mysuru", 4, 4);
        AgriculturalLand farmland = new AgriculturalLand();
        farmland.setTitle("Mandya Sugarcane Land");
        farmland.setPurpose("BUY");
        farmland.setPrice(16500000.0);
        farmland.setTotalLandArea(5.5);
        farmland.setLandUnit("ACRES");
        farmland.setPropertyArea(5.5);

        // 2. Polymorphism: Same method call on abstract type produces distinct category behavior
        Property[] properties = { villa, farmland };
        for (Property p : properties) {
            System.out.println("[POLYMORPHISM] Property Type: " + p.getCategoryName());
            System.out.println("  Details: " + p.getPropertyTypeDetails());
            System.out.println("  Monthly Maintenance: Rs. " + p.calculateEstimatedMaintenanceCost());
            
            // 3. Method Overloading:
            System.out.println("  Est. Registration (Default 6.6%): Rs. " + p.calculateEstimatedRegistrationFee());
            System.out.println("  Est. Registration (Concessional 3.0%): Rs. " + p.calculateEstimatedRegistrationFee(3.0));
        }

        // 4. Multithreading & Synchronization Demo
        System.out.println("\n[MULTITHREADING & SYNCHRONIZATION] Triggering Async Notification Service Worker...");
        AsyncNotificationService.getInstance().dispatchNotification(
            "admin@realestate.com",
            "System Boot Alert",
            "Real Estate Management System successfully initialized."
        );
        System.out.println("------------------------------------------");
    }

    // =========================================================================
    // EMBEDDED HTTP HANDLERS
    // =========================================================================

    private static class StaticFileHttpHandler implements HttpHandler {
        private final File webappDir = new File("src/main/webapp");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            File canonicalBase = webappDir.getCanonicalFile();
            File file = new File(canonicalBase, path.replace('/', File.separatorChar)).getCanonicalFile();
            if (!file.getPath().startsWith(canonicalBase.getPath() + File.separator)) {
                String forbidden = "<h1>403 Forbidden</h1>";
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(403, forbidden.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(forbidden.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            if (!file.exists() || file.isDirectory()) {
                file = new File(canonicalBase, path.replace('/', File.separatorChar) + ".html").getCanonicalFile();
            }

            if (file.getPath().startsWith(canonicalBase.getPath() + File.separator) && file.exists() && !file.isDirectory()) {
                String mime = getMimeType(file.getName());
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                String response = "<h1>404 Not Found</h1><p>The file " + path + " was not found.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private String getMimeType(String filename) {
            if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
            if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
            if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
            if (filename.endsWith(".png")) return "image/png";
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
            if (filename.endsWith(".svg")) return "image/svg+xml";
            return "text/plain";
        }
    }

    private static class AuthHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            try {
                if (path.endsWith("/me")) {
                    if (user != null) {
                        String data = "{\"userId\":" + user.getUserId() + 
                                      ",\"fullName\":" + JsonUtil.escape(user.getFullName()) + 
                                      ",\"email\":" + JsonUtil.escape(user.getEmail()) + 
                                      ",\"mobile\":" + JsonUtil.escape(user.getMobileNumber()) + 
                                      ",\"role\":" + JsonUtil.escape(user.getUserRole()) + 
                                      ",\"isVerified\":" + user.isVerified() + "}";
                        sendJson(exchange, 200, JsonUtil.success("Authenticated", data));
                    } else {
                        sendJson(exchange, 401, JsonUtil.error("Not authenticated", 401));
                    }
                } else if (path.endsWith("/logout")) {
                    if (token != null) sessions.remove(token);
                    exchange.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=; Path=/; Max-Age=0; HttpOnly");
                    sendJson(exchange, 200, JsonUtil.success("Logged out successfully", null));
                } else if (path.endsWith("/login")) {
                    String email = params.get("email");
                    String password = params.get("password");
                    User loggedIn = userService.login(email, password);

                    String newToken = UUID.randomUUID().toString();
                    sessions.put(newToken, loggedIn);

                    exchange.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=" + newToken + "; Path=/; HttpOnly");

                    String data = "{\"userId\":" + loggedIn.getUserId() + 
                                  ",\"fullName\":" + JsonUtil.escape(loggedIn.getFullName()) + 
                                  ",\"email\":" + JsonUtil.escape(loggedIn.getEmail()) + 
                                  ",\"role\":" + JsonUtil.escape(loggedIn.getUserRole()) + 
                                  ",\"isVerified\":" + loggedIn.isVerified() + "}";
                    sendJson(exchange, 200, JsonUtil.success("Login successful", data));
                } else if (path.endsWith("/register")) {
                    String fullName = params.get("fullName");
                    String email = params.get("email");
                    String mobile = params.get("mobile");
                    String password = params.get("password");
                    String role = params.get("role");

                    User reg = userService.registerUser(fullName, email, mobile, password, role);
                    String newToken = UUID.randomUUID().toString();
                    sessions.put(newToken, reg);

                    exchange.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=" + newToken + "; Path=/; HttpOnly");

                    String data = "{\"userId\":" + reg.getUserId() + 
                                  ",\"fullName\":" + JsonUtil.escape(reg.getFullName()) + 
                                  ",\"email\":" + JsonUtil.escape(reg.getEmail()) + 
                                  ",\"role\":" + JsonUtil.escape(reg.getUserRole()) + "}";
                    sendJson(exchange, 200, JsonUtil.success("Registration successful", data));
                } else if (path.endsWith("/validate-email")) {
                    String email = params.get("email");
                    EmailService.EmailValidationResult v = userService.validateEmail(email);
                    boolean exists = v.valid && "true".equalsIgnoreCase(params.get("checkExists")) && userService.emailExists(v.normalizedEmail);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("valid", v.valid);
                    data.put("normalizedEmail", v.normalizedEmail);
                    data.put("message", v.message);
                    data.put("suggestion", v.suggestion);
                    data.put("mxVerified", v.mxVerified);
                    data.put("registered", exists);
                    sendJson(exchange, 200, JsonUtil.success(v.message, JsonUtil.toJson(data)));
                } else if (path.endsWith("/forgot-password")) {
                    Map<String, Object> otpResult = userService.requestPasswordResetOtp(params.get("email"));
                    sendJson(exchange, 200, JsonUtil.success(String.valueOf(otpResult.get("infoMessage")), JsonUtil.toJson(otpResult)));
                } else if (path.endsWith("/verify-otp")) {
                    String resetToken = userService.verifyPasswordResetOtp(params.get("email"), params.get("otp"));
                    sendJson(exchange, 200, JsonUtil.success("OTP verified successfully", "{\"resetToken\":" + JsonUtil.escape(resetToken) + "}"));
                } else if (path.endsWith("/reset-password")) {
                    userService.resetPassword(params.get("email"), params.get("resetToken"), params.get("otp"), params.get("newPassword"));
                    sendJson(exchange, 200, JsonUtil.success("Password updated successfully! You can now sign in with your new password.", null));
                } else {
                    sendJson(exchange, 404, JsonUtil.error("Not found", 404));
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class PropertyHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    if (user == null) {
                        sendJson(exchange, 401, JsonUtil.error("Authentication required to post or modify properties", 401));
                        return;
                    }

                    if (path.endsWith("/delete")) {
                        int propId = Integer.parseInt(params.get("propertyId"));
                        boolean deleted = propertyService.deleteProperty(propId, user.getUserId(), user.isAdministrator());
                        sendJson(exchange, 200, JsonUtil.success("Property deleted successfully", null));
                    } else {
                        // Create Property
                        String catCode = params.get("categoryCode");
                        if (catCode == null) catCode = "RESIDENTIAL";
                        Property p = PropertyDAO.createPropertyInstance(catCode);
                        p.setOwnerId(user.getUserId());
                        p.setTitle(params.get("title"));
                        p.setDescription(params.get("description"));
                        p.setPurpose(params.get("purpose") != null ? params.get("purpose").toUpperCase() : "BUY");
                        p.setPrice(Double.parseDouble(params.get("price")));
                        p.setPropertyArea(Double.parseDouble(params.get("propertyArea")));
                        p.setAreaUnit(params.get("areaUnit") != null ? params.get("areaUnit") : "Sq. Ft.");
                        p.setState(params.get("state") != null ? params.get("state") : "Karnataka");
                        p.setCity(params.get("city"));
                        p.setLocality(params.get("locality") != null ? params.get("locality") : params.get("city"));
                        p.setAddress(params.get("address"));
                        p.setPincode(params.get("pincode"));

                        int catId = 1;
                        if ("COMMERCIAL".equalsIgnoreCase(catCode)) catId = 2;
                        else if ("AGRICULTURAL".equalsIgnoreCase(catCode)) catId = 3;
                        else if ("FARMHOUSE".equalsIgnoreCase(catCode)) catId = 4;
                        else if ("PLOT".equalsIgnoreCase(catCode)) catId = 5;
                        else if ("BUILDING".equalsIgnoreCase(catCode)) catId = 6;
                        p.setCategoryId(catId);

                        // Category-specific attributes for all 6 property types
                        if (p instanceof ResidentialProperty) {
                            ResidentialProperty rp = (ResidentialProperty) p;
                            try { rp.setBedrooms(Integer.parseInt(params.get("bedrooms"))); } catch (Exception ignored) { rp.setBedrooms(2); }
                            try { rp.setBathrooms(Integer.parseInt(params.get("bathrooms"))); } catch (Exception ignored) { rp.setBathrooms(2); }
                            try { rp.setBuiltUpArea(Double.parseDouble(params.get("builtUpArea"))); } catch (Exception ignored) { rp.setBuiltUpArea(p.getPropertyArea()); }
                            rp.setResidentialType(params.get("residentialType") != null ? params.get("residentialType") : "APARTMENT");
                            rp.setFurnishingStatus(params.get("furnishingStatus") != null ? params.get("furnishingStatus") : "SEMI_FURNISHED");
                        } else if (p instanceof CommercialProperty) {
                            CommercialProperty cp = (CommercialProperty) p;
                            cp.setCommercialType(params.get("commercialType") != null ? params.get("commercialType") : "OFFICE_SPACE");
                            try { cp.setCarpetArea(Double.parseDouble(params.get("carpetArea"))); } catch (Exception ignored) { cp.setCarpetArea(p.getPropertyArea()); }
                            cp.setCommercialUsage(params.get("commercialUsage"));
                        } else if (p instanceof AgriculturalLand) {
                            AgriculturalLand al = (AgriculturalLand) p;
                            al.setLandType(params.get("landType") != null ? params.get("landType") : "FARMLAND");
                            try { al.setTotalLandArea(Double.parseDouble(params.get("totalLandArea"))); } catch (Exception ignored) { al.setTotalLandArea(p.getPropertyArea()); }
                            al.setSoilType(params.get("soilType"));
                            al.setWaterAvailability(params.get("waterAvailability"));
                            al.setHasBorewell("true".equalsIgnoreCase(params.get("hasBorewell")));
                        } else if (p instanceof FarmHouse) {
                            FarmHouse fh = (FarmHouse) p;
                            try { fh.setLandArea(Double.parseDouble(params.get("landArea"))); } catch (Exception ignored) { fh.setLandArea(p.getPropertyArea()); }
                            try { fh.setBuiltUpArea(Double.parseDouble(params.get("builtUpArea"))); } catch (Exception ignored) { fh.setBuiltUpArea(p.getPropertyArea()); }
                            try { fh.setBedrooms(Integer.parseInt(params.get("bedrooms"))); } catch (Exception ignored) { fh.setBedrooms(3); }
                            try { fh.setBathrooms(Integer.parseInt(params.get("bathrooms"))); } catch (Exception ignored) { fh.setBathrooms(2); }
                            fh.setHasGarden(true);
                            fh.setHasSwimmingPool("true".equalsIgnoreCase(params.get("hasSwimmingPool")));
                        } else if (p instanceof Plot) {
                            Plot pl = (Plot) p;
                            pl.setPlotType(params.get("plotType") != null ? params.get("plotType") : "RESIDENTIAL_PLOT");
                            pl.setPlotDimensions(params.get("plotDimensions"));
                            pl.setTotalArea(p.getPropertyArea());
                            pl.setFacingDirection(params.get("facingDirection") != null ? params.get("facingDirection") : "EAST");
                            pl.setApprovalAuthority(params.get("approvalAuthority"));
                        } else if (p instanceof Building) {
                            Building b = (Building) p;
                            b.setBuildingType(params.get("buildingType") != null ? params.get("buildingType") : "APARTMENT_BUILDING");
                            try { b.setTotalUnits(Integer.parseInt(params.get("totalUnits"))); } catch (Exception ignored) { b.setTotalUnits(10); }
                            b.setBuilderName(params.get("builderName"));
                            b.setConstructionStatus(params.get("constructionStatus") != null ? params.get("constructionStatus") : "READY_TO_MOVE");
                        }

                        // Images
                        String imgUrls = params.get("imageUrls");
                        if (imgUrls != null && !imgUrls.trim().isEmpty()) {
                            List<String> imgList = new ArrayList<>();
                            for (String img : imgUrls.split("[,;\\n]")) {
                                if (!img.trim().isEmpty()) imgList.add(img.trim());
                            }
                            if (imgList.isEmpty()) {
                                imgList.add("https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80");
                            }
                            p.setImageUrls(imgList);
                        } else {
                            p.setImageUrls(Collections.singletonList("https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80"));
                        }

                        // Amenities
                        String amens = params.get("amenities");
                        if (amens != null && !amens.trim().isEmpty()) {
                            List<String> amenList = new ArrayList<>();
                            for (String a : amens.split("[,;\\n]")) {
                                if (!a.trim().isEmpty()) amenList.add(a.trim());
                            }
                            p.setAmenities(amenList);
                        }

                        Property saved = propertyService.postProperty(p);
                        sendJson(exchange, 200, JsonUtil.success("Property posted and pending approval", "{\"propertyId\":" + saved.getPropertyId() + "}"));
                    }
                } else {
                    // GET
                    if (path.endsWith("/details")) {
                        int propId = Integer.parseInt(params.get("id"));
                        Property p = propertyService.getPropertyDetails(propId);
                        sendJson(exchange, 200, JsonUtil.success("Details loaded", PropertyServlet.serializeProperty(p, true)));
                    } else if (path.endsWith("/compare")) {
                        String ids = params.get("ids");
                        List<Property> list = new ArrayList<>();
                        if (ids != null) {
                            for (String idStr : ids.split(",")) {
                                try {
                                    Property p = propertyService.getPropertyDetails(Integer.parseInt(idStr.trim()));
                                    if (p != null) list.add(p);
                                } catch (Exception ignored) {}
                            }
                        }
                        sendJson(exchange, 200, JsonUtil.success("Compare loaded", PropertyServlet.serializePropertyList(list)));
                    } else if (path.endsWith("/my")) {
                        if (user == null) {
                            sendJson(exchange, 401, JsonUtil.error("Please login", 401));
                            return;
                        }
                        List<Property> list = propertyService.getPropertiesByOwner(user.getUserId());
                        sendJson(exchange, 200, JsonUtil.success("My properties", PropertyServlet.serializePropertyList(list)));
                    } else {
                        if ("true".equalsIgnoreCase(params.get("featured"))) {
                            List<Property> featured = propertyService.getFeaturedProperties(6);
                            sendJson(exchange, 200, JsonUtil.success("Featured", PropertyServlet.serializePropertyList(featured)));
                        } else {
                            List<Property> list = propertyService.searchProperties(params, params.get("sortBy"));
                            sendJson(exchange, 200, JsonUtil.success("Found", PropertyServlet.serializePropertyList(list)));
                        }
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class AppointmentHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            if (user == null) {
                sendJson(exchange, 401, JsonUtil.error("Login required", 401));
                return;
            }

            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    if (path.endsWith("/status")) {
                        int visitId = Integer.parseInt(params.get("visitId"));
                        String status = params.get("status");
                        appointmentService.updateVisitStatus(visitId, status);
                        sendJson(exchange, 200, JsonUtil.success("Status updated", null));
                    } else {
                        PropertyVisit pv = new PropertyVisit();
                        pv.setPropertyId(Integer.parseInt(params.get("propertyId")));
                        pv.setUserId(user.getUserId());
                        pv.setUserEmail(user.getEmail());
                        pv.setVisitDate(Date.valueOf(params.get("visitDate")));
                        String t = params.get("visitTime");
                        if (t != null && t.length() == 5) t += ":00";
                        pv.setVisitTime(Time.valueOf(t != null ? t : "10:00:00"));
                        pv.setContactNumber(params.get("contactNumber") != null ? params.get("contactNumber") : user.getMobileNumber());
                        pv.setNotes(params.get("notes"));

                        PropertyVisit scheduled = appointmentService.scheduleVisit(pv);
                        sendJson(exchange, 200, JsonUtil.success("Visit booked", "{\"visitId\":" + scheduled.getVisitId() + "}"));
                    }
                } else {
                    List<PropertyVisit> list;
                    if (user.isAdministrator()) list = appointmentService.getAllVisits();
                    else if ("PROPERTY_OWNER".equalsIgnoreCase(user.getUserRole()) || "SELLER".equalsIgnoreCase(user.getUserRole()) || "AGENT".equalsIgnoreCase(user.getUserRole())) {
                        list = appointmentService.getVisitsByOwner(user.getUserId());
                    } else {
                        list = appointmentService.getVisitsByUser(user.getUserId());
                    }
                    sendJson(exchange, 200, JsonUtil.success("Appointments", serializeVisits(list)));
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }

        private String serializeVisits(List<PropertyVisit> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                PropertyVisit v = list.get(i);
                sb.append("{\"visitId\":").append(v.getVisitId())
                  .append(",\"propertyId\":").append(v.getPropertyId())
                  .append(",\"propertyTitle\":").append(JsonUtil.escape(v.getPropertyTitle()))
                  .append(",\"propertyCity\":").append(JsonUtil.escape(v.getPropertyCity()))
                  .append(",\"userId\":").append(v.getUserId())
                  .append(",\"userName\":").append(JsonUtil.escape(v.getUserName()))
                  .append(",\"visitDate\":").append(JsonUtil.escape(v.getVisitDate() != null ? v.getVisitDate().toString() : ""))
                  .append(",\"visitTime\":").append(JsonUtil.escape(v.getVisitTime() != null ? v.getVisitTime().toString() : ""))
                  .append(",\"contactNumber\":").append(JsonUtil.escape(v.getContactNumber()))
                  .append(",\"notes\":").append(JsonUtil.escape(v.getNotes()))
                  .append(",\"status\":").append(JsonUtil.escape(v.getAppointmentStatus()))
                  .append("}");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    private static class RentalHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            if (user == null) {
                sendJson(exchange, 401, JsonUtil.error("Login required", 401));
                return;
            }

            try {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    if (path.endsWith("/pay")) {
                        RentPayment rp = new RentPayment();
                        rp.setAgreementId(Integer.parseInt(params.get("agreementId")));
                        rp.setPaymentMonth(params.get("paymentMonth"));
                        rp.setAmountPaid(Double.parseDouble(params.get("amount")));
                        rp.setPaymentDate(new Date(System.currentTimeMillis()));
                        rp.setPaymentMethod(params.get("paymentMethod") != null ? params.get("paymentMethod") : "SIMULATED_UPI");
                        RentPayment recorded = rentalService.payRent(rp);
                        sendJson(exchange, 200, JsonUtil.success("Rent paid", "{\"txnRef\":" + JsonUtil.escape(recorded.getTransactionRef()) + "}"));
                    } else {
                        RentalAgreement ra = new RentalAgreement();
                        ra.setPropertyId(Integer.parseInt(params.get("propertyId")));
                        ra.setOwnerId(Integer.parseInt(params.get("ownerId")));
                        ra.setTenantId(user.getUserId());
                        ra.setMonthlyRent(Double.parseDouble(params.get("monthlyRent")));
                        ra.setSecurityDeposit(Double.parseDouble(params.get("securityDeposit")));
                        ra.setAgreementStartDate(Date.valueOf(params.get("startDate")));
                        ra.setAgreementEndDate(Date.valueOf(params.get("endDate")));
                        ra.setTermsAndConditions(params.get("terms"));
                        RentalAgreement created = rentalService.createAgreement(ra);
                        sendJson(exchange, 200, JsonUtil.success("Rental agreement created", "{\"agreementId\":" + created.getAgreementId() + "}"));
                    }
                } else {
                    if (path.endsWith("/payments")) {
                        int agreementId = Integer.parseInt(params.get("agreementId"));
                        List<RentPayment> payments = rentalService.getRentPayments(agreementId);
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < payments.size(); i++) {
                            if (i > 0) sb.append(",");
                            RentPayment rp = payments.get(i);
                            sb.append("{\"paymentId\":").append(rp.getRentPaymentId())
                              .append(",\"month\":").append(JsonUtil.escape(rp.getPaymentMonth()))
                              .append(",\"amount\":").append(rp.getAmountPaid())
                              .append(",\"date\":").append(JsonUtil.escape(rp.getPaymentDate() != null ? rp.getPaymentDate().toString() : ""))
                              .append(",\"method\":").append(JsonUtil.escape(rp.getPaymentMethod()))
                              .append(",\"txnRef\":").append(JsonUtil.escape(rp.getTransactionRef()))
                              .append(",\"status\":").append(JsonUtil.escape(rp.getPaymentStatus()))
                              .append("}");
                        }
                        sb.append("]");
                        sendJson(exchange, 200, JsonUtil.success("Rent payments", sb.toString()));
                    } else {
                        List<RentalAgreement> list = rentalService.getAgreementsForUser(user.getUserId());
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < list.size(); i++) {
                            if (i > 0) sb.append(",");
                            RentalAgreement ra = list.get(i);
                            sb.append("{\"agreementId\":").append(ra.getAgreementId())
                              .append(",\"propertyId\":").append(ra.getPropertyId())
                              .append(",\"propertyTitle\":").append(JsonUtil.escape(ra.getPropertyTitle()))
                              .append(",\"ownerName\":").append(JsonUtil.escape(ra.getOwnerName()))
                              .append(",\"tenantName\":").append(JsonUtil.escape(ra.getTenantName()))
                              .append(",\"monthlyRent\":").append(ra.getMonthlyRent())
                              .append(",\"securityDeposit\":").append(ra.getSecurityDeposit())
                              .append(",\"startDate\":").append(JsonUtil.escape(ra.getAgreementStartDate() != null ? ra.getAgreementStartDate().toString() : ""))
                              .append(",\"endDate\":").append(JsonUtil.escape(ra.getAgreementEndDate() != null ? ra.getAgreementEndDate().toString() : ""))
                              .append(",\"status\":").append(JsonUtil.escape(ra.getAgreementStatus()))
                              .append("}");
                        }
                        sb.append("]");
                        sendJson(exchange, 200, JsonUtil.success("Agreements", sb.toString()));
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class PurchaseHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            if (user == null) {
                sendJson(exchange, 401, JsonUtil.error("Login required", 401));
                return;
            }

            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    PurchaseTransaction txn = new PurchaseTransaction();
                    txn.setPropertyId(Integer.parseInt(params.get("propertyId")));
                    txn.setBuyerId(user.getUserId());
                    txn.setSellerId(Integer.parseInt(params.get("sellerId")));
                    txn.setPurchasePrice(Double.parseDouble(params.get("purchasePrice")));
                    txn.setRemarks(params.get("remarks") != null ? params.get("remarks") : "Simulated Project Purchase Transaction");
                    PurchaseTransaction rec = purchaseService.executeSimulatedPurchase(txn);
                    sendJson(exchange, 200, JsonUtil.success("Purchase complete", "{\"txnId\":" + rec.getTransactionId() + ",\"txnRef\":" + JsonUtil.escape(rec.getTransactionReference()) + "}"));
                } else {
                    List<PurchaseTransaction> list = user.isAdministrator() ? purchaseService.getAllTransactions() : purchaseService.getPurchasesForUser(user.getUserId());
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(",");
                        PurchaseTransaction t = list.get(i);
                        sb.append("{\"transactionId\":").append(t.getTransactionId())
                          .append(",\"propertyId\":").append(t.getPropertyId())
                          .append(",\"propertyTitle\":").append(JsonUtil.escape(t.getPropertyTitle()))
                          .append(",\"buyerName\":").append(JsonUtil.escape(t.getBuyerName()))
                          .append(",\"sellerName\":").append(JsonUtil.escape(t.getSellerName()))
                          .append(",\"purchasePrice\":").append(t.getPurchasePrice())
                          .append(",\"date\":").append(JsonUtil.escape(t.getTransactionDate() != null ? t.getTransactionDate().toString() : ""))
                          .append(",\"txnRef\":").append(JsonUtil.escape(t.getTransactionReference()))
                          .append(",\"status\":").append(JsonUtil.escape(t.getPaymentStatus()))
                          .append(",\"remarks\":").append(JsonUtil.escape(t.getRemarks()))
                          .append("}");
                    }
                    sb.append("]");
                    sendJson(exchange, 200, JsonUtil.success("Purchases", sb.toString()));
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class VerificationHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            if (user == null) {
                sendJson(exchange, 401, JsonUtil.error("Login required", 401));
                return;
            }

            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    OwnerVerification v = new OwnerVerification();
                    v.setUserId(user.getUserId());
                    v.setIdentityProofType(params.get("proofType"));
                    v.setIdentityProofNumber(params.get("proofNumber"));
                    v.setDocumentPath(params.get("documentPath") != null ? params.get("documentPath") : "/uploads/docs/doc_" + user.getUserId() + ".pdf");
                    v.setPropertyAddress(params.get("propertyAddress"));
                    OwnerVerification sub = verificationService.submitVerification(v);
                    sendJson(exchange, 200, JsonUtil.success("Verification submitted", "{\"verificationId\":" + sub.getVerificationId() + "}"));
                } else {
                    OwnerVerification v = verificationService.getByUserId(user.getUserId());
                    if (v != null) {
                        String data = "{\"verificationId\":" + v.getVerificationId() +
                                      ",\"status\":" + JsonUtil.escape(v.getVerificationStatus()) + 
                                      ",\"proofType\":" + JsonUtil.escape(v.getIdentityProofType()) + 
                                      ",\"proofNumber\":" + JsonUtil.escape(v.getIdentityProofNumber()) + 
                                      ",\"propertyAddress\":" + JsonUtil.escape(v.getPropertyAddress()) +
                                      ",\"adminNotes\":" + JsonUtil.escape(v.getAdminNotes()) + "}";
                        sendJson(exchange, 200, JsonUtil.success("Record found", data));
                    } else {
                        sendJson(exchange, 200, JsonUtil.success("None", "{\"status\":\"NOT_SUBMITTED\"}"));
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class AdminHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParameters(exchange);
            String token = getSessionToken(exchange);
            User user = token != null ? sessions.get(token) : null;

            if (user == null || !user.isAdministrator()) {
                sendJson(exchange, 403, JsonUtil.error("Administrator access required", 403));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    if (path.endsWith("/approve")) {
                        int propId = Integer.parseInt(params.get("propertyId"));
                        propertyService.approveProperty(propId);
                        sendJson(exchange, 200, JsonUtil.success("Property approved", null));
                    } else if (path.endsWith("/reject")) {
                        int propId = Integer.parseInt(params.get("propertyId"));
                        String reason = params.get("reason") != null ? params.get("reason") : "Does not meet platform guidelines";
                        propertyService.rejectProperty(propId, reason);
                        sendJson(exchange, 200, JsonUtil.success("Property rejected", null));
                    } else if (path.endsWith("/action")) {
                        int verId = Integer.parseInt(params.get("verificationId"));
                        String notes = params.get("notes") != null ? params.get("notes") : "Documents reviewed by Admin";
                        verificationService.updateStatus(verId, params.get("status"), notes);
                        sendJson(exchange, 200, JsonUtil.success("Verification updated", null));
                    }
                } else {
                    if (path.endsWith("/stats")) {
                        Map<String, Integer> stats = analyticsDAO.getAdminDashboardStats();
                        StringBuilder sb = new StringBuilder("{");
                        int i = 0;
                        for (Map.Entry<String, Integer> e : stats.entrySet()) {
                            if (i++ > 0) sb.append(",");
                            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
                        }
                        sb.append("}");
                        sendJson(exchange, 200, JsonUtil.success("Stats", sb.toString()));
                    } else if (path.endsWith("/pending")) {
                        List<Property> pending = propertyService.getPendingProperties();
                        sendJson(exchange, 200, JsonUtil.success("Pending", PropertyServlet.serializePropertyList(pending)));
                    } else if (path.endsWith("/verifications")) {
                        List<OwnerVerification> list = verificationService.getPendingVerifications();
                        StringBuilder sb = new StringBuilder("[");
                        for (int j = 0; j < list.size(); j++) {
                            if (j > 0) sb.append(",");
                            OwnerVerification v = list.get(j);
                            sb.append("{\"verificationId\":").append(v.getVerificationId())
                              .append(",\"userId\":").append(v.getUserId())
                              .append(",\"userName\":").append(JsonUtil.escape(v.getUserName()))
                              .append(",\"userEmail\":").append(JsonUtil.escape(v.getUserEmail()))
                              .append(",\"proofType\":").append(JsonUtil.escape(v.getIdentityProofType()))
                              .append(",\"proofNumber\":").append(JsonUtil.escape(v.getIdentityProofNumber()))
                              .append(",\"address\":").append(JsonUtil.escape(v.getPropertyAddress()))
                              .append(",\"documentPath\":").append(JsonUtil.escape(v.getDocumentPath()))
                              .append(",\"status\":").append(JsonUtil.escape(v.getVerificationStatus()))
                              .append("}");
                        }
                        sb.append("]");
                        sendJson(exchange, 200, JsonUtil.success("Verifications", sb.toString()));
                    } else if (path.endsWith("/users")) {
                        List<User> users = userService.getAllUsers();
                        StringBuilder sb = new StringBuilder("[");
                        for (int k = 0; k < users.size(); k++) {
                            if (k > 0) sb.append(",");
                            User u = users.get(k);
                            sb.append("{\"userId\":").append(u.getUserId())
                              .append(",\"fullName\":").append(JsonUtil.escape(u.getFullName()))
                              .append(",\"email\":").append(JsonUtil.escape(u.getEmail()))
                              .append(",\"mobile\":").append(JsonUtil.escape(u.getMobileNumber()))
                              .append(",\"role\":").append(JsonUtil.escape(u.getUserRole()))
                              .append(",\"isVerified\":").append(u.isVerified())
                              .append(",\"isActive\":").append(u.isActive())
                              .append("}");
                        }
                        sb.append("]");
                        sendJson(exchange, 200, JsonUtil.success("Users", sb.toString()));
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    // Helper utilities for embedded HTTP server
    private static void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getSessionToken(HttpExchange exchange) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String cookieHeader : cookieHeaders) {
                for (String cookie : cookieHeader.split(";")) {
                    String[] parts = cookie.trim().split("=");
                    if (parts.length == 2 && "AUTH_TOKEN".equals(parts[0])) {
                        return parts[1];
                    }
                }
            }
        }
        return null;
    }

    private static Map<String, String> parseParameters(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();

        // Parse query string
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()),
                            URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()));
                } else if (pair.length == 1) {
                    map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), "");
                }
            }
        }

        // Parse POST request body (form-urlencoded)
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            String body = baos.toString(StandardCharsets.UTF_8.name());
            if (!body.isEmpty()) {
                for (String param : body.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length > 1) {
                        map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()),
                                URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()));
                    } else if (pair.length == 1) {
                        map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), "");
                    }
                }
            }
        }
        return map;
    }
}
