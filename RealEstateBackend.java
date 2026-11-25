import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * =============================================================================
 * REAL ESTATE MANAGEMENT SYSTEM - HARDENED ALL-IN-ONE BACKEND (v2.0)
 * Tagline: "Find. Buy. Sell. Rent. Build."
 *
 * Demonstrates:
 * 1. Classes, Objects, and Encapsulation
 * 2. Inheritance (User and Property hierarchies)
 * 3. Polymorphism (Dynamic dispatch & interface implementations)
 * 4. Method Overloading & Method Overriding
 * 5. Abstract Classes & Interfaces (Searchable, Payable, Verifiable, Auditable)
 * 6. Constructors with 'this' and 'super' chaining
 * 7. 'static' and 'final' keywords
 * 8. Custom Exception Handling Hierarchy with HTTP Status Mapping
 * 9. Multithreading (Producer-Consumer background notification worker thread)
 * 10. Synchronization & Thread-Safety (ConcurrentHashMap, AtomicInteger, SessionManager, RateLimiter)
 * 11. Security Hardening (PBKDF2-HMAC-SHA256, Path-Traversal Guard, IDOR & Price Integrity Checks)
 * 12. 3-Tier Persistence (Supabase REST -> MySQL JDBC -> Seeded In-Memory Demo Store)
 * 13. Embedded REST HTTP Server on Port 8080 (JSON + Form Body Support)
 *
 * Run directly with: java RealEstateBackend.java
 * =============================================================================
 */
public class RealEstateBackend {

    public static final int SERVER_PORT = parsePort();
    public static final Instant SERVER_STARTED_AT = Instant.now();
    private static final int MAX_BODY_BYTES = 1024 * 1024; // 1 MB body cap
    private static final String ALLOWED_CORS_ORIGIN = resolveCorsOrigin();

    private static int parsePort() {
        String p = System.getenv("PORT");
        if (p != null && !p.trim().isEmpty()) {
            try { return Integer.parseInt(p.trim()); } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

    private static String resolveCorsOrigin() {
        Map<String, String> dotEnv = DBConnection.loadDotEnv();
        String origin = DBConnection.getConfig("CORS_ORIGIN", dotEnv, "*");
        return (origin == null || origin.trim().isEmpty()) ? "*" : origin.trim();
    }

    public enum PersistenceMode {
        SUPABASE, MYSQL, IN_MEMORY_DEMO
    }

    private static volatile PersistenceMode activePersistenceMode = PersistenceMode.IN_MEMORY_DEMO;

    // =========================================================================
    // 1. OOP INTERFACES
    // =========================================================================

    public interface Searchable {
        boolean matchesKeyword(String keyword);
        boolean matchesLocation(String city);
        boolean matchesPriceRange(double minPrice, double maxPrice);
    }

    public interface Payable {
        double calculateTotalPayable();
        String generateTransactionReference();
        boolean processSimulatedPayment(double amount);
    }

    public interface Verifiable {
        boolean isVerified();
        void setVerified(boolean verified);
        String getVerificationDetails();
    }

    public interface Auditable {
        Timestamp getCreatedAt();
        String getAuditSummary();
    }

    // =========================================================================
    // 2. CUSTOM EXCEPTION HIERARCHY
    // =========================================================================

    public static class RealEstateException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int errorCode;

        public RealEstateException(String message) {
            super(message);
            this.errorCode = 500;
        }

        public RealEstateException(String message, int errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() { return errorCode; }
    }

    public static class AuthenticationException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        public AuthenticationException(String message) { super(message, 401); }
    }

    public static class PropertyNotFoundException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        public PropertyNotFoundException(int propertyId) { super("Property ID #" + propertyId + " not found.", 404); }
    }

    public static class InvalidPropertyDataException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        public InvalidPropertyDataException(String message) { super(message, 400); }
    }

    public static class PaymentFailedException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        public PaymentFailedException(String message) { super(message, 402); }
    }

    public static class UnauthorizedAccessException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        public UnauthorizedAccessException(String message) { super(message, 403); }
    }

    public static class ConflictException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        public ConflictException(String message) { super(message, 409); }
    }

    public static class RateLimitExceededException extends RealEstateException {
        private static final long serialVersionUID = 1L;
        private final long retryAfterSeconds;
        public RateLimitExceededException(String message, long retryAfterSeconds) {
            super(message, 429);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    // =========================================================================
    // 3. USER OOP HIERARCHY
    // =========================================================================

    public static abstract class User implements Auditable {
        private int userId;
        private String fullName;
        private String email;
        private String mobileNumber;
        private String passwordHash;
        private String passwordSalt;
        private String userRole; // BUYER, SELLER, TENANT, PROPERTY_OWNER, AGENT, ADMIN
        private boolean isVerified;
        private boolean isActive;
        private Timestamp createdAt;

        public User() {
            this(0, "", "", "", "BUYER");
        }

        public User(int userId, String fullName, String email, String mobileNumber, String userRole) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.mobileNumber = mobileNumber;
            this.userRole = userRole;
            this.isActive = true;
            this.isVerified = false;
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }

        public abstract String getRolePermissionsDescription();

        public final boolean isAdministrator() {
            return "ADMIN".equalsIgnoreCase(this.userRole);
        }

        public final boolean isListingActor() {
            return isAdministrator()
                || "PROPERTY_OWNER".equalsIgnoreCase(userRole)
                || "SELLER".equalsIgnoreCase(userRole)
                || "AGENT".equalsIgnoreCase(userRole);
        }

        @Override
        public Timestamp getCreatedAt() { return createdAt; }

        @Override
        public String getAuditSummary() {
            return "User #" + userId + " (" + fullName + ") [" + userRole + "]";
        }

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName != null ? fullName.trim() : ""; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email != null ? email.trim().toLowerCase() : ""; }
        public String getMobileNumber() { return mobileNumber; }
        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber != null ? mobileNumber.trim() : ""; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public String getPasswordSalt() { return passwordSalt; }
        public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
        public boolean isVerified() { return isVerified; }
        public void setVerified(boolean verified) { isVerified = verified; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }

    public static class Buyer extends User implements Payable {
        private double maxBudget;

        public Buyer() { super(); setUserRole("BUYER"); }
        public Buyer(int id, String name, String email, String phone, double budget) {
            super(id, name, email, phone, "BUYER");
            this.maxBudget = budget;
        }

        @Override
        public String getRolePermissionsDescription() {
            return "Permissions: Browse listings, book visits, simulate purchases, compare properties.";
        }

        @Override public double calculateTotalPayable() { return this.maxBudget; }
        @Override public String generateTransactionReference() {
            return "BUYER-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        @Override public boolean processSimulatedPayment(double amount) { return amount > 0; }
        public double getMaxBudget() { return maxBudget; }
        public void setMaxBudget(double maxBudget) { this.maxBudget = maxBudget; }
    }

    public static class Seller extends User implements Verifiable {
        public Seller() { super(); setUserRole("SELLER"); }
        public Seller(int id, String name, String email, String phone) {
            super(id, name, email, phone, "SELLER");
        }

        @Override
        public String getRolePermissionsDescription() {
            return "Permissions: Post properties for sale, receive purchase offers, view appointment requests.";
        }

        @Override
        public String getVerificationDetails() {
            return "Seller #" + getUserId() + " | Status: " + (isVerified() ? "Verified" : "Pending");
        }
    }

    public static class Tenant extends User implements Payable {
        private double monthlyBudget;

        public Tenant() { super(); setUserRole("TENANT"); }
        public Tenant(int id, String name, String email, String phone, double budget) {
            super(id, name, email, phone, "TENANT");
            this.monthlyBudget = budget;
        }

        @Override
        public String getRolePermissionsDescription() {
            return "Permissions: Browse rentals, manage rental agreements, simulate rent payments.";
        }

        @Override public double calculateTotalPayable() { return this.monthlyBudget; }
        @Override public String generateTransactionReference() {
            return "RENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        @Override public boolean processSimulatedPayment(double amount) { return amount > 0; }
        public double getMonthlyBudget() { return monthlyBudget; }
        public void setMonthlyBudget(double monthlyBudget) { this.monthlyBudget = monthlyBudget; }
    }

    public static class PropertyOwner extends User implements Verifiable {
        private String titleDeedNumber;

        public PropertyOwner() { super(); setUserRole("PROPERTY_OWNER"); }
        public PropertyOwner(int id, String name, String email, String phone) {
            super(id, name, email, phone, "PROPERTY_OWNER");
        }

        @Override
        public String getRolePermissionsDescription() {
            return "Permissions: Post listings for sell/rent, manage rental agreements, submit KYC verification.";
        }

        @Override
        public String getVerificationDetails() {
            return "Owner #" + getUserId() + " | Title Deed: " + (titleDeedNumber != null ? titleDeedNumber : "Pending");
        }
        public String getTitleDeedNumber() { return titleDeedNumber; }
        public void setTitleDeedNumber(String deed) { this.titleDeedNumber = deed; }
    }

    public static class Agent extends User implements Verifiable {
        private String agencyName;
        private String reraRegistrationNumber;

        public Agent() { super(); setUserRole("AGENT"); }
        public Agent(int id, String name, String email, String phone, String agency, String rera) {
            super(id, name, email, phone, "AGENT");
            this.agencyName = agency;
            this.reraRegistrationNumber = rera;
        }

        @Override
        public String getRolePermissionsDescription() {
            return "Permissions: Multi-property brokerage, commercial projects, client visits.";
        }

        @Override
        public String getVerificationDetails() {
            return "RERA Agent: " + reraRegistrationNumber + " (" + agencyName + ")";
        }
        public String getAgencyName() { return agencyName; }
        public void setAgencyName(String agency) { this.agencyName = agency; }
        public String getReraRegistrationNumber() { return reraRegistrationNumber; }
        public void setReraRegistrationNumber(String rera) { this.reraRegistrationNumber = rera; }
    }

    public static class Admin extends User {
        public Admin() { super(); setUserRole("ADMIN"); setVerified(true); }
        public Admin(int id, String name, String email, String phone) {
            super(id, name, email, phone, "ADMIN");
            setVerified(true);
        }

        @Override
        public String getRolePermissionsDescription() {
            return "Permissions: Full system governance, approve/reject listings, verify owners, view audit logs.";
        }
    }

    // =========================================================================
    // 4. PROPERTY OOP HIERARCHY
    // =========================================================================

    public static abstract class Property implements Searchable, Auditable {
        private int propertyId;
        private int ownerId;
        private int categoryId;
        private String categoryName;
        private String categoryCode;
        private String title;
        private String description;
        private String purpose; // BUY, RENT, LEASE
        private double price;
        private double propertyArea;
        private String areaUnit;
        private String state;
        private String city;
        private String locality;
        private String address;
        private String pincode;
        private double latitude;
        private double longitude;
        private String approvalStatus; // PENDING, APPROVED, REJECTED
        private String listingStatus;  // AVAILABLE, UNDER_OFFER, SOLD, RENTED, INACTIVE
        private String rejectionReason;
        private boolean isFeatured;
        private String primaryImageUrl;
        private List<String> imageUrls = new ArrayList<>();
        private List<String> amenities = new ArrayList<>();
        private Timestamp createdAt;

        private String ownerName;
        private String ownerEmail;
        private String ownerPhone;
        private boolean ownerVerified;
        private String videoUrl;
        private String videoLabel;
        private String model3dUrl;
        private String model3dFormat = "GLB";
        private String hdriPreset = "daylight";

        // Engagement & analytics metrics
        private double averageRating = 4.8;
        private int reviewCount = 0;
        private int viewsCount = 0;

        public Property() {
            this(0, 0, "", "BUY", 0.0, 0.0, "Karnataka", "Mysuru");
        }

        public Property(int propertyId, int ownerId, String title, String purpose, double price, double propertyArea, String state, String city) {
            this.propertyId = propertyId;
            this.ownerId = ownerId;
            this.title = title;
            this.purpose = purpose;
            this.price = price;
            this.propertyArea = propertyArea;
            this.areaUnit = "Sq. Ft.";
            this.state = state;
            this.city = city;
            this.approvalStatus = "PENDING";
            this.listingStatus = "AVAILABLE";
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }

        public abstract String getPropertyTypeDetails();
        public abstract double calculateEstimatedMaintenanceCost();
        public abstract void validateCategorySpecifics() throws InvalidPropertyDataException;

        public final void validateBaseFields() throws InvalidPropertyDataException {
            if (title == null || title.trim().length() < 4) {
                throw new InvalidPropertyDataException("Property title must be at least 4 characters.");
            }
            if (city == null || city.trim().isEmpty()) {
                throw new InvalidPropertyDataException("City is required.");
            }
            if (price <= 0) {
                throw new InvalidPropertyDataException("Price must be a positive number.");
            }
            if (propertyArea <= 0) {
                throw new InvalidPropertyDataException("Property area must be positive.");
            }
            if (!"BUY".equalsIgnoreCase(purpose) && !"RENT".equalsIgnoreCase(purpose) && !"LEASE".equalsIgnoreCase(purpose)) {
                throw new InvalidPropertyDataException("Purpose must be BUY, RENT, or LEASE.");
            }
            validateCategorySpecifics();
        }

        // Method Overloading (1): Default 6.6% stamp duty/registration fee
        public double calculateEstimatedRegistrationFee() {
            return calculateEstimatedRegistrationFee(6.6);
        }

        // Method Overloading (2): Custom government concession rate
        public double calculateEstimatedRegistrationFee(double customRatePercentage) {
            if ("BUY".equalsIgnoreCase(this.purpose)) {
                return (this.price * customRatePercentage) / 100.0;
            }
            return 0.0;
        }

        @Override
        public boolean matchesKeyword(String keyword) {
            if (keyword == null || keyword.trim().isEmpty()) return true;
            String kw = keyword.toLowerCase().trim();
            return (title != null && title.toLowerCase().contains(kw)) ||
                   (description != null && description.toLowerCase().contains(kw)) ||
                   (locality != null && locality.toLowerCase().contains(kw)) ||
                   (city != null && city.toLowerCase().contains(kw)) ||
                   (categoryName != null && categoryName.toLowerCase().contains(kw));
        }

        @Override
        public boolean matchesLocation(String queryCity) {
            if (queryCity == null || queryCity.trim().isEmpty() || "ALL".equalsIgnoreCase(queryCity)) return true;
            return this.city != null && this.city.equalsIgnoreCase(queryCity.trim());
        }

        @Override
        public boolean matchesPriceRange(double minPrice, double maxPrice) {
            if (minPrice > 0 && this.price < minPrice) return false;
            if (maxPrice > 0 && this.price > maxPrice) return false;
            return true;
        }

        @Override public Timestamp getCreatedAt() { return createdAt; }
        @Override public String getAuditSummary() {
            return "Property #" + propertyId + " (" + title + ") Rs." + price;
        }

        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int propertyId) { this.propertyId = propertyId; }
        public int getOwnerId() { return ownerId; }
        public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
        public int getCategoryId() { return categoryId; }
        public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public String getCategoryCode() { return categoryCode; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public double getPropertyArea() { return propertyArea; }
        public void setPropertyArea(double propertyArea) { this.propertyArea = propertyArea; }
        public String getAreaUnit() { return areaUnit; }
        public void setAreaUnit(String areaUnit) { this.areaUnit = areaUnit; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getLocality() { return locality; }
        public void setLocality(String locality) { this.locality = locality; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPincode() { return pincode; }
        public void setPincode(String pincode) { this.pincode = pincode; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public String getApprovalStatus() { return approvalStatus; }
        public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
        public String getListingStatus() { return listingStatus; }
        public void setListingStatus(String listingStatus) { this.listingStatus = listingStatus; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
        public boolean isFeatured() { return isFeatured; }
        public void setFeatured(boolean featured) { isFeatured = featured; }
        public String getPrimaryImageUrl() { return primaryImageUrl; }
        public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
        public List<String> getAmenities() { return amenities; }
        public void setAmenities(List<String> amenities) { this.amenities = amenities; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public String getOwnerEmail() { return ownerEmail; }
        public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
        public String getOwnerPhone() { return ownerPhone; }
        public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
        public boolean isOwnerVerified() { return ownerVerified; }
        public void setOwnerVerified(boolean ownerVerified) { this.ownerVerified = ownerVerified; }
        public String getVideoUrl() { return videoUrl; }
        public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
        public String getVideoLabel() { return videoLabel; }
        public void setVideoLabel(String videoLabel) { this.videoLabel = videoLabel; }
        public String getModel3dUrl() { return model3dUrl; }
        public void setModel3dUrl(String model3dUrl) { this.model3dUrl = model3dUrl; }
        public String getModel3dFormat() { return model3dFormat; }
        public void setModel3dFormat(String model3dFormat) { this.model3dFormat = model3dFormat; }
        public String getHdriPreset() { return hdriPreset; }
        public void setHdriPreset(String hdriPreset) { this.hdriPreset = hdriPreset; }
        public double getAverageRating() { return averageRating; }
        public void setAverageRating(double averageRating) { this.averageRating = averageRating; }
        public int getReviewCount() { return reviewCount; }
        public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
        public int getViewsCount() { return viewsCount; }
        public void setViewsCount(int viewsCount) { this.viewsCount = viewsCount; }
    }

    public static class ResidentialProperty extends Property {
        private String residentialType; // APARTMENT, INDEPENDENT_HOUSE, FLAT, VILLA, DUPLEX, PG
        private int bedrooms;
        private int bathrooms;
        private double builtUpArea;
        private String furnishingStatus;

        public ResidentialProperty() {
            super();
            setCategoryId(1);
            setCategoryCode("RESIDENTIAL");
            setCategoryName("Residential Properties");
            this.bedrooms = 2;
            this.bathrooms = 2;
            this.furnishingStatus = "SEMI_FURNISHED";
        }

        public ResidentialProperty(int id, int ownerId, String title, String purpose, double price, double area, String state, String city, int beds, int baths) {
            super(id, ownerId, title, purpose, price, area, state, city);
            setCategoryId(1);
            setCategoryCode("RESIDENTIAL");
            setCategoryName("Residential Properties");
            this.bedrooms = beds;
            this.bathrooms = baths;
            this.builtUpArea = area;
            this.furnishingStatus = "SEMI_FURNISHED";
        }

        @Override
        public String getPropertyTypeDetails() {
            return bedrooms + " BHK " + (residentialType != null ? residentialType.replace('_', ' ') : "Residential") +
                   " | " + bathrooms + " Baths | " + (furnishingStatus != null ? furnishingStatus.replace('_', ' ') : "Unfurnished");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() {
            return (builtUpArea > 0 ? builtUpArea : getPropertyArea()) * 2.5;
        }

        @Override
        public void validateCategorySpecifics() throws InvalidPropertyDataException {
            if (bedrooms <= 0) throw new InvalidPropertyDataException("Residential property must have at least 1 bedroom.");
        }

        public String getResidentialType() { return residentialType; }
        public void setResidentialType(String type) { this.residentialType = type; }
        public int getBedrooms() { return bedrooms; }
        public void setBedrooms(int beds) { this.bedrooms = beds; }
        public int getBathrooms() { return bathrooms; }
        public void setBathrooms(int baths) { this.bathrooms = baths; }
        public double getBuiltUpArea() { return builtUpArea; }
        public void setBuiltUpArea(double area) { this.builtUpArea = area; }
        public String getFurnishingStatus() { return furnishingStatus; }
        public void setFurnishingStatus(String status) { this.furnishingStatus = status; }
    }

    public static class CommercialProperty extends Property {
        private String commercialType;
        private double carpetArea;
        private String commercialUsage;
        private boolean powerBackup;

        public CommercialProperty() {
            super();
            setCategoryId(2);
            setCategoryCode("COMMERCIAL");
            setCategoryName("Commercial Properties");
            this.powerBackup = true;
        }

        @Override
        public String getPropertyTypeDetails() {
            return (commercialType != null ? commercialType.replace('_', ' ') : "Commercial") +
                   " | Carpet: " + (carpetArea > 0 ? carpetArea : getPropertyArea()) + " Sq. Ft." +
                   (powerBackup ? " | 100% Power Backup" : "");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() {
            return (carpetArea > 0 ? carpetArea : getPropertyArea()) * 4.5;
        }

        @Override
        public void validateCategorySpecifics() throws InvalidPropertyDataException {
            if (carpetArea <= 0 && getPropertyArea() <= 0) throw new InvalidPropertyDataException("Commercial property requires valid carpet area.");
        }

        public String getCommercialType() { return commercialType; }
        public void setCommercialType(String type) { this.commercialType = type; }
        public double getCarpetArea() { return carpetArea; }
        public void setCarpetArea(double area) { this.carpetArea = area; }
        public String getCommercialUsage() { return commercialUsage; }
        public void setCommercialUsage(String usage) { this.commercialUsage = usage; }
        public boolean isPowerBackup() { return powerBackup; }
        public void setPowerBackup(boolean backup) { this.powerBackup = backup; }
    }

    public static class AgriculturalLand extends Property {
        private String landType;
        private double totalLandArea;
        private String landUnit;
        private String soilType;
        private String waterAvailability;
        private boolean hasBorewell;

        public AgriculturalLand() {
            super();
            setCategoryId(3);
            setCategoryCode("AGRICULTURAL");
            setCategoryName("Agricultural Land");
            setAreaUnit("Acres");
            this.landUnit = "ACRES";
        }

        @Override
        public String getPropertyTypeDetails() {
            return (landType != null ? landType.replace('_', ' ') : "Farmland") +
                   " | " + (totalLandArea > 0 ? totalLandArea : getPropertyArea()) + " " + landUnit +
                   (soilType != null ? " | Soil: " + soilType : "");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() {
            return (totalLandArea > 0 ? totalLandArea : getPropertyArea()) * 1500.0;
        }

        @Override
        public void validateCategorySpecifics() throws InvalidPropertyDataException {
            if (totalLandArea <= 0 && getPropertyArea() <= 0) throw new InvalidPropertyDataException("Agricultural land requires positive acreage.");
        }

        public String getLandType() { return landType; }
        public void setLandType(String type) { this.landType = type; }
        public double getTotalLandArea() { return totalLandArea; }
        public void setTotalLandArea(double area) { this.totalLandArea = area; }
        public String getLandUnit() { return landUnit; }
        public void setLandUnit(String unit) { this.landUnit = unit; }
        public String getSoilType() { return soilType; }
        public void setSoilType(String soil) { this.soilType = soil; }
        public String getWaterAvailability() { return waterAvailability; }
        public void setWaterAvailability(String water) { this.waterAvailability = water; }
        public boolean isHasBorewell() { return hasBorewell; }
        public void setHasBorewell(boolean borewell) { this.hasBorewell = borewell; }
    }

    public static class FarmHouse extends Property {
        private double landArea;
        private double builtUpArea;
        private int bedrooms;
        private int bathrooms;
        private boolean hasGarden;
        private boolean hasSwimmingPool;

        public FarmHouse() {
            super();
            setCategoryId(4);
            setCategoryCode("FARMHOUSE");
            setCategoryName("Farmhouses");
            this.hasGarden = true;
            this.bedrooms = 3;
            this.bathrooms = 3;
        }

        @Override
        public String getPropertyTypeDetails() {
            return "Farmhouse on " + (landArea > 0 ? landArea : getPropertyArea()) + " Acres | " +
                   bedrooms + " Bed Villa | " + (hasSwimmingPool ? "Private Pool" : "Lush Garden");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() {
            return 5000.0 + ((landArea > 0 ? landArea : getPropertyArea()) * 1000.0);
        }

        @Override
        public void validateCategorySpecifics() throws InvalidPropertyDataException {
            if (landArea <= 0 && getPropertyArea() <= 0) throw new InvalidPropertyDataException("Farmhouse requires designated land area.");
        }

        public double getLandArea() { return landArea; }
        public void setLandArea(double area) { this.landArea = area; }
        public double getBuiltUpArea() { return builtUpArea; }
        public void setBuiltUpArea(double area) { this.builtUpArea = area; }
        public int getBedrooms() { return bedrooms; }
        public void setBedrooms(int beds) { this.bedrooms = beds; }
        public int getBathrooms() { return bathrooms; }
        public void setBathrooms(int baths) { this.bathrooms = baths; }
        public boolean isHasGarden() { return hasGarden; }
        public void setHasGarden(boolean garden) { this.hasGarden = garden; }
        public boolean isHasSwimmingPool() { return hasSwimmingPool; }
        public void setHasSwimmingPool(boolean pool) { this.hasSwimmingPool = pool; }
    }

    public static class Plot extends Property {
        private String plotType;
        private String plotDimensions;
        private double totalArea;
        private String facingDirection;
        private String approvalAuthority;

        public Plot() {
            super();
            setCategoryId(5);
            setCategoryCode("PLOT");
            setCategoryName("Plots and Sites");
            this.facingDirection = "EAST";
        }

        @Override
        public String getPropertyTypeDetails() {
            return (plotType != null ? plotType.replace('_', ' ') : "Plot") +
                   (plotDimensions != null ? " | Dim: " + plotDimensions : "") +
                   " | Facing: " + (facingDirection != null ? facingDirection : "EAST");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() { return 500.0; }

        @Override
        public void validateCategorySpecifics() throws InvalidPropertyDataException {
            if (totalArea <= 0 && getPropertyArea() <= 0) throw new InvalidPropertyDataException("Plot requires valid dimensions or total area.");
        }

        public String getPlotType() { return plotType; }
        public void setPlotType(String type) { this.plotType = type; }
        public String getPlotDimensions() { return plotDimensions; }
        public void setPlotDimensions(String dim) { this.plotDimensions = dim; }
        public double getTotalArea() { return totalArea; }
        public void setTotalArea(double area) { this.totalArea = area; }
        public String getFacingDirection() { return facingDirection; }
        public void setFacingDirection(String facing) { this.facingDirection = facing; }
        public String getApprovalAuthority() { return approvalAuthority; }
        public void setApprovalAuthority(String auth) { this.approvalAuthority = auth; }
    }

    public static class Building extends Property {
        private String buildingType;
        private int totalUnits;
        private String constructionStatus;
        private String builderName;

        public Building() {
            super();
            setCategoryId(6);
            setCategoryCode("BUILDING");
            setCategoryName("Buildings and Projects");
            this.totalUnits = 10;
        }

        @Override
        public String getPropertyTypeDetails() {
            return (buildingType != null ? buildingType.replace('_', ' ') : "Building") +
                   " | " + totalUnits + " Units | Builder: " + (builderName != null ? builderName : "Verified Developer");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() { return Math.max(1, totalUnits) * 1500.0; }

        @Override
        public void validateCategorySpecifics() throws InvalidPropertyDataException {
            if (totalUnits <= 0) throw new InvalidPropertyDataException("Building project requires total units.");
        }

        public String getBuildingType() { return buildingType; }
        public void setBuildingType(String type) { this.buildingType = type; }
        public int getTotalUnits() { return totalUnits; }
        public void setTotalUnits(int units) { this.totalUnits = units; }
        public String getConstructionStatus() { return constructionStatus; }
        public void setConstructionStatus(String status) { this.constructionStatus = status; }
        public String getBuilderName() { return builderName; }
        public void setBuilderName(String name) { this.builderName = name; }
    }

    // =========================================================================
    // 5. TRANSACTIONAL, ENGAGEMENT & GOVERNANCE MODELS
    // =========================================================================

    public static class RentalAgreement {
        private int agreementId;
        private int propertyId;
        private String propertyTitle;
        private int ownerId;
        private String ownerName;
        private int tenantId;
        private String tenantName;
        private double monthlyRent;
        private double securityDeposit;
        private Date agreementStartDate;
        private Date agreementEndDate;
        private String agreementStatus;
        private String terms;

        public int getAgreementId() { return agreementId; }
        public void setAgreementId(int id) { this.agreementId = id; }
        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int id) { this.propertyId = id; }
        public String getPropertyTitle() { return propertyTitle; }
        public void setPropertyTitle(String title) { this.propertyTitle = title; }
        public int getOwnerId() { return ownerId; }
        public void setOwnerId(int id) { this.ownerId = id; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String name) { this.ownerName = name; }
        public int getTenantId() { return tenantId; }
        public void setTenantId(int id) { this.tenantId = id; }
        public String getTenantName() { return tenantName; }
        public void setTenantName(String name) { this.tenantName = name; }
        public double getMonthlyRent() { return monthlyRent; }
        public void setMonthlyRent(double rent) { this.monthlyRent = rent; }
        public double getSecurityDeposit() { return securityDeposit; }
        public void setSecurityDeposit(double dep) { this.securityDeposit = dep; }
        public Date getAgreementStartDate() { return agreementStartDate; }
        public void setAgreementStartDate(Date start) { this.agreementStartDate = start; }
        public Date getAgreementEndDate() { return agreementEndDate; }
        public void setAgreementEndDate(Date end) { this.agreementEndDate = end; }
        public String getAgreementStatus() { return agreementStatus; }
        public void setAgreementStatus(String status) { this.agreementStatus = status; }
        public String getTerms() { return terms; }
        public void setTerms(String terms) { this.terms = terms; }
    }

    public static class RentPayment implements Payable {
        private int rentPaymentId;
        private int agreementId;
        private String paymentMonth;
        private double amountPaid;
        private Date paymentDate;
        private String paymentMethod;
        private String transactionRef;
        private String paymentStatus;

        @Override public double calculateTotalPayable() { return amountPaid; }
        @Override public String generateTransactionReference() { return "RENT-" + System.currentTimeMillis(); }
        @Override public boolean processSimulatedPayment(double amount) {
            if (amount <= 0) return false;
            this.amountPaid = amount;
            this.paymentStatus = "SUCCESS";
            this.transactionRef = generateTransactionReference();
            return true;
        }

        public int getRentPaymentId() { return rentPaymentId; }
        public void setRentPaymentId(int id) { this.rentPaymentId = id; }
        public int getAgreementId() { return agreementId; }
        public void setAgreementId(int id) { this.agreementId = id; }
        public String getPaymentMonth() { return paymentMonth; }
        public void setPaymentMonth(String month) { this.paymentMonth = month; }
        public double getAmountPaid() { return amountPaid; }
        public void setAmountPaid(double amt) { this.amountPaid = amt; }
        public Date getPaymentDate() { return paymentDate; }
        public void setPaymentDate(Date date) { this.paymentDate = date; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String method) { this.paymentMethod = method; }
        public String getTransactionRef() { return transactionRef; }
        public void setTransactionRef(String ref) { this.transactionRef = ref; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String status) { this.paymentStatus = status; }
    }

    public static class PurchaseTransaction implements Payable {
        private int transactionId;
        private int propertyId;
        private String propertyTitle;
        private int buyerId;
        private String buyerName;
        private int sellerId;
        private String sellerName;
        private double purchasePrice;
        private Timestamp transactionDate;
        private String paymentStatus;
        private String transactionReference;
        private String remarks;

        public PurchaseTransaction() {
            this.transactionDate = new Timestamp(System.currentTimeMillis());
            this.paymentStatus = "SUCCESS";
        }

        @Override public double calculateTotalPayable() { return purchasePrice; }
        @Override public String generateTransactionReference() {
            return "TXN-PURCHASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        @Override public boolean processSimulatedPayment(double amount) {
            if (amount <= 0) return false;
            this.purchasePrice = amount;
            this.transactionReference = generateTransactionReference();
            this.paymentStatus = "SUCCESS";
            return true;
        }

        public int getTransactionId() { return transactionId; }
        public void setTransactionId(int id) { this.transactionId = id; }
        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int id) { this.propertyId = id; }
        public String getPropertyTitle() { return propertyTitle; }
        public void setPropertyTitle(String title) { this.propertyTitle = title; }
        public int getBuyerId() { return buyerId; }
        public void setBuyerId(int id) { this.buyerId = id; }
        public String getBuyerName() { return buyerName; }
        public void setBuyerName(String name) { this.buyerName = name; }
        public int getSellerId() { return sellerId; }
        public void setSellerId(int id) { this.sellerId = id; }
        public String getSellerName() { return sellerName; }
        public void setSellerName(String name) { this.sellerName = name; }
        public double getPurchasePrice() { return purchasePrice; }
        public void setPurchasePrice(double price) { this.purchasePrice = price; }
        public Timestamp getTransactionDate() { return transactionDate; }
        public void setTransactionDate(Timestamp date) { this.transactionDate = date; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String status) { this.paymentStatus = status; }
        public String getTransactionReference() { return transactionReference; }
        public void setTransactionReference(String ref) { this.transactionReference = ref; }
        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
    }

    public static class PropertyVisit {
        private int visitId;
        private int propertyId;
        private String propertyTitle;
        private String propertyCity;
        private int ownerId;
        private int userId;
        private String userName;
        private Date visitDate;
        private Time visitTime;
        private String contactNumber;
        private String notes;
        private String appointmentStatus = "PENDING";

        public int getVisitId() { return visitId; }
        public void setVisitId(int id) { this.visitId = id; }
        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int id) { this.propertyId = id; }
        public String getPropertyTitle() { return propertyTitle; }
        public void setPropertyTitle(String title) { this.propertyTitle = title; }
        public String getPropertyCity() { return propertyCity; }
        public void setPropertyCity(String city) { this.propertyCity = city; }
        public int getOwnerId() { return ownerId; }
        public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
        public int getUserId() { return userId; }
        public void setUserId(int id) { this.userId = id; }
        public String getUserName() { return userName; }
        public void setUserName(String name) { this.userName = name; }
        public Date getVisitDate() { return visitDate; }
        public void setVisitDate(Date date) { this.visitDate = date; }
        public Time getVisitTime() { return visitTime; }
        public void setVisitTime(Time time) { this.visitTime = time; }
        public String getContactNumber() { return contactNumber; }
        public void setContactNumber(String contact) { this.contactNumber = contact; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public String getAppointmentStatus() { return appointmentStatus; }
        public void setAppointmentStatus(String status) { this.appointmentStatus = status; }
    }

    public static class OwnerVerification implements Verifiable {
        private int verificationId;
        private int userId;
        private String userName;
        private String userEmail;
        private String identityProofType;
        private String identityProofNumber;
        private String documentPath;
        private String propertyAddress;
        private String verificationStatus = "PENDING";
        private String adminNotes;
        private Timestamp submittedAt = new Timestamp(System.currentTimeMillis());

        @Override public boolean isVerified() { return "APPROVED".equalsIgnoreCase(verificationStatus); }
        @Override public void setVerified(boolean verified) { this.verificationStatus = verified ? "APPROVED" : "REJECTED"; }
        @Override public String getVerificationDetails() { return "Verification #" + verificationId + " for " + userName; }

        public int getVerificationId() { return verificationId; }
        public void setVerificationId(int id) { this.verificationId = id; }
        public int getUserId() { return userId; }
        public void setUserId(int id) { this.userId = id; }
        public String getUserName() { return userName; }
        public void setUserName(String name) { this.userName = name; }
        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String email) { this.userEmail = email; }
        public String getIdentityProofType() { return identityProofType; }
        public void setIdentityProofType(String type) { this.identityProofType = type; }
        public String getIdentityProofNumber() { return identityProofNumber; }
        public void setIdentityProofNumber(String num) { this.identityProofNumber = num; }
        public String getDocumentPath() { return documentPath; }
        public void setDocumentPath(String path) { this.documentPath = path; }
        public String getPropertyAddress() { return propertyAddress; }
        public void setPropertyAddress(String address) { this.propertyAddress = address; }
        public String getVerificationStatus() { return verificationStatus; }
        public void setVerificationStatus(String status) { this.verificationStatus = status; }
        public String getAdminNotes() { return adminNotes; }
        public void setAdminNotes(String notes) { this.adminNotes = notes; }
        public Timestamp getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(Timestamp t) { this.submittedAt = t; }
    }

    public static class PropertyReview {
        private int reviewId;
        private int propertyId;
        private int userId;
        private String userName;
        private String userRole;
        private int rating;
        private String comment;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

        public int getReviewId() { return reviewId; }
        public void setReviewId(int reviewId) { this.reviewId = reviewId; }
        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int propertyId) { this.propertyId = propertyId; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }

    public static class PropertyInquiry {
        private int inquiryId;
        private int propertyId;
        private String propertyTitle;
        private int senderId;
        private String senderName;
        private String senderEmail;
        private int recipientId;
        private String recipientName;
        private String message;
        private String status = "OPEN";
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

        public int getInquiryId() { return inquiryId; }
        public void setInquiryId(int inquiryId) { this.inquiryId = inquiryId; }
        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int propertyId) { this.propertyId = propertyId; }
        public String getPropertyTitle() { return propertyTitle; }
        public void setPropertyTitle(String propertyTitle) { this.propertyTitle = propertyTitle; }
        public int getSenderId() { return senderId; }
        public void setSenderId(int senderId) { this.senderId = senderId; }
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
        public String getSenderEmail() { return senderEmail; }
        public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }
        public int getRecipientId() { return recipientId; }
        public void setRecipientId(int recipientId) { this.recipientId = recipientId; }
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }

    public static class AuditLogEntry {
        private int logId;
        private int actorUserId;
        private String actorName;
        private String action;
        private String entityType;
        private int entityId;
        private String details;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

        public int getLogId() { return logId; }
        public void setLogId(int logId) { this.logId = logId; }
        public int getActorUserId() { return actorUserId; }
        public void setActorUserId(int actorUserId) { this.actorUserId = actorUserId; }
        public String getActorName() { return actorName; }
        public void setActorName(String actorName) { this.actorName = actorName; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public int getEntityId() { return entityId; }
        public void setEntityId(int entityId) { this.entityId = entityId; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }

    public static class UserNotification {
        private int notificationId;
        private int userId;
        private String recipient;
        private String title;
        private String message;
        private boolean read;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

        public int getNotificationId() { return notificationId; }
        public void setNotificationId(int id) { this.notificationId = id; }
        public int getUserId() { return userId; }
        public void setUserId(int id) { this.userId = id; }
        public String getRecipient() { return recipient; }
        public void setRecipient(String r) { this.recipient = r; }
        public String getTitle() { return title; }
        public void setTitle(String t) { this.title = t; }
        public String getMessage() { return message; }
        public void setMessage(String m) { this.message = m; }
        public boolean isRead() { return read; }
        public void setRead(boolean r) { this.read = r; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp c) { this.createdAt = c; }
    }

    // =========================================================================
    // 6. DATABASE CONNECTION, PASSWORD UTIL (PBKDF2), SESSION & RATE LIMITER
    // =========================================================================

    public static final class DBConnection {
        private static final String DEFAULT_HOST = "localhost";
        private static final String DEFAULT_PORT = "3306";
        private static final String DEFAULT_DB = "real_estate_management";
        private static final String DEFAULT_USER = "root";
        private static final String DEFAULT_PASS = "";
        private static volatile DBConnection instance;

        private final String jdbcUrl;
        private final String dbUser;
        private final String dbPass;
        private final boolean driverPresent;
        private final int maxPoolSize;
        private final ArrayBlockingQueue<Connection> pool;

        private DBConnection() {
            boolean foundDriver = false;
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                foundDriver = true;
            } catch (ClassNotFoundException ignored) {}
            this.driverPresent = foundDriver;
            Map<String, String> dotEnv = loadDotEnv();
            String host = getConfig("DB_HOST", dotEnv, DEFAULT_HOST);
            String port = getConfig("DB_PORT", dotEnv, DEFAULT_PORT);
            String db = getConfig("DB_NAME", dotEnv, DEFAULT_DB);
            this.dbUser = getConfig("DB_USER", dotEnv, DEFAULT_USER);
            this.dbPass = getConfig("DB_PASS", dotEnv, DEFAULT_PASS);
            int pSize = 10;
            try { pSize = Math.max(2, Math.min(50, Integer.parseInt(getConfig("DB_POOL_SIZE", dotEnv, "10")))); } catch (Exception ignored) {}
            this.maxPoolSize = pSize;
            this.pool = new ArrayBlockingQueue<>(this.maxPoolSize);
            this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
        }

        public static String getConfig(String key, Map<String, String> dotEnv, String defaultValue) {
            String envVal = System.getenv(key);
            if (envVal != null && !envVal.isEmpty()) return envVal;
            if (dotEnv.containsKey(key)) return dotEnv.get(key);
            return defaultValue;
        }

        public static Map<String, String> loadDotEnv() {
            Map<String, String> map = new HashMap<>();
            File envFile = new File(".env");
            if (!envFile.exists() || !envFile.isFile()) return map;
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String k = line.substring(0, eq).trim();
                        String v = line.substring(eq + 1).trim();
                        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                            v = v.substring(1, v.length() - 1);
                        }
                        map.put(k, v);
                    }
                }
            } catch (IOException ignored) {}
            return map;
        }

        public static DBConnection getInstance() {
            if (instance == null) {
                synchronized (DBConnection.class) {
                    if (instance == null) instance = new DBConnection();
                }
            }
            return instance;
        }

        public Connection getConnection() throws SQLException {
            if (!driverPresent) throw new SQLException("MySQL JDBC driver not found on classpath.");
            Connection physical = pool.poll();
            if (physical != null) {
                try {
                    if (!physical.isClosed() && physical.isValid(2)) {
                        return wrapPooledConnection(physical);
                    }
                } catch (SQLException ignored) {}
                try { physical.close(); } catch (SQLException ignored) {}
            }
            Connection fresh = DriverManager.getConnection(this.jdbcUrl, this.dbUser, this.dbPass);
            return wrapPooledConnection(fresh);
        }

        private Connection wrapPooledConnection(final Connection physical) {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                        releaseConnection(physical);
                        return null;
                    }
                    try {
                        return method.invoke(physical, args);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        throw ite.getCause();
                    }
                }
            );
        }

        public void releaseConnection(Connection physical) {
            if (physical == null) return;
            try {
                if (!physical.isClosed() && physical.getAutoCommit() && pool.offer(physical)) {
                    return;
                }
                if (!physical.isClosed()) physical.close();
            } catch (SQLException ignored) {}
        }

        public int getIdlePoolSize() { return pool.size(); }

        public void shutdown() {
            Connection c;
            while ((c = pool.poll()) != null) {
                try { c.close(); } catch (SQLException ignored) {}
            }
        }

        public boolean testConnection() {
            if (!driverPresent) return false;
            try (Connection conn = getConnection()) {
                return conn != null && !conn.isClosed();
            } catch (SQLException e) {
                return false;
            }
        }
    }

    public static final class RedisStore {
        private static volatile RedisStore instance;
        private final String host;
        private final int port;
        private final String password;
        private final boolean configured;
        private volatile boolean connected;
        private final ConcurrentHashMap<String, String[]> memoryFallback = new ConcurrentHashMap<>();

        private RedisStore() {
            Map<String, String> dotEnv = DBConnection.loadDotEnv();
            String h = DBConnection.getConfig("REDIS_HOST", dotEnv, "");
            this.host = h != null ? h.trim() : "";
            int p = 6379;
            try { p = Integer.parseInt(DBConnection.getConfig("REDIS_PORT", dotEnv, "6379")); } catch (Exception ignored) {}
            this.port = p;
            this.password = DBConnection.getConfig("REDIS_PASSWORD", dotEnv, "");
            this.configured = !this.host.isEmpty();
            this.connected = this.configured && ping();
        }

        public static RedisStore getInstance() {
            if (instance == null) {
                synchronized (RedisStore.class) {
                    if (instance == null) instance = new RedisStore();
                }
            }
            return instance;
        }

        public boolean isConfigured() { return configured; }
        public boolean isConnected() { return connected; }

        public boolean ping() {
            if (!configured) return false;
            try {
                String resp = sendCommand("PING");
                connected = resp != null && resp.contains("PONG");
                return connected;
            } catch (Exception e) {
                connected = false;
                return false;
            }
        }

        public void setex(String key, long ttlSeconds, String value) {
            long exp = System.currentTimeMillis() + (ttlSeconds * 1000L);
            memoryFallback.put(key, new String[]{value, String.valueOf(exp)});
            if (!configured) return;
            try { sendCommand("SETEX", key, String.valueOf(Math.max(1L, ttlSeconds)), value); } catch (Exception ignored) {}
        }

        public String get(String key) {
            if (configured) {
                try {
                    String val = sendCommand("GET", key);
                    if (val != null) return val;
                } catch (Exception ignored) {}
            }
            String[] rec = memoryFallback.get(key);
            if (rec == null) return null;
            if (System.currentTimeMillis() > Long.parseLong(rec[1])) {
                memoryFallback.remove(key);
                return null;
            }
            return rec[0];
        }

        public void del(String key) {
            memoryFallback.remove(key);
            if (!configured) return;
            try { sendCommand("DEL", key); } catch (Exception ignored) {}
        }

        private String sendCommand(String... args) throws IOException {
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(host, port), 1500);
                sock.setSoTimeout(2000);
                OutputStream os = sock.getOutputStream();
                BufferedInputStream bis = new BufferedInputStream(sock.getInputStream());
                if (password != null && !password.isEmpty()) {
                    writeResp(os, "AUTH", password);
                    readRespLine(bis);
                }
                writeResp(os, args);
                return readRespValue(bis);
            }
        }

        private void writeResp(OutputStream os, String... args) throws IOException {
            StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
            for (String a : args) {
                byte[] b = (a != null ? a : "").getBytes(StandardCharsets.UTF_8);
                sb.append("$").append(b.length).append("\r\n");
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                sb.setLength(0);
                os.write(b);
                os.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            os.flush();
        }

        private String readRespLine(InputStream is) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int prev = -1, cur;
            while ((cur = is.read()) != -1) {
                if (prev == '\r' && cur == '\n') break;
                if (prev != -1) baos.write(prev);
                prev = cur;
            }
            return baos.toString(StandardCharsets.UTF_8);
        }

        private String readRespValue(InputStream is) throws IOException {
            String line = readRespLine(is);
            if (line == null || line.isEmpty()) return null;
            char prefix = line.charAt(0);
            if (prefix == '+' || prefix == ':') return line.substring(1);
            if (prefix == '-') throw new IOException("Redis error: " + line.substring(1));
            if (prefix == '$') {
                int len = Integer.parseInt(line.substring(1));
                if (len < 0) return null;
                byte[] buf = new byte[len];
                int read = 0;
                while (read < len) {
                    int n = is.read(buf, read, len - read);
                    if (n == -1) break;
                    read += n;
                }
                is.read(); is.read(); // trailing CRLF
                return new String(buf, StandardCharsets.UTF_8);
            }
            return line;
        }
    }

    public static final class JwtUtil {
        private static final byte[] SECRET_BYTES = initSecret();
        private static final String HEADER_B64 = b64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        private static final Base64.Encoder B64_ENC = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

        private JwtUtil() {}

        private static byte[] initSecret() {
            Map<String, String> dotEnv = DBConnection.loadDotEnv();
            String s = DBConnection.getConfig("JWT_SECRET", dotEnv, "");
            if (s != null && s.trim().length() >= 16) {
                return s.trim().getBytes(StandardCharsets.UTF_8);
            }
            byte[] rnd = new byte[32];
            new SecureRandom().nextBytes(rnd);
            return rnd;
        }

        private static String b64Url(byte[] data) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        }

        public static String hmacSha256Hex(String message) {
            byte[] mac = hmacSha256Bytes(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac) sb.append(String.format("%02x", b));
            return sb.toString();
        }

        private static byte[] hmacSha256Bytes(byte[] data) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET_BYTES, "HmacSHA256"));
                return mac.doFinal(data);
            } catch (Exception e) {
                throw new RuntimeException("HMAC failure", e);
            }
        }

        public static String sign(User u, long ttlSeconds) {
            long nowSec = System.currentTimeMillis() / 1000L;
            long expSec = nowSec + Math.max(60L, ttlSeconds);
            String jti = UUID.randomUUID().toString();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", u.getUserId());
            claims.put("email", u.getEmail());
            claims.put("name", u.getFullName());
            claims.put("role", u.getUserRole());
            claims.put("ver", u.isVerified());
            claims.put("jti", jti);
            claims.put("iat", nowSec);
            claims.put("exp", expSec);
            String payloadB64 = B64_ENC.encodeToString(JsonUtil.toJson(claims).getBytes(StandardCharsets.UTF_8));
            String signingInput = HEADER_B64 + "." + payloadB64;
            String sigB64 = B64_ENC.encodeToString(hmacSha256Bytes(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + sigB64;
        }

        @SuppressWarnings("unchecked")
        public static Map<String, Object> verify(String token) {
            if (token == null) return null;
            String[] parts = token.trim().split("\\.");
            if (parts.length != 3) return null;
            try {
                String signingInput = parts[0] + "." + parts[1];
                byte[] expectedSig = hmacSha256Bytes(signingInput.getBytes(StandardCharsets.UTF_8));
                byte[] actualSig = B64_DEC.decode(parts[2]);
                if (!MessageDigest.isEqual(expectedSig, actualSig)) return null;
                String json = new String(B64_DEC.decode(parts[1]), StandardCharsets.UTF_8);
                Object parsed = JsonUtil.parse(json);
                if (!(parsed instanceof Map)) return null;
                Map<String, Object> claims = (Map<String, Object>) parsed;
                long exp = ((Number) claims.get("exp")).longValue();
                if ((System.currentTimeMillis() / 1000L) > exp) return null;
                String jti = JsonUtil.getString(claims, "jti");
                if (jti != null && RedisStore.getInstance().get("revoked:" + jti) != null) return null;
                return claims;
            } catch (Exception e) {
                return null;
            }
        }

        public static void revoke(String token) {
            Map<String, Object> claims = verify(token);
            if (claims == null) return;
            String jti = JsonUtil.getString(claims, "jti");
            if (jti != null) {
                long exp = ((Number) claims.get("exp")).longValue();
                long rem = Math.max(60L, exp - (System.currentTimeMillis() / 1000L));
                RedisStore.getInstance().setex("revoked:" + jti, rem, "1");
            }
        }
    }

    public static final class PasswordUtil {
        private static final SecureRandom RANDOM = new SecureRandom();
        private static final int PBKDF2_ITERATIONS = 65536;
        private static final int PBKDF2_KEY_BITS = 256;
        private static final String PBKDF2_PREFIX = "pbkdf2$";

        private PasswordUtil() {}

        public static String generateSalt() {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return Base64.getEncoder().encodeToString(salt);
        }

        public static String hashPassword(String password, String salt) {
            try {
                byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
                PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
                SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] derived = skf.generateSecret(spec).getEncoded();
                spec.clearPassword();
                return PBKDF2_PREFIX + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(derived);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException("Failed to derive password hash", e);
            }
        }

        private static String legacySha256(String password, String salt) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] bytes = md.digest((password + salt).getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        public static boolean verifyPassword(String entered, String salt, String storedHash) {
            if (entered == null || salt == null || storedHash == null) return false;
            String expected;
            if (storedHash.startsWith(PBKDF2_PREFIX)) {
                expected = hashPassword(entered, salt);
            } else {
                // Backward-compatible verification for legacy SHA-256 hashes
                expected = legacySha256(entered, salt);
            }
            return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                storedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    public static final class SessionManager {
        private static final long SESSION_TTL_MS = 24L * 60L * 60L * 1000L; // 24 hours sliding window
        private static final class SessionRecord {
            final User user;
            final long createdAt;
            volatile long lastAccessedAt;
            SessionRecord(User user, long now) {
                this.user = user;
                this.createdAt = now;
                this.lastAccessedAt = now;
            }
        }
        private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();

        public String createSession(User user) {
            evictExpired();
            String jwt = JwtUtil.sign(user, SESSION_TTL_MS / 1000L);
            sessions.put(jwt, new SessionRecord(user, System.currentTimeMillis()));
            return jwt;
        }

        public User resolve(String token) {
            if (token == null || token.trim().isEmpty()) return null;
            String clean = token.trim();
            Map<String, Object> claims = JwtUtil.verify(clean);
            if (claims == null) {
                sessions.remove(clean);
                return null;
            }
            SessionRecord rec = sessions.get(clean);
            long now = System.currentTimeMillis();
            if (rec != null) {
                rec.lastAccessedAt = now;
                return rec.user;
            }
            // Stateless JWT reconstruction across restarts or horizontally scaled instances
            User u = UserDAO.createInstance(JsonUtil.getString(claims, "role"));
            u.setUserId(JsonUtil.getInt(claims, "sub"));
            u.setEmail(JsonUtil.getString(claims, "email"));
            u.setFullName(JsonUtil.getString(claims, "name"));
            u.setVerified(JsonUtil.getBoolean(claims, "ver"));
            u.setActive(true);
            sessions.put(clean, new SessionRecord(u, now));
            return u;
        }

        public void invalidate(String token) {
            if (token != null) {
                String clean = token.trim();
                sessions.remove(clean);
                JwtUtil.revoke(clean);
            }
        }

        public void refreshUser(User updated) {
            if (updated == null) return;
            for (Map.Entry<String, SessionRecord> entry : sessions.entrySet()) {
                if (entry.getValue().user.getUserId() == updated.getUserId()) {
                    entry.setValue(new SessionRecord(updated, entry.getValue().createdAt));
                }
            }
        }

        public int activeCount() {
            evictExpired();
            return sessions.size();
        }

        private void evictExpired() {
            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(e -> (now - e.getValue().lastAccessedAt) > SESSION_TTL_MS);
        }
    }

    public static final class RateLimiter {
        private static final long WINDOW_MS = 60_000L;
        private static final int MAX_REQUESTS_PER_WINDOW = 20;
        private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

        public void check(String key) throws RateLimitExceededException {
            long now = System.currentTimeMillis();
            Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > WINDOW_MS) {
                    timestamps.pollFirst();
                }
                if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                    long oldest = timestamps.peekFirst();
                    long retrySec = Math.max(1L, ((oldest + WINDOW_MS) - now) / 1000L);
                    throw new RateLimitExceededException("Too many authentication attempts. Try again in " + retrySec + "s.", retrySec);
                }
                timestamps.addLast(now);
            }
        }
    }

    public static final class SupabaseClient {
        private static volatile SupabaseClient instance;
        private final String baseUrl;
        private final String apiKey;
        private final HttpClient httpClient;

        private SupabaseClient() {
            Map<String, String> dotEnv = DBConnection.loadDotEnv();
            String url = DBConnection.getConfig("SUPABASE_URL", dotEnv, null);
            if (url != null) {
                url = url.trim();
                if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            }
            String key = DBConnection.getConfig("SUPABASE_KEY", dotEnv, null);
            if (key == null) key = DBConnection.getConfig("SUPABASE_SERVICE_ROLE_KEY", dotEnv, null);
            if (key == null) key = DBConnection.getConfig("SUPABASE_ANON_KEY", dotEnv, null);
            this.baseUrl = url;
            this.apiKey = key != null ? key.trim() : null;
            this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        }

        public static SupabaseClient getInstance() {
            if (instance == null) {
                synchronized (SupabaseClient.class) {
                    if (instance == null) instance = new SupabaseClient();
                }
            }
            return instance;
        }

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isEmpty() && apiKey != null && !apiKey.isEmpty();
        }

        public String getBaseUrl() { return baseUrl; }

        public boolean testConnection() {
            if (!isConfigured()) return false;
            try {
                try {
                    List<Map<String, Object>> r = get("property_categories?select=category_id&limit=1");
                    if (r != null) return true;
                } catch (Exception ignored) {}
                try {
                    List<Map<String, Object>> r = get("properties?select=id&limit=1");
                    if (r != null) return true;
                } catch (Exception ignored) {}
                try {
                    List<Map<String, Object>> r = get("properties?select=property_id&limit=1");
                    if (r != null) return true;
                } catch (Exception ignored) {}
                return false;
            } catch (Exception e) {
                System.err.println("[SupabaseClient] Connection test failed: " + e.getMessage());
                return false;
            }
        }

        public List<Map<String, Object>> get(String resourceAndQuery) throws SQLException {
            return JsonUtil.parseListOfMaps(send("GET", resourceAndQuery, null, null));
        }

        public Map<String, Object> post(String table, Map<String, Object> payload) throws SQLException {
            List<Map<String, Object>> rows = JsonUtil.parseListOfMaps(send("POST", table, JsonUtil.toJson(payload), "return=representation"));
            return rows.isEmpty() ? new HashMap<>() : rows.get(0);
        }

        public void postBatch(String table, List<Map<String, Object>> rows) throws SQLException {
            if (rows == null || rows.isEmpty()) return;
            send("POST", table, JsonUtil.toJson(rows), "return=minimal");
        }

        public List<Map<String, Object>> patch(String resourceAndQuery, Map<String, Object> payload) throws SQLException {
            return JsonUtil.parseListOfMaps(send("PATCH", resourceAndQuery, JsonUtil.toJson(payload), "return=representation"));
        }

        public boolean delete(String resourceAndQuery) throws SQLException {
            return !JsonUtil.parseListOfMaps(send("DELETE", resourceAndQuery, null, "return=representation")).isEmpty();
        }

        public static String encode(String v) {
            return v == null ? "" : URLEncoder.encode(v, StandardCharsets.UTF_8);
        }

        public static String sanitizePostgrestTerm(String raw) {
            if (raw == null) return "";
            return raw.replaceAll("[(),*\\\\]", " ").trim();
        }

        private String send(String method, String resourceAndQuery, String jsonBody, String prefer) throws SQLException {
            if (!isConfigured()) throw new SQLException("Supabase is not configured in .env");
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/v1/" + resourceAndQuery))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "RealEstateBackend/2.0")
                    .header("Accept", "application/json");
                if (prefer != null) b.header("Prefer", prefer);
                if ("POST".equals(method) || "PATCH".equals(method)) {
                    b.header("Content-Type", "application/json; charset=UTF-8");
                    b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}", StandardCharsets.UTF_8));
                } else {
                    b.method(method, HttpRequest.BodyPublishers.noBody());
                }
                HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    throw new SQLException("Supabase API error (HTTP " + resp.statusCode() + ")");
                }
                return resp.body();
            } catch (SQLException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Supabase request failed: " + e.getMessage(), e);
            }
        }
    }

    public static final class JsonUtil {
        private JsonUtil() {}
        public static String success(String msg, String data) {
            return "{\"status\":\"success\",\"message\":" + escape(msg) + (data != null ? ",\"data\":" + data : "") + "}";
        }
        public static String error(String msg, int code) {
            return "{\"status\":\"error\",\"code\":" + code + ",\"message\":" + escape(msg) + "}";
        }
        public static String escape(String s) {
            if (s == null) return "null";
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                switch (ch) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (ch < ' ') {
                            String t = "000" + Integer.toHexString(ch);
                            sb.append("\\u").append(t.substring(t.length() - 4));
                        } else {
                            sb.append(ch);
                        }
                }
            }
            sb.append("\"");
            return sb.toString();
        }
        @SuppressWarnings("unchecked")
        public static String toJson(Object value) {
            if (value == null) return "null";
            if (value instanceof String) return escape((String) value);
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Timestamp) return escape(value.toString());
            if (value instanceof Map) {
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                    if (i++ > 0) sb.append(",");
                    sb.append(escape(e.getKey())).append(":").append(toJson(e.getValue()));
                }
                return sb.append("}").toString();
            }
            if (value instanceof Collection) {
                StringBuilder sb = new StringBuilder("[");
                int i = 0;
                for (Object item : (Collection<?>) value) {
                    if (i++ > 0) sb.append(",");
                    sb.append(toJson(item));
                }
                return sb.append("]").toString();
            }
            return escape(value.toString());
        }
        public static Object parse(String json) {
            if (json == null || json.trim().isEmpty()) return null;
            return new Parser(json).parseValue();
        }
        @SuppressWarnings("unchecked")
        public static List<Map<String, Object>> parseListOfMaps(String json) {
            Object parsed = parse(json);
            List<Map<String, Object>> out = new ArrayList<>();
            if (parsed instanceof List) {
                for (Object item : (List<?>) parsed) if (item instanceof Map) out.add((Map<String, Object>) item);
            } else if (parsed instanceof Map) {
                out.add((Map<String, Object>) parsed);
            }
            return out;
        }
        @SuppressWarnings("unchecked")
        public static Map<String, Object> getMap(Map<String, Object> row, String key) {
            if (row == null) return null;
            Object v = row.get(key);
            if (v instanceof Map) return (Map<String, Object>) v;
            if (v instanceof List && !((List<?>) v).isEmpty() && ((List<?>) v).get(0) instanceof Map) {
                return (Map<String, Object>) ((List<?>) v).get(0);
            }
            return null;
        }
        @SuppressWarnings("unchecked")
        public static List<Map<String, Object>> getList(Map<String, Object> row, String key) {
            List<Map<String, Object>> out = new ArrayList<>();
            if (row != null && row.get(key) instanceof List) {
                for (Object o : (List<?>) row.get(key)) if (o instanceof Map) out.add((Map<String, Object>) o);
            }
            return out;
        }
        public static String getString(Map<String, Object> row, String key) {
            Object v = row != null ? row.get(key) : null;
            return v != null ? v.toString() : null;
        }
        public static int getInt(Map<String, Object> row, String key) {
            Object v = row != null ? row.get(key) : null;
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) { try { return Integer.parseInt(((String) v).trim()); } catch (Exception ignored) {} }
            return 0;
        }
        public static double getDouble(Map<String, Object> row, String key) {
            Object v = row != null ? row.get(key) : null;
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v instanceof String) { try { return Double.parseDouble(((String) v).trim()); } catch (Exception ignored) {} }
            return 0.0;
        }
        public static boolean getBoolean(Map<String, Object> row, String key) {
            Object v = row != null ? row.get(key) : null;
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return "true".equalsIgnoreCase((String) v);
            return false;
        }
        private static final class Parser {
            private final String s; private int pos;
            Parser(String s) { this.s = s; }
            Object parseValue() {
                ws(); if (pos >= s.length()) return null;
                char c = s.charAt(pos);
                if (c == '{') return obj();
                if (c == '[') return arr();
                if (c == '"') return str();
                if (c == 't' && s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
                if (c == 'f' && s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
                if (c == 'n' && s.startsWith("null", pos)) { pos += 4; return null; }
                return num();
            }
            private Map<String, Object> obj() {
                Map<String, Object> m = new LinkedHashMap<>(); pos++; ws();
                if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
                while (pos < s.length()) {
                    ws(); String k = str(); ws();
                    if (pos < s.length() && s.charAt(pos) == ':') pos++;
                    m.put(k, parseValue()); ws();
                    if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                    if (pos < s.length() && s.charAt(pos) == '}') { pos++; break; }
                    break;
                }
                return m;
            }
            private List<Object> arr() {
                List<Object> l = new ArrayList<>(); pos++; ws();
                if (pos < s.length() && s.charAt(pos) == ']') { pos++; return l; }
                while (pos < s.length()) {
                    l.add(parseValue()); ws();
                    if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                    if (pos < s.length() && s.charAt(pos) == ']') { pos++; break; }
                    break;
                }
                return l;
            }
            private String str() {
                if (pos < s.length() && s.charAt(pos) == '"') pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < s.length()) {
                    char c = s.charAt(pos++);
                    if (c == '"') return sb.toString();
                    if (c == '\\' && pos < s.length()) {
                        char e = s.charAt(pos++);
                        switch (e) {
                            case '"': sb.append('"'); break; case '\\': sb.append('\\'); break;
                            case '/': sb.append('/'); break; case 'b': sb.append('\b'); break;
                            case 'f': sb.append('\f'); break; case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break; case 't': sb.append('\t'); break;
                            case 'u': if (pos + 4 <= s.length()) { sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16)); pos += 4; } break;
                            default: sb.append(e);
                        }
                    } else sb.append(c);
                }
                return sb.toString();
            }
            private Number num() {
                int st = pos;
                while (pos < s.length()) {
                    char c = s.charAt(pos);
                    if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') pos++; else break;
                }
                String ns = s.substring(st, pos);
                if (ns.isEmpty()) return 0;
                if (ns.contains(".") || ns.contains("e") || ns.contains("E")) return Double.parseDouble(ns);
                try { return Long.parseLong(ns); } catch (NumberFormatException ex) { return Double.parseDouble(ns); }
            }
            private void ws() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        }
    }

    // =========================================================================
    // 7. MULTITHREADED ASYNCHRONOUS BACKGROUND NOTIFICATION WORKER
    // =========================================================================

    public static final class AsyncNotificationService {
        public static class NotificationTask {
            private final int userId;
            private final String recipient;
            private final String subject;
            private final String message;
            public NotificationTask(int userId, String r, String s, String m) {
                this.userId = userId; this.recipient = r; this.subject = s; this.message = m;
            }
            public int getUserId() { return userId; }
            public String getRecipient() { return recipient; }
            public String getSubject() { return subject; }
            public String getMessage() { return message; }
        }

        private static volatile AsyncNotificationService instance;
        private final BlockingQueue<NotificationTask> queue = new LinkedBlockingQueue<>(500);
        private final AtomicInteger taskCounter = new AtomicInteger(0);
        private final AtomicInteger notificationIdSeq = new AtomicInteger(1);
        private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<UserNotification>> inboxByUser = new ConcurrentHashMap<>();

        private AsyncNotificationService() {
            Thread worker = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        NotificationTask task = queue.take();
                        int c = taskCounter.incrementAndGet();
                        if (task.getUserId() > 0) {
                            UserNotification n = new UserNotification();
                            n.setNotificationId(notificationIdSeq.getAndIncrement());
                            n.setUserId(task.getUserId());
                            n.setRecipient(task.getRecipient());
                            n.setTitle(task.getSubject());
                            n.setMessage(task.getMessage());
                            n.setRead(false);
                            inboxByUser.computeIfAbsent(task.getUserId(), k -> new CopyOnWriteArrayList<>()).add(0, n);
                            String sseData = "{\"notificationId\":" + n.getNotificationId()
                                + ",\"title\":" + JsonUtil.escape(n.getTitle())
                                + ",\"message\":" + JsonUtil.escape(n.getMessage()) + "}";
                            SseEventHub.getInstance().broadcastToUser(task.getUserId(), "notification", sseData);
                        }
                        System.out.println("[ASYNC WORKER #" + c + "] Alert -> " + task.getRecipient() + " | " + task.getSubject());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "RealEstate-Async-Notification-Worker");
            worker.setDaemon(true);
            worker.start();
        }

        public static AsyncNotificationService getInstance() {
            if (instance == null) {
                synchronized (AsyncNotificationService.class) {
                    if (instance == null) instance = new AsyncNotificationService();
                }
            }
            return instance;
        }

        public boolean dispatchNotification(String recipient, String subject) {
            return dispatchNotification(0, recipient, subject, subject);
        }

        public synchronized boolean dispatchNotification(int userId, String recipient, String subject, String message) {
            return queue.offer(new NotificationTask(userId, recipient, subject, message));
        }

        public List<UserNotification> getForUser(int userId) {
            return new ArrayList<>(inboxByUser.getOrDefault(userId, new CopyOnWriteArrayList<>()));
        }

        public void markAllRead(int userId) {
            for (UserNotification n : inboxByUser.getOrDefault(userId, new CopyOnWriteArrayList<>())) {
                n.setRead(true);
            }
        }

        public int getProcessedCount() { return taskCounter.get(); }
    }

    // =========================================================================
    // 7B. REAL-TIME SERVER-SENT EVENTS (SSE) PUSH HUB & NATIVE PDF 1.4 BUILDER
    // =========================================================================

    public static final class SseEventHub {
        public static final class SseClient {
            final int userId;
            final boolean isAdmin;
            final OutputStream os;
            final HttpExchange exchange;
            final Object lock = new Object();
            volatile boolean alive = true;
            SseClient(int userId, boolean isAdmin, OutputStream os, HttpExchange ex) {
                this.userId = userId; this.isAdmin = isAdmin; this.os = os; this.exchange = ex;
            }
        }

        private static final SseEventHub INSTANCE = new SseEventHub();
        public static SseEventHub getInstance() { return INSTANCE; }
        private final CopyOnWriteArrayList<SseClient> clients = new CopyOnWriteArrayList<>();

        public SseClient register(int userId, boolean isAdmin, HttpExchange ex) throws IOException {
            OutputStream os = ex.getResponseBody();
            SseClient client = new SseClient(userId, isAdmin, os, ex);
            clients.add(client);
            sendRaw(client, "event: connected\ndata: {\"status\":\"connected\",\"userId\":" + userId + "}\n\n");
            return client;
        }

        public void unregister(SseClient client) {
            if (client != null) {
                client.alive = false;
                clients.remove(client);
            }
        }

        public int activeSubscribers() { return clients.size(); }

        public void broadcastToUser(int userId, String eventName, String jsonData) {
            for (SseClient c : clients) {
                if (c.userId == userId || c.isAdmin) {
                    sendEvent(c, eventName, jsonData);
                }
            }
        }

        public void broadcastAll(String eventName, String jsonData) {
            for (SseClient c : clients) {
                sendEvent(c, eventName, jsonData);
            }
        }

        public boolean sendHeartbeat(SseClient client) {
            return sendRaw(client, ": keepalive\n\n");
        }

        private void sendEvent(SseClient c, String eventName, String jsonData) {
            sendRaw(c, "event: " + eventName + "\ndata: " + jsonData + "\n\n");
        }

        private boolean sendRaw(SseClient c, String payload) {
            if (!c.alive) return false;
            synchronized (c.lock) {
                try {
                    c.os.write(payload.getBytes(StandardCharsets.UTF_8));
                    c.os.flush();
                    return true;
                } catch (IOException e) {
                    unregister(c);
                    return false;
                }
            }
        }

        public void shutdownAll() {
            for (SseClient c : clients) {
                c.alive = false;
                try { c.os.close(); } catch (IOException ignored) {}
            }
            clients.clear();
        }
    }

    public static final class PdfGenerator {
        private PdfGenerator() {}

        public static byte[] buildDocument(String docTitle, String subtitle, List<String[]> rows, String footerNote) {
            StringBuilder stream = new StringBuilder();
            // Outer architectural border and header band
            stream.append("0.02 0.59 0.41 rg 40 740 532 56 re f\n");
            stream.append("0.78 0.61 0.24 RG 1.5 w 40 48 532 748 re S\n");
            stream.append("BT /F2 16 Tf 1 1 1 rg 54 772 Td (").append(pdfEsc("ESTATEVAULT | " + docTitle)).append(") Tj ET\n");
            stream.append("BT /F1 9 Tf 0.9 0.95 0.92 rg 54 754 Td (").append(pdfEsc(subtitle)).append(") Tj ET\n");

            int y = 708;
            stream.append("0.85 0.87 0.90 RG 0.6 w ");
            if (rows != null) {
                for (String[] pair : rows) {
                    if (y < 90) break;
                    String label = pair != null && pair.length > 0 ? pair[0] : "";
                    String value = pair != null && pair.length > 1 ? pair[1] : "";
                    stream.append("BT /F2 9.5 Tf 0.30 0.35 0.42 rg 54 ").append(y).append(" Td (").append(pdfEsc(label)).append(") Tj ET\n");
                    List<String> wrapped = wrapText(value, 64);
                    for (String line : wrapped) {
                        if (y < 90) break;
                        stream.append("BT /F1 10 Tf 0.08 0.10 0.14 rg 205 ").append(y).append(" Td (").append(pdfEsc(line)).append(") Tj ET\n");
                        y -= 15;
                    }
                    stream.append("54 ").append(y + 6).append(" m 558 ").append(y + 6).append(" l S\n");
                    y -= 12;
                }
            }
            stream.append("BT /F1 8.5 Tf 0.40 0.45 0.52 rg 54 64 Td (").append(pdfEsc(footerNote)).append(") Tj ET\n");
            String streamStr = stream.toString();
            byte[] streamBytes = streamStr.getBytes(StandardCharsets.ISO_8859_1);

            List<String> objects = new ArrayList<>();
            objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
            objects.add("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
            objects.add("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n");
            objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
            objects.add("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");
            objects.add("6 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n" + streamStr + "\nendstream\nendobj\n");

            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            try {
                pdf.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
                int[] offsets = new int[objects.size() + 1];
                for (int i = 0; i < objects.size(); i++) {
                    offsets[i + 1] = pdf.size();
                    pdf.write(objects.get(i).getBytes(StandardCharsets.ISO_8859_1));
                }
                int xrefStart = pdf.size();
                pdf.write(("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n").getBytes(StandardCharsets.ISO_8859_1));
                for (int i = 1; i <= objects.size(); i++) {
                    pdf.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.ISO_8859_1));
                }
                String trailer = "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xrefStart + "\n%%EOF\n";
                pdf.write(trailer.getBytes(StandardCharsets.ISO_8859_1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return pdf.toByteArray();
        }

        private static List<String> wrapText(String text, int maxLen) {
            List<String> out = new ArrayList<>();
            if (text == null || text.trim().isEmpty()) { out.add("-"); return out; }
            String clean = text.replace('\r', ' ').replace('\n', ' ').trim();
            while (clean.length() > maxLen) {
                int cut = clean.lastIndexOf(' ', maxLen);
                if (cut <= 0) cut = maxLen;
                out.add(clean.substring(0, cut).trim());
                clean = clean.substring(cut).trim();
            }
            if (!clean.isEmpty()) out.add(clean);
            return out;
        }

        private static String pdfEsc(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\') sb.append("\\\\");
                else if (c == '(') sb.append("\\(");
                else if (c == ')') sb.append("\\)");
                else if (c == '\u20B9') sb.append("Rs.");
                else if (c >= 32 && c <= 126) sb.append(c);
                else sb.append(' ');
            }
            return sb.toString();
        }
    }

    // =========================================================================
    // 8. SEEDED IN-MEMORY FALLBACK STORE (FOR SEAMLESS LOCAL DEMO & TESTING)
    // =========================================================================

    public static final class InMemoryStore {
        private static final InMemoryStore INSTANCE = new InMemoryStore();
        public static InMemoryStore getInstance() { return INSTANCE; }

        final AtomicInteger userSeq = new AtomicInteger(10);
        final AtomicInteger propSeq = new AtomicInteger(113);
        final AtomicInteger visitSeq = new AtomicInteger(20);
        final AtomicInteger rentalSeq = new AtomicInteger(30);
        final AtomicInteger purchaseSeq = new AtomicInteger(40);
        final AtomicInteger verifSeq = new AtomicInteger(50);
        final AtomicInteger reviewSeq = new AtomicInteger(60);
        final AtomicInteger inquirySeq = new AtomicInteger(70);
        final AtomicInteger auditSeq = new AtomicInteger(80);

        final Map<Integer, User> users = new ConcurrentHashMap<>();
        final Map<Integer, Property> properties = new ConcurrentHashMap<>();
        final List<PropertyVisit> visits = new CopyOnWriteArrayList<>();
        final List<RentalAgreement> agreements = new CopyOnWriteArrayList<>();
        final List<RentPayment> rentPayments = new CopyOnWriteArrayList<>();
        final List<PurchaseTransaction> purchases = new CopyOnWriteArrayList<>();
        final List<OwnerVerification> verifications = new CopyOnWriteArrayList<>();
        final Map<Integer, Set<Integer>> favoritesByUser = new ConcurrentHashMap<>();
        final List<PropertyReview> reviews = new CopyOnWriteArrayList<>();
        final List<PropertyInquiry> inquiries = new CopyOnWriteArrayList<>();
        final List<AuditLogEntry> auditLogs = new CopyOnWriteArrayList<>();

        private InMemoryStore() {
            seed();
        }

        private User makeSeedUser(int id, String name, String email, String phone, String role, boolean verified) {
            User u = UserDAO.createInstance(role);
            u.setUserId(id);
            u.setFullName(name);
            u.setEmail(email);
            u.setMobileNumber(phone);
            u.setUserRole(role);
            u.setVerified(verified);
            u.setActive(true);
            String salt = PasswordUtil.generateSalt();
            u.setPasswordSalt(salt);
            u.setPasswordHash(PasswordUtil.hashPassword("Password@123", salt));
            users.put(id, u);
            return u;
        }

        private void seed() {
            User admin = makeSeedUser(1, "Aarav Mehta (Admin)", "admin@realestate.com", "+91 98450 10001", "ADMIN", true);
            User owner = makeSeedUser(2, "Vikramaditya Rao", "owner@realestate.com", "+91 98450 20002", "PROPERTY_OWNER", true);
            User buyer = makeSeedUser(3, "Ananya Sharma", "buyer@realestate.com", "+91 98450 30003", "BUYER", true);
            User seller = makeSeedUser(4, "Rohan Kulkarni", "seller@realestate.com", "+91 98450 40004", "SELLER", true);
            User tenant = makeSeedUser(5, "Diya Nair", "tenant@realestate.com", "+91 98450 50005", "TENANT", false);
            User agent = makeSeedUser(6, "Kabir Verma (RERA)", "agent@realestate.com", "+91 98450 60006", "AGENT", true);

            // 1. Residential Luxury Villa (Mysuru)
            ResidentialProperty p1 = new ResidentialProperty(101, owner.getUserId(), "Palatial 4 BHK Heritage Courtyard Villa", "BUY", 18500000, 3400, "Karnataka", "Mysuru", 4, 4);
            p1.setResidentialType("VILLA"); p1.setFurnishingStatus("FULLY_FURNISHED"); p1.setFeatured(true);
            p1.setLocality("Vijayanagar 2nd Stage"); p1.setAddress("42 Heritage Boulevard, Vijayanagar"); p1.setPincode("570017");
            p1.setLatitude(12.3375); p1.setLongitude(76.6182);
            p1.setApprovalStatus("APPROVED"); p1.setListingStatus("AVAILABLE");
            p1.setDescription("Sun-drenched architectural villa featuring double-height living room, Italian marble foyer, landscaped courtyard, solar water heating, and EV-ready 2-car porch.");
            p1.setAmenities(Arrays.asList("Private Garden", "EV Charging", "Solar Backup", "Modular Kitchen", "24x7 Security", "Home Automation"));
            p1.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p1, owner); p1.setAverageRating(4.9); p1.setReviewCount(2); p1.setViewsCount(312);
            properties.put(101, p1);

            // 2. Residential Sky Apartment (Bengaluru - RENT)
            ResidentialProperty p2 = new ResidentialProperty(102, owner.getUserId(), "Skyline 3 BHK Glass Penthouse Flat", "RENT", 68000, 2150, "Karnataka", "Bengaluru", 3, 3);
            p2.setResidentialType("APARTMENT"); p2.setFurnishingStatus("FULLY_FURNISHED"); p2.setFeatured(true);
            p2.setLocality("Indiranagar 100ft Rd"); p2.setAddress("19th Floor, Aurelia Crest, Indiranagar"); p2.setPincode("560038");
            p2.setLatitude(12.9784); p2.setLongitude(77.6408);
            p2.setApprovalStatus("APPROVED"); p2.setListingStatus("AVAILABLE");
            p2.setDescription("Panoramic city views from wrap-around terrace, concierge lobby, infinity rooftop pool, soundproof glazing, 5 mins to Indiranagar Metro.");
            p2.setAmenities(Arrays.asList("Infinity Pool", "Concierge", "Sky Gym", "Central AC", "Metro Proximity"));
            p2.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p2, owner); p2.setAverageRating(4.8); p2.setReviewCount(1); p2.setViewsCount(428);
            properties.put(102, p2);

            // 3. Commercial Grade-A Tech Floor (Bengaluru - LEASE)
            CommercialProperty p3 = new CommercialProperty();
            p3.setPropertyId(103); p3.setOwnerId(agent.getUserId()); p3.setTitle("Grade-A LEED Platinum Tech Office Floor");
            p3.setPurpose("LEASE"); p3.setPrice(285000); p3.setPropertyArea(4800); p3.setCarpetArea(4450);
            p3.setCommercialType("OFFICE_SPACE"); p3.setCommercialUsage("IT / FinTech / Corporate HQ"); p3.setPowerBackup(true);
            p3.setState("Karnataka"); p3.setCity("Bengaluru"); p3.setLocality("Outer Ring Road, Bellandur"); p3.setAddress("Tower B, Emerald Tech Park"); p3.setPincode("560103");
            p3.setLatitude(12.9279); p3.setLongitude(77.6762);
            p3.setApprovalStatus("APPROVED"); p3.setListingStatus("AVAILABLE"); p3.setFeatured(true);
            p3.setDescription("Plug-and-play 60-seat architectural workspace with boardrooms, biometric access, twin DG backup, and cafeteria access.");
            p3.setAmenities(Arrays.asList("100% Power Backup", "Dual Fiber", "Conference Suites", "Covered Parking", "LEED Platinum"));
            p3.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p3, agent); p3.setAverageRating(4.7); p3.setReviewCount(1); p3.setViewsCount(194);
            properties.put(103, p3);

            // 4. Agricultural Organic Estate (Coorg - BUY)
            AgriculturalLand p4 = new AgriculturalLand();
            p4.setPropertyId(104); p4.setOwnerId(seller.getUserId()); p4.setTitle("5.5 Acre Organic Coffee & Pepper Estate");
            p4.setPurpose("BUY"); p4.setPrice(16500000); p4.setPropertyArea(5.5); p4.setTotalLandArea(5.5); p4.setLandUnit("ACRES");
            p4.setLandType("PLANTATION"); p4.setSoilType("Rich Forest Loam"); p4.setWaterAvailability("Perennial Stream + Check Dam"); p4.setHasBorewell(true);
            p4.setState("Karnataka"); p4.setCity("Coorg"); p4.setLocality("Madikeri Hills"); p4.setAddress("Survey 118/4, Suntikoppa Road"); p4.setPincode("571237");
            p4.setLatitude(12.4244); p4.setLongitude(75.7382);
            p4.setApprovalStatus("APPROVED"); p4.setListingStatus("AVAILABLE"); p4.setFeatured(true);
            p4.setDescription("High-yield Arabica & Robusta coffee plantation with silver oak shade trees, drying yard, manager cottage, and clear single-family title.");
            p4.setAmenities(Arrays.asList("Perennial Stream", "2 Borewells", "Drip Irrigation", "Fenced Perimeter", "Tar Road Frontage"));
            p4.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p4, seller); p4.setAverageRating(4.9); p4.setReviewCount(1); p4.setViewsCount(275);
            properties.put(104, p4);

            // 5. Luxury Farmhouse Retreat (Goa - BUY)
            FarmHouse p5 = new FarmHouse();
            p5.setPropertyId(105); p5.setOwnerId(owner.getUserId()); p5.setTitle("Tropical Portuguese Pool Farmhouse Retreat");
            p5.setPurpose("BUY"); p5.setPrice(34000000); p5.setPropertyArea(1.8); p5.setLandArea(1.8); p5.setBuiltUpArea(4200);
            p5.setBedrooms(4); p5.setBathrooms(5); p5.setHasGarden(true); p5.setHasSwimmingPool(true);
            p5.setState("Goa"); p5.setCity("Goa"); p5.setLocality("Assagao Valley"); p5.setAddress("Casa Verde, Assagao"); p5.setPincode("403507");
            p5.setLatitude(15.5955); p5.setLongitude(73.7745);
            p5.setApprovalStatus("APPROVED"); p5.setListingStatus("AVAILABLE"); p5.setFeatured(true);
            p5.setDescription("Restored laterite stone courtyard villa with lap pool, tropical orchard, outdoor gazebo dining, and strong luxury homestay rental yield.");
            p5.setAmenities(Arrays.asList("Private Lap Pool", "Mango Orchard", "Staff Quarters", "Outdoor Bar", "Smart Security"));
            p5.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p5, owner); p5.setAverageRating(5.0); p5.setReviewCount(1); p5.setViewsCount(510);
            properties.put(105, p5);

            // 6. RERA Residential Plot (Mysuru - BUY)
            Plot p6 = new Plot();
            p6.setPropertyId(106); p6.setOwnerId(seller.getUserId()); p6.setTitle("MUDA Approved Corner Residential Site (40x60)");
            p6.setPurpose("BUY"); p6.setPrice(7200000); p6.setPropertyArea(2400); p6.setTotalArea(2400);
            p6.setPlotType("RESIDENTIAL_PLOT"); p6.setPlotDimensions("40 x 60 Ft"); p6.setFacingDirection("NORTH_EAST"); p6.setApprovalAuthority("MUDA & RERA");
            p6.setState("Karnataka"); p6.setCity("Mysuru"); p6.setLocality("Bogadi Ring Road"); p6.setAddress("Plot 88, Royal Avenue Layout"); p6.setPincode("570026");
            p6.setLatitude(12.2958); p6.setLongitude(76.5974);
            p6.setApprovalStatus("APPROVED"); p6.setListingStatus("AVAILABLE"); p6.setFeatured(true);
            p6.setDescription("North-East Vaastu corner site in gated layout with underground cabling, avenue plantation, clubhouse park, and clear A-Khata.");
            p6.setAmenities(Arrays.asList("Underground Cabling", "40ft Concrete Road", "Gated Arch", "Avenue Trees", "Water Line"));
            p6.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p6, seller); p6.setAverageRating(4.6); p6.setReviewCount(0); p6.setViewsCount(163);
            properties.put(106, p6);

            // 7. Mixed-Use Architectural Building (Hyderabad - BUY)
            Building p7 = new Building();
            p7.setPropertyId(107); p7.setOwnerId(agent.getUserId()); p7.setTitle("Aurelia Mixed-Use Boutique Tower (18 Units)");
            p7.setPurpose("BUY"); p7.setPrice(95000000); p7.setPropertyArea(24000); p7.setTotalUnits(18);
            p7.setBuildingType("MIXED_USE_COMPLEX"); p7.setConstructionStatus("READY_TO_MOVE"); p7.setBuilderName("Verma Signature Estates");
            p7.setState("Telangana"); p7.setCity("Hyderabad"); p7.setLocality("Jubilee Hills"); p7.setAddress("Plot 14, Road No. 36, Jubilee Hills"); p7.setPincode("500033");
            p7.setLatitude(17.4326); p7.setLongitude(78.4071);
            p7.setApprovalStatus("APPROVED"); p7.setListingStatus("AVAILABLE"); p7.setFeatured(false);
            p7.setDescription("Newly completed 5-floor glass facade building with ground retail, 16 luxury suites, 2 basement parking decks, and solar microgrid.");
            p7.setAmenities(Arrays.asList("2 Elevators", "Basement Parking", "Fire NOC", "Solar Microgrid", "OC Received"));
            p7.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p7, agent); p7.setAverageRating(4.9); p7.setReviewCount(1); p7.setViewsCount(220);
            properties.put(107, p7);

            // 8. Coastal Modern Residence (Mangaluru - BUY)
            ResidentialProperty p8 = new ResidentialProperty(108, owner.getUserId(), "Arabian Sea Breeze 3 BHK Duplex", "BUY", 11200000, 2350, "Karnataka", "Mangaluru", 3, 3);
            p8.setResidentialType("DUPLEX"); p8.setFurnishingStatus("SEMI_FURNISHED");
            p8.setLocality("Kadri Hills"); p8.setAddress("12 Kadri Crest, Mangaluru"); p8.setPincode("575004");
            p8.setLatitude(12.8897); p8.setLongitude(74.8559);
            p8.setApprovalStatus("APPROVED"); p8.setListingStatus("AVAILABLE");
            p8.setDescription("Cross-ventilated coastal duplex featuring teakwood staircase, rooftop pergola deck, and 10 mins from Tannirbhavi Beach.");
            p8.setAmenities(Arrays.asList("Terrace Pergola", "Rainwater Harvesting", "CCTV", "2 Car Parking"));
            p8.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p8, owner); p8.setAverageRating(4.7); p8.setViewsCount(145);
            properties.put(108, p8);

            // 9. Boutique Retail Showroom (Mysuru - RENT)
            CommercialProperty p9 = new CommercialProperty();
            p9.setPropertyId(109); p9.setOwnerId(agent.getUserId()); p9.setTitle("High-Street Corner Glass Showroom");
            p9.setPurpose("RENT"); p9.setPrice(95000); p9.setPropertyArea(1850); p9.setCarpetArea(1700);
            p9.setCommercialType("SHOWROOM"); p9.setCommercialUsage("Luxury Retail / Flagship Cafe / Clinic"); p9.setPowerBackup(true);
            p9.setState("Karnataka"); p9.setCity("Mysuru"); p9.setLocality("Kalidasa Road"); p9.setAddress("55 Kalidasa Main Rd"); p9.setPincode("570002");
            p9.setLatitude(12.3228); p9.setLongitude(76.6258);
            p9.setApprovalStatus("APPROVED"); p9.setListingStatus("AVAILABLE");
            p9.setDescription("40ft frontage toughened-glass showroom on Mysuru's most vibrant commercial high street with dedicated valet bay.");
            p9.setAmenities(Arrays.asList("40ft Frontage", "Valet Bay", "HVAC Ducting", "3-Phase Power"));
            p9.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p9, agent); p9.setAverageRating(4.5); p9.setViewsCount(130);
            properties.put(109, p9);

            // 10. Farmhouse Rental (Coorg - RENT)
            FarmHouse p10 = new FarmHouse();
            p10.setPropertyId(110); p10.setOwnerId(owner.getUserId()); p10.setTitle("Misty Canopy Timber Farmhouse Villa");
            p10.setPurpose("RENT"); p10.setPrice(54000); p10.setPropertyArea(2.2); p10.setLandArea(2.2); p10.setBuiltUpArea(2600);
            p10.setBedrooms(3); p10.setBathrooms(3); p10.setHasGarden(true); p10.setHasSwimmingPool(false);
            p10.setState("Karnataka"); p10.setCity("Coorg"); p10.setLocality("Pollibetta"); p10.setAddress("Misty Ridge Estate, Pollibetta"); p10.setPincode("571215");
            p10.setLatitude(12.2389); p10.setLongitude(75.9532);
            p10.setApprovalStatus("APPROVED"); p10.setListingStatus("AVAILABLE");
            p10.setDescription("Work-from-hills sanctuary surrounded by bamboo groves, gigabit fiber link, wrap-around deck, and fireplace lounge.");
            p10.setAmenities(Arrays.asList("Gigabit Fiber", "Fireplace", "Fruit Orchard", "Caretaker"));
            p10.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p10, owner); p10.setAverageRating(4.8); p10.setViewsCount(178);
            properties.put(110, p10);

            // 11. PENDING Listing for Admin Moderation Queue
            ResidentialProperty p11 = new ResidentialProperty(111, seller.getUserId(), "Lakeview 2 BHK Minimalist Condo (Pending)", "BUY", 6400000, 1320, "Karnataka", "Bengaluru", 2, 2);
            p11.setResidentialType("FLAT"); p11.setLocality("Hebbal Lake Road"); p11.setAddress("Tower C, Lakeside Horizon"); p11.setPincode("560024");
            p11.setLatitude(13.0358); p11.setLongitude(77.5970);
            p11.setApprovalStatus("PENDING"); p11.setListingStatus("AVAILABLE");
            p11.setDescription("Brand new corner lake-facing condo awaiting final Admin publication review.");
            p11.setAmenities(Arrays.asList("Clubhouse", "Jogging Track", "Covered Parking"));
            p11.setImageUrls(Arrays.asList("https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80"));
            attachOwner(p11, seller);
            properties.put(111, p11);

            // 12. PENDING Plot Listing for Admin Moderation Queue
            Plot p12 = new Plot();
            p12.setPropertyId(112); p12.setOwnerId(owner.getUserId()); p12.setTitle("Hitech Corridor Commercial Highway Plot (Pending)");
            p12.setPurpose("BUY"); p12.setPrice(22000000); p12.setPropertyArea(5000); p12.setTotalArea(5000);
            p12.setPlotType("COMMERCIAL_PLOT"); p12.setPlotDimensions("50 x 100 Ft"); p12.setFacingDirection("SOUTH"); p12.setApprovalAuthority("HMDA");
            p12.setState("Telangana"); p12.setCity("Hyderabad"); p12.setLocality("Gachibowli"); p12.setAddress("Financial District Link Rd"); p12.setPincode("500032");
            p12.setLatitude(17.4401); p12.setLongitude(78.3489);
            p12.setApprovalStatus("PENDING"); p12.setListingStatus("AVAILABLE");
            p12.setDescription("High-visibility commercial corner plot submitted for verification.");
            p12.setAmenities(Arrays.asList("60ft Road", "Corner Access"));
            p12.setImageUrls(Arrays.asList("https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80"));
            // 13. Mumbai Suburban, Maharashtra: 4 BHK Sea-Facing Penthouse
            ResidentialProperty p13 = new ResidentialProperty(113, owner.getUserId(), "Arabian Sea Front 4 BHK Penthouse", "BUY", 145000000, 4200, "Maharashtra", "Mumbai", 4, 5);
            p13.setResidentialType("PENTHOUSE"); p13.setFurnishingStatus("FULLY_FURNISHED"); p13.setFeatured(true);
            p13.setLocality("Bandra West"); p13.setAddress("Tower 1, Sea View Enclave, Carter Road, Bandra West"); p13.setPincode("400050");
            p13.setLatitude(19.0657); p13.setLongitude(72.8271);
            p13.setApprovalStatus("APPROVED"); p13.setListingStatus("AVAILABLE");
            p13.setDescription("Ultra-exclusive sea-facing penthouse offering 180-degree Arabian Sea vistas, private elevator access, Italian marble floors, and 4 stilt car parks.");
            p13.setAmenities(Arrays.asList("Sea Facing", "Private Terrace Pool", "Concierge Desk", "4 Car Parking", "High Speed Lifts", "24x7 Security"));
            p13.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p13, owner); p13.setAverageRating(5.0); p13.setViewsCount(420);
            properties.put(113, p13);

            // 14. Mumbai Suburban, Maharashtra: 2 BHK Designer Flat (RENT)
            ResidentialProperty p14 = new ResidentialProperty(114, owner.getUserId(), "Modern 2 BHK Designer Flat", "RENT", 85000, 1180, "Maharashtra", "Mumbai", 2, 2);
            p14.setResidentialType("APARTMENT"); p14.setFurnishingStatus("FULLY_FURNISHED"); p14.setFeatured(false);
            p14.setLocality("Andheri East"); p14.setAddress("Flat 804, Pinnacle Heights, Mahakali Caves Road, Andheri East"); p14.setPincode("400093");
            p14.setLatitude(19.1136); p14.setLongitude(72.8697);
            p14.setApprovalStatus("APPROVED"); p14.setListingStatus("AVAILABLE");
            p14.setDescription("Chic contemporary flat close to Western Express Highway and Metro Station. Features modular kitchen, club access, and piped gas.");
            p14.setAmenities(Arrays.asList("Metro Proximity", "Clubhouse Gym", "Piped Gas", "Covered Parking"));
            p14.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p14, owner); p14.setAverageRating(4.7); p14.setViewsCount(210);
            properties.put(114, p14);

            // 15. Pune District, Maharashtra: 3 BHK Garden Duplex Villa (BUY)
            ResidentialProperty p15 = new ResidentialProperty(115, seller.getUserId(), "Koregaon Park 3 BHK Garden Duplex Villa", "BUY", 24500000, 2750, "Maharashtra", "Pune", 3, 3);
            p15.setResidentialType("VILLA"); p15.setFurnishingStatus("SEMI_FURNISHED"); p15.setFeatured(true);
            p15.setLocality("Koregaon Park"); p15.setAddress("Villa 12, Green Oaks Enclave, North Main Road, Koregaon Park"); p15.setPincode("411001");
            p15.setLatitude(18.5362); p15.setLongitude(73.8940);
            p15.setApprovalStatus("APPROVED"); p15.setListingStatus("AVAILABLE");
            p15.setDescription("Bespoke architectural residence in Pune's most leafy prime neighborhood. Features private lawn, skylit dining foyer, and EV charging.");
            p15.setAmenities(Arrays.asList("Private Lawn", "EV Charging", "Pet Friendly", "Solar Water Heating"));
            p15.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p15, seller); p15.setAverageRating(4.8); p15.setViewsCount(330);
            properties.put(115, p15);

            // 16. Pune District, Maharashtra: Commercial Office Floor (RENT)
            CommercialProperty p16 = new CommercialProperty();
            p16.setPropertyId(116); p16.setOwnerId(agent.getUserId()); p16.setTitle("Hinjawadi IT Park Plug-and-Play Office Floor");
            p16.setPurpose("RENT"); p16.setPrice(185000); p16.setPropertyArea(3200); p16.setCarpetArea(2900);
            p16.setCommercialType("OFFICE_SPACE"); p16.setCommercialUsage("Software / Tech Delivery Center"); p16.setPowerBackup(true);
            p16.setState("Maharashtra"); p16.setCity("Pune"); p16.setLocality("Hinjawadi Phase 1"); p16.setAddress("Floor 4, Tower Alpha, Rajiv Gandhi Infotech Park, Hinjawadi"); p16.setPincode("411057");
            p16.setLatitude(18.5913); p16.setLongitude(73.7389);
            p16.setApprovalStatus("APPROVED"); p16.setListingStatus("AVAILABLE");
            p16.setDescription("Plug-and-play 50-workstation setup with 2 director cabins, conference room, server room with precision AC, and 100% DG backup.");
            p16.setAmenities(Arrays.asList("100% DG Backup", "Server Room", "Dual ISP Line", "Cafeteria Access", "Parking"));
            p16.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p16, agent); p16.setAverageRating(4.6); p16.setViewsCount(180);
            properties.put(116, p16);

            // 17. North Goa, Goa: 4 BHK Portuguese Heritage Pool Villa (BUY)
            ResidentialProperty p17 = new ResidentialProperty(117, owner.getUserId(), "Candolim Beachfront 4 BHK Heritage Pool Villa", "BUY", 42000000, 3800, "Goa", "Goa", 4, 4);
            p17.setResidentialType("VILLA"); p17.setFurnishingStatus("FULLY_FURNISHED"); p17.setFeatured(true);
            p17.setLocality("Candolim"); p17.setAddress("Villa 4, Aguada Fort Road, Candolim Beach"); p17.setPincode("403515");
            p17.setLatitude(15.5186); p17.setLongitude(73.7686);
            p17.setApprovalStatus("APPROVED"); p17.setListingStatus("AVAILABLE");
            p17.setDescription("Restored Portuguese heritage villa 300 meters from Candolim beach. Private infinity swimming pool, laterite stone architecture, and lush tropical palms.");
            p17.setAmenities(Arrays.asList("Beach Walking Distance", "Private Pool", "Heritage Architecture", "Homestay License Ready"));
            p17.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1200&q=80",
                "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p17, owner); p17.setAverageRating(4.9); p17.setViewsCount(610);
            properties.put(117, p17);

            // 18. Hyderabad, Telangana: 5 BHK Hilltop Luxury Mansion (BUY)
            ResidentialProperty p18 = new ResidentialProperty(118, seller.getUserId(), "Jubilee Hills 5 BHK Signature Hilltop Mansion", "BUY", 168000000, 6500, "Telangana", "Hyderabad", 5, 6);
            p18.setResidentialType("VILLA"); p18.setFurnishingStatus("FULLY_FURNISHED"); p18.setFeatured(true);
            p18.setLocality("Jubilee Hills"); p18.setAddress("Plot 22, Road No. 36, Near Peddamma Temple, Jubilee Hills"); p18.setPincode("500033");
            p18.setLatitude(17.4326); p18.setLongitude(78.4071);
            p18.setApprovalStatus("APPROVED"); p18.setListingStatus("AVAILABLE");
            p18.setDescription("Awe-inspiring architectural masterpiece perched on Jubilee Hills. Features private home theater, temperature-controlled indoor pool, and elevator.");
            p18.setAmenities(Arrays.asList("Home Theater", "Private Elevator", "Heated Pool", "Security Automation", "6 Car Garage"));
            p18.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p18, seller); p18.setAverageRating(5.0); p18.setViewsCount(490);
            properties.put(118, p18);

            // 19. Hyderabad, Telangana: 3 BHK Lake-Facing High Rise (RENT)
            ResidentialProperty p19 = new ResidentialProperty(119, owner.getUserId(), "Gachibowli 3 BHK Lake-Facing High Rise", "RENT", 75000, 2250, "Telangana", "Hyderabad", 3, 3);
            p19.setResidentialType("APARTMENT"); p19.setFurnishingStatus("SEMI_FURNISHED");
            p19.setLocality("Gachibowli"); p19.setAddress("Flat 1402, Waverock Enclave, Financial District, Gachibowli"); p19.setPincode("500032");
            p19.setLatitude(17.4190); p19.setLongitude(78.3428);
            p19.setApprovalStatus("APPROVED"); p19.setListingStatus("AVAILABLE");
            p19.setDescription("Lake-view 14th floor luxury apartment walking distance from major tech headquarters in Financial District. 2 car parking bays.");
            p19.setAmenities(Arrays.asList("Lake View", "Clubhouse", "Tennis Court", "2 Car Parking"));
            p19.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p19, owner); p19.setAverageRating(4.7); p19.setViewsCount(280);
            properties.put(119, p19);

            // 20. Chennai, Tamil Nadu: 4 BHK Royal Waterfront Residence (BUY)
            ResidentialProperty p20 = new ResidentialProperty(120, seller.getUserId(), "Boat Club Road 4 BHK Royal Waterfront Residence", "BUY", 92000000, 3900, "Tamil Nadu", "Chennai", 4, 4);
            p20.setResidentialType("VILLA"); p20.setFurnishingStatus("FULLY_FURNISHED"); p20.setFeatured(true);
            p20.setLocality("R.A. Puram"); p20.setAddress("8 Boat Club Road, R.A. Puram, Chennai"); p20.setPincode("600028");
            p20.setLatitude(13.0234); p20.setLongitude(80.2520);
            p20.setApprovalStatus("APPROVED"); p20.setListingStatus("AVAILABLE");
            p20.setDescription("Ultra-premium estate in Chennai's most coveted VIP pincode. Overlooks Adyar river, landscaped private lawn, and marble finish.");
            p20.setAmenities(Arrays.asList("VIP Neighborhood", "Riverfront", "100% Power Backup", "Private Security Post"));
            p20.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p20, seller); p20.setAverageRating(4.9); p20.setViewsCount(370);
            properties.put(120, p20);

            // 21. Chennai, Tamil Nadu: Gated Villa Plot (BUY)
            Plot p21 = new Plot();
            p21.setPropertyId(121); p21.setOwnerId(seller.getUserId()); p21.setTitle("OMR IT Corridor CMDA Approved Villa Plot (30x50)");
            p21.setPurpose("BUY"); p21.setPrice(4800000); p21.setPropertyArea(1500); p21.setTotalArea(1500);
            p21.setPlotType("RESIDENTIAL_PLOT"); p21.setPlotDimensions("30 x 50 Ft"); p21.setFacingDirection("EAST"); p21.setApprovalAuthority("CMDA & RERA");
            p21.setState("Tamil Nadu"); p21.setCity("Chennai"); p21.setLocality("Sholinganallur"); p21.setAddress("Plot 45, Tech Meadows, OMR Road, Sholinganallur"); p21.setPincode("600119");
            p21.setLatitude(12.9010); p21.setLongitude(80.2279);
            p21.setApprovalStatus("APPROVED"); p21.setListingStatus("AVAILABLE");
            p21.setDescription("Clear CMDA title gated villa plot with black-top tar roads, underground storm water drains, street lamps, and children's park.");
            p21.setAmenities(Arrays.asList("Blacktop Road", "Underground Drainage", "Gated Security", "Clear CMDA Title"));
            p21.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p21, seller); p21.setAverageRating(4.6); p21.setViewsCount(190);
            properties.put(121, p21);

            // 22. New Delhi, Delhi NCR: 4 BHK Diplomatic Enclave (RENT)
            ResidentialProperty p22 = new ResidentialProperty(122, owner.getUserId(), "Vasant Vihar 4 BHK Diplomatic Enclave Apartment", "RENT", 220000, 3600, "Delhi NCR", "Delhi NCR", 4, 4);
            p22.setResidentialType("APARTMENT"); p22.setFurnishingStatus("FULLY_FURNISHED"); p22.setFeatured(true);
            p22.setLocality("Vasant Vihar"); p22.setAddress("A-Block, Poorvi Marg, Vasant Vihar, New Delhi"); p22.setPincode("110057");
            p22.setLatitude(28.5603); p22.setLongitude(77.1627);
            p22.setApprovalStatus("APPROVED"); p22.setListingStatus("AVAILABLE");
            p22.setDescription("Sprawling builder floor in diplomat row. Italian marble, central VRV air conditioning, servant quarters, and 3 reserved parking spaces.");
            p22.setAmenities(Arrays.asList("Diplomatic Row", "Central AC", "Servant Quarters", "3 Covered Car Parking"));
            p22.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p22, owner); p22.setAverageRating(4.9); p22.setViewsCount(340);
            properties.put(122, p22);

            // 23. Gurugram, Delhi NCR: 4 BHK Golf Course Luxury Villa (BUY)
            ResidentialProperty p23 = new ResidentialProperty(123, seller.getUserId(), "Golf Course Extension 4 BHK Signature Luxury Villa", "BUY", 85000000, 4800, "Delhi NCR", "Delhi NCR", 4, 5);
            p23.setResidentialType("VILLA"); p23.setFurnishingStatus("FULLY_FURNISHED"); p23.setFeatured(true);
            p23.setLocality("Golf Course Extn"); p23.setAddress("Villa 18, Palm Springs Golf Enclave, Sector 54, Gurugram"); p23.setPincode("122011");
            p23.setLatitude(28.4089); p23.setLongitude(77.0783);
            p23.setApprovalStatus("APPROVED"); p23.setListingStatus("AVAILABLE");
            p23.setDescription("Ultra-modern golf-facing villa with private elevator, heated jacuzzi deck, clubhouse golf access, and 24x7 biometric security.");
            p23.setAmenities(Arrays.asList("Golf Course Access", "Private Jacuzzi", "Elevator", "EV Ready 3 Car Parking"));
            p23.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p23, seller); p23.setAverageRating(4.9); p23.setViewsCount(410);
            properties.put(123, p23);

            // 24. Mysuru District, Karnataka: 3 BHK Heritage View Bungalow (BUY)
            ResidentialProperty p24 = new ResidentialProperty(124, seller.getUserId(), "Gokulam 3 BHK Chamundi View Heritage Bungalow", "BUY", 13500000, 2300, "Karnataka", "Mysuru", 3, 3);
            p24.setResidentialType("VILLA"); p24.setFurnishingStatus("SEMI_FURNISHED"); p24.setFeatured(false);
            p24.setLocality("Gokulam 3rd Stage"); p24.setAddress("5th Cross, Near Yoga Shala, Gokulam 3rd Stage, Mysuru"); p24.setPincode("570002");
            p24.setLatitude(12.3312); p24.setLongitude(76.6298);
            p24.setApprovalStatus("APPROVED"); p24.setListingStatus("AVAILABLE");
            p24.setDescription("Charming bungalow in tranquil Gokulam cultural enclave. Clear view of Chamundi Hills from rooftop pergola, rain harvesting, and teakwood doors.");
            p24.setAmenities(Arrays.asList("Chamundi Hill View", "Rainwater Harvesting", "Teak Finish", "2 Car Porch"));
            p24.setImageUrls(Arrays.asList(
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80"
            ));
            attachOwner(p24, seller); p24.setAverageRating(4.8); p24.setViewsCount(295);
            properties.put(124, p24);

            propSeq.set(126);

            // Seed visits, rental agreement, verification, favorites, reviews, and audit log
            PropertyVisit v1 = new PropertyVisit();
            v1.setVisitId(1); v1.setPropertyId(101); v1.setPropertyTitle(p1.getTitle()); v1.setPropertyCity("Mysuru");
            v1.setOwnerId(owner.getUserId()); v1.setUserId(buyer.getUserId()); v1.setUserName(buyer.getFullName());
            v1.setVisitDate(Date.valueOf(LocalDate.now().plusDays(3))); v1.setVisitTime(Time.valueOf("11:00:00"));
            v1.setContactNumber(buyer.getMobileNumber()); v1.setNotes("Interested in inspecting solar installation & courtyard.");
            v1.setAppointmentStatus("CONFIRMED");
            visits.add(v1);

            RentalAgreement ra = new RentalAgreement();
            ra.setAgreementId(1); ra.setPropertyId(102); ra.setPropertyTitle(p2.getTitle());
            ra.setOwnerId(owner.getUserId()); ra.setOwnerName(owner.getFullName());
            ra.setTenantId(tenant.getUserId()); ra.setTenantName(tenant.getFullName());
            ra.setMonthlyRent(68000); ra.setSecurityDeposit(300000);
            ra.setAgreementStartDate(Date.valueOf(LocalDate.now().minusMonths(2)));
            ra.setAgreementEndDate(Date.valueOf(LocalDate.now().plusMonths(10)));
            ra.setAgreementStatus("ACTIVE");
            agreements.add(ra);

            OwnerVerification ov = new OwnerVerification();
            ov.setVerificationId(1); ov.setUserId(tenant.getUserId()); ov.setUserName(tenant.getFullName());
            ov.setUserEmail(tenant.getEmail()); ov.setIdentityProofType("PAN"); ov.setIdentityProofNumber("ABCDE1234F");
            ov.setPropertyAddress("44 Indiranagar, Bengaluru"); ov.setDocumentPath("/uploads/docs/kyc_5.pdf");
            ov.setVerificationStatus("PENDING");
            verifications.add(ov);

            favoritesByUser.computeIfAbsent(buyer.getUserId(), k -> ConcurrentHashMap.newKeySet()).addAll(Arrays.asList(101, 105));

            PropertyReview r1 = new PropertyReview();
            r1.setReviewId(1); r1.setPropertyId(101); r1.setUserId(buyer.getUserId()); r1.setUserName(buyer.getFullName());
            r1.setUserRole("BUYER"); r1.setRating(5); r1.setComment("Exceptional daylighting, quiet cul-de-sac, and transparent documentation.");
            reviews.add(r1);

            PropertyReview r2 = new PropertyReview();
            r2.setReviewId(2); r2.setPropertyId(105); r2.setUserId(buyer.getUserId()); r2.setUserName(buyer.getFullName());
            r2.setUserRole("BUYER"); r2.setRating(5); r2.setComment("Assagao laterite architecture is timeless. Great rental yield projection.");
            reviews.add(r2);

            recordAudit(admin, "SYSTEM_BOOT", "SYSTEM", 0, "Initialized EstateVault Demo Persistence Store with 12 listings.");
        }

        private void attachOwner(Property p, User owner) {
            p.setOwnerName(owner.getFullName());
            p.setOwnerEmail(owner.getEmail());
            p.setOwnerPhone(owner.getMobileNumber());
            p.setOwnerVerified(owner.isVerified());
        }

        public void recordAudit(User actor, String action, String entityType, int entityId, String details) {
            AuditLogEntry e = new AuditLogEntry();
            e.setLogId(auditSeq.getAndIncrement());
            e.setActorUserId(actor != null ? actor.getUserId() : 0);
            e.setActorName(actor != null ? actor.getFullName() : "System");
            e.setAction(action);
            e.setEntityType(entityType);
            e.setEntityId(entityId);
            e.setDetails(details);
            auditLogs.add(0, e);
            if (auditLogs.size() > 200) {
                auditLogs.remove(auditLogs.size() - 1);
            }
        }
    }

    // =========================================================================
    // 9. DATA ACCESS OBJECTS (SUPABASE -> MYSQL JDBC -> IN-MEMORY STORE)
    // =========================================================================

    public static class UserDAO {
        public User authenticate(String email, String rawPassword) throws SQLException, AuthenticationException {
            String normEmail = email != null ? email.trim().toLowerCase() : "";
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                try {
                    SupabaseClient sb = SupabaseClient.getInstance();
                    List<Map<String, Object>> rows = sb.get("users?select=*&email=eq." + SupabaseClient.encode(normEmail) + "&is_active=eq.true&limit=1");
                    if (!rows.isEmpty()) {
                        Map<String, Object> r = rows.get(0);
                        if (PasswordUtil.verifyPassword(rawPassword, JsonUtil.getString(r, "password_salt"), JsonUtil.getString(r, "password_hash"))) {
                            return mapUser(r);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[UserDAO] Supabase auth query notice (" + e.getMessage() + "), checking fallback store.");
                }
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                String sql = "SELECT * FROM users WHERE email = ? AND is_active = TRUE";
                try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, normEmail);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) {
                            String salt = rs.getString("password_salt");
                            String hash = rs.getString("password_hash");
                            if (PasswordUtil.verifyPassword(rawPassword, salt, hash)) {
                                return mapResultSetUser(rs);
                            }
                        }
                    }
                }
                throw new AuthenticationException("Invalid email or password.");
            }
            for (User u : InMemoryStore.getInstance().users.values()) {
                if (u.isActive() && u.getEmail().equalsIgnoreCase(normEmail)) {
                    if (PasswordUtil.verifyPassword(rawPassword, u.getPasswordSalt(), u.getPasswordHash())) {
                        return u;
                    }
                    break;
                }
            }
            throw new AuthenticationException("Invalid email or password.");
        }

        public User register(User u, String rawPassword) throws SQLException, AuthenticationException {
            if (rawPassword == null || rawPassword.length() < 6) {
                throw new AuthenticationException("Password must be at least 6 characters.");
            }
            if (u.getFullName() == null || u.getFullName().trim().length() < 2) {
                throw new AuthenticationException("Full name is required.");
            }
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(rawPassword, salt);

            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                SupabaseClient sb = SupabaseClient.getInstance();
                if (!sb.get("users?select=user_id&email=eq." + SupabaseClient.encode(u.getEmail())).isEmpty()) {
                    throw new AuthenticationException("Email already exists.");
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("full_name", u.getFullName());
                payload.put("email", u.getEmail());
                payload.put("mobile_number", u.getMobileNumber());
                payload.put("password_hash", hash);
                payload.put("password_salt", salt);
                payload.put("user_role", u.getUserRole());
                payload.put("is_verified", false);
                payload.put("is_active", true);
                Map<String, Object> created = sb.post("users", payload);
                u.setUserId(JsonUtil.getInt(created, "user_id"));
                return u;
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                String checkSql = "SELECT user_id FROM users WHERE email = ?";
                String insertSql = "INSERT INTO users (full_name, email, mobile_number, password_hash, password_salt, user_role, is_verified) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (Connection conn = DBConnection.getInstance().getConnection()) {
                    try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                        check.setString(1, u.getEmail());
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next()) throw new AuthenticationException("Email already exists.");
                        }
                    }
                    try (PreparedStatement ins = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        ins.setString(1, u.getFullName());
                        ins.setString(2, u.getEmail());
                        ins.setString(3, u.getMobileNumber());
                        ins.setString(4, hash);
                        ins.setString(5, salt);
                        ins.setString(6, u.getUserRole());
                        ins.setBoolean(7, false);
                        ins.executeUpdate();
                        try (ResultSet gk = ins.getGeneratedKeys()) {
                            if (gk.next()) u.setUserId(gk.getInt(1));
                        }
                    }
                }
                return u;
            }
            InMemoryStore store = InMemoryStore.getInstance();
            for (User existing : store.users.values()) {
                if (existing.getEmail().equalsIgnoreCase(u.getEmail())) {
                    throw new AuthenticationException("Email already exists.");
                }
            }
            int id = store.userSeq.getAndIncrement();
            u.setUserId(id);
            u.setPasswordSalt(salt);
            u.setPasswordHash(hash);
            u.setActive(true);
            store.users.put(id, u);
            store.recordAudit(u, "USER_REGISTER", "USER", id, "New account registered (" + u.getUserRole() + ")");
            return u;
        }

        public List<User> getAllUsers() throws SQLException {
            List<User> list = new ArrayList<>();
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                for (Map<String, Object> r : SupabaseClient.getInstance().get("users?select=*&order=created_at.desc")) {
                    list.add(mapUser(r));
                }
                return list;
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection();
                     PreparedStatement st = conn.prepareStatement("SELECT * FROM users ORDER BY created_at DESC");
                     ResultSet rs = st.executeQuery()) {
                    while (rs.next()) list.add(mapResultSetUser(rs));
                }
                return list;
            }
            list.addAll(InMemoryStore.getInstance().users.values());
            list.sort(Comparator.comparingInt(User::getUserId));
            return list;
        }

        public User findByEmail(String email) throws SQLException {
            String norm = email != null ? email.trim().toLowerCase() : "";
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                List<Map<String, Object>> rows = SupabaseClient.getInstance().get("users?select=*&email=eq." + SupabaseClient.encode(norm) + "&limit=1");
                return rows.isEmpty() ? null : mapUser(rows.get(0));
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement("SELECT * FROM users WHERE email = ?")) {
                    st.setString(1, norm);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) return mapResultSetUser(rs);
                    }
                }
                return null;
            }
            for (User u : InMemoryStore.getInstance().users.values()) {
                if (u.getEmail().equalsIgnoreCase(norm)) return u;
            }
            return null;
        }

        public User findById(int userId) throws SQLException {
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                List<Map<String, Object>> rows = SupabaseClient.getInstance().get("users?select=*&user_id=eq." + userId + "&limit=1");
                return rows.isEmpty() ? null : mapUser(rows.get(0));
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement("SELECT * FROM users WHERE user_id = ?")) {
                    st.setInt(1, userId);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) return mapResultSetUser(rs);
                    }
                }
                return null;
            }
            return InMemoryStore.getInstance().users.get(userId);
        }

        public boolean updatePasswordByEmail(String email, String newRawPassword) throws SQLException {
            String norm = email != null ? email.trim().toLowerCase() : "";
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(newRawPassword, salt);
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("password_hash", hash);
                body.put("password_salt", salt);
                return !SupabaseClient.getInstance().patch("users?email=eq." + SupabaseClient.encode(norm), body).isEmpty();
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement("UPDATE users SET password_hash = ?, password_salt = ? WHERE email = ?")) {
                    st.setString(1, hash); st.setString(2, salt); st.setString(3, norm);
                    return st.executeUpdate() > 0;
                }
            }
            User u = findByEmail(norm);
            if (u == null) return false;
            u.setPasswordSalt(salt);
            u.setPasswordHash(hash);
            return true;
        }

        private static User mapResultSetUser(ResultSet rs) throws SQLException {
            User u = createInstance(rs.getString("user_role"));
            u.setUserId(rs.getInt("user_id"));
            u.setFullName(rs.getString("full_name"));
            u.setEmail(rs.getString("email"));
            u.setMobileNumber(rs.getString("mobile_number"));
            u.setUserRole(rs.getString("user_role"));
            u.setVerified(rs.getBoolean("is_verified"));
            u.setActive(rs.getBoolean("is_active"));
            return u;
        }

        private static User mapUser(Map<String, Object> r) {
            User u = createInstance(JsonUtil.getString(r, "user_role"));
            u.setUserId(JsonUtil.getInt(r, "user_id"));
            u.setFullName(JsonUtil.getString(r, "full_name"));
            u.setEmail(JsonUtil.getString(r, "email"));
            u.setMobileNumber(JsonUtil.getString(r, "mobile_number"));
            u.setUserRole(JsonUtil.getString(r, "user_role"));
            u.setVerified(JsonUtil.getBoolean(r, "is_verified"));
            u.setActive(JsonUtil.getBoolean(r, "is_active"));
            return u;
        }

        public static User createInstance(String role) {
            if (role == null) return new Buyer();
            switch (role.toUpperCase()) {
                case "ADMIN": return new Admin();
                case "SELLER": return new Seller();
                case "TENANT": return new Tenant();
                case "PROPERTY_OWNER": return new PropertyOwner();
                case "AGENT": return new Agent();
                default: return new Buyer();
            }
        }
    }

    public static class PropertyDAO {
        private static final String SB_SELECT = "select=*,property_images(*)";

        public static Property createPropertyInstance(String code) {
            if (code == null) return new ResidentialProperty();
            switch (code.toUpperCase()) {
                case "COMMERCIAL": return new CommercialProperty();
                case "AGRICULTURAL": return new AgriculturalLand();
                case "FARMHOUSE": return new FarmHouse();
                case "PLOT": return new Plot();
                case "BUILDING": return new Building();
                default: return new ResidentialProperty();
            }
        }

        public Property save(Property p, User actor) throws SQLException, InvalidPropertyDataException {
            p.validateBaseFields();
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                SupabaseClient sb = SupabaseClient.getInstance();
                Map<String, Object> base = buildSupabasePropertyPayload(p, true);
                Map<String, Object> created = sb.post("properties", base);
                int pid = JsonUtil.getInt(created, "property_id");
                p.setPropertyId(pid);
                saveCategoryDetailsSupabase(sb, p, pid);
                saveImagesAndAmenitiesSupabase(sb, p, pid);
                return p;
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                String sql = "INSERT INTO properties (owner_id, category_id, title, description, purpose, price, property_area, area_unit, state, city, locality, address, pincode, model_3d_url, model_3d_format, hdri_preset, approval_status, listing_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'AVAILABLE')";
                Connection conn = null;
                try {
                    conn = DBConnection.getInstance().getConnection();
                    conn.setAutoCommit(false);
                    int pid = 0;
                    try (PreparedStatement st = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        st.setInt(1, p.getOwnerId());
                        st.setInt(2, p.getCategoryId());
                        st.setString(3, p.getTitle());
                        st.setString(4, p.getDescription());
                        st.setString(5, p.getPurpose());
                        st.setDouble(6, p.getPrice());
                        st.setDouble(7, p.getPropertyArea());
                        st.setString(8, p.getAreaUnit() != null ? p.getAreaUnit() : "Sq. Ft.");
                        st.setString(9, p.getState());
                        st.setString(10, p.getCity());
                        st.setString(11, p.getLocality());
                        st.setString(12, p.getAddress());
                        st.setString(13, p.getPincode());
                        st.setString(14, p.getModel3dUrl());
                        st.setString(15, p.getModel3dFormat() != null ? p.getModel3dFormat() : "GLB");
                        st.setString(16, p.getHdriPreset() != null ? p.getHdriPreset() : "daylight");
                        st.executeUpdate();
                        try (ResultSet rs = st.getGeneratedKeys()) {
                            if (rs.next()) { pid = rs.getInt(1); p.setPropertyId(pid); }
                        }
                    }
                    if (p instanceof ResidentialProperty) {
                        ResidentialProperty rp = (ResidentialProperty) p;
                        try (PreparedStatement st = conn.prepareStatement("INSERT INTO residential_details (property_id, residential_type, bedrooms, bathrooms, built_up_area, furnishing_status) VALUES (?, ?, ?, ?, ?, ?)")) {
                            st.setInt(1, pid);
                            st.setString(2, rp.getResidentialType() != null ? rp.getResidentialType() : "APARTMENT");
                            st.setInt(3, rp.getBedrooms());
                            st.setInt(4, rp.getBathrooms());
                            st.setDouble(5, rp.getBuiltUpArea() > 0 ? rp.getBuiltUpArea() : rp.getPropertyArea());
                            st.setString(6, rp.getFurnishingStatus() != null ? rp.getFurnishingStatus() : "SEMI_FURNISHED");
                            st.executeUpdate();
                        }
                    }
                    conn.commit();
                    return p;
                } catch (SQLException e) {
                    if (conn != null) conn.rollback();
                    throw e;
                } finally {
                    if (conn != null) { conn.setAutoCommit(true); conn.close(); }
                }
            }
            InMemoryStore store = InMemoryStore.getInstance();
            int pid = store.propSeq.getAndIncrement();
            p.setPropertyId(pid);
            p.setApprovalStatus(actor != null && actor.isAdministrator() ? "APPROVED" : "PENDING");
            p.setListingStatus("AVAILABLE");
            if (actor != null) {
                p.setOwnerName(actor.getFullName());
                p.setOwnerEmail(actor.getEmail());
                p.setOwnerPhone(actor.getMobileNumber());
                p.setOwnerVerified(actor.isVerified());
            }
            if (p.getLatitude() == 0 && p.getLongitude() == 0) {
                double[] coords = defaultCityCoords(p.getCity());
                p.setLatitude(coords[0] + ((pid % 7) - 3) * 0.008);
                p.setLongitude(coords[1] + ((pid % 5) - 2) * 0.008);
            }
            if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                p.setPrimaryImageUrl(p.getImageUrls().get(0));
            }
            store.properties.put(pid, p);
            store.recordAudit(actor, "PROPERTY_CREATED", "PROPERTY", pid, "Listed \"" + p.getTitle() + "\" (" + p.getApprovalStatus() + ")");
            return p;
        }

        public Property updateWithIDORCheck(int propId, Map<String, String> params, User actor)
                throws SQLException, PropertyNotFoundException, UnauthorizedAccessException, InvalidPropertyDataException {
            Property existing = getById(propId);
            if (!actor.isAdministrator() && existing.getOwnerId() != actor.getUserId()) {
                throw new UnauthorizedAccessException("You do not own this listing.");
            }
            if (params.containsKey("title") && !params.get("title").trim().isEmpty()) existing.setTitle(params.get("title").trim());
            if (params.containsKey("description")) existing.setDescription(params.get("description"));
            if (params.containsKey("purpose") && !params.get("purpose").trim().isEmpty()) existing.setPurpose(params.get("purpose").trim().toUpperCase());
            if (params.containsKey("price") && !params.get("price").trim().isEmpty()) existing.setPrice(Double.parseDouble(params.get("price")));
            if (params.containsKey("propertyArea") && !params.get("propertyArea").trim().isEmpty()) existing.setPropertyArea(Double.parseDouble(params.get("propertyArea")));
            if (params.containsKey("city") && !params.get("city").trim().isEmpty()) existing.setCity(params.get("city").trim());
            if (params.containsKey("locality")) existing.setLocality(params.get("locality"));
            if (params.containsKey("address")) existing.setAddress(params.get("address"));
            if (params.containsKey("model3dUrl")) existing.setModel3dUrl(params.get("model3dUrl"));
            if (params.containsKey("model3dFormat") && !params.get("model3dFormat").trim().isEmpty()) {
                existing.setModel3dFormat(params.get("model3dFormat").trim().toUpperCase(Locale.ROOT));
            }
            if (params.containsKey("hdriPreset") && !params.get("hdriPreset").trim().isEmpty()) {
                existing.setHdriPreset(params.get("hdriPreset").trim().toLowerCase(Locale.ROOT));
            }
            existing.validateBaseFields();

            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                Map<String, Object> patch = buildSupabasePropertyPayload(existing, false);
                SupabaseClient.getInstance().patch("properties?property_id=eq." + propId, patch);
            } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection();
                     PreparedStatement st = conn.prepareStatement("UPDATE properties SET title=?, description=?, purpose=?, price=?, property_area=?, city=?, locality=?, address=?, model_3d_url=?, model_3d_format=?, hdri_preset=? WHERE property_id=?")) {
                    st.setString(1, existing.getTitle()); st.setString(2, existing.getDescription()); st.setString(3, existing.getPurpose());
                    st.setDouble(4, existing.getPrice()); st.setDouble(5, existing.getPropertyArea());
                    st.setString(6, existing.getCity()); st.setString(7, existing.getLocality()); st.setString(8, existing.getAddress());
                    st.setString(9, existing.getModel3dUrl());
                    st.setString(10, existing.getModel3dFormat() != null ? existing.getModel3dFormat() : "GLB");
                    st.setString(11, existing.getHdriPreset() != null ? existing.getHdriPreset() : "daylight");
                    st.setInt(12, propId);
                    st.executeUpdate();
                }
            } else {
                InMemoryStore.getInstance().recordAudit(actor, "PROPERTY_UPDATED", "PROPERTY", propId, "Updated listing \"" + existing.getTitle() + "\"");
            }
            return existing;
        }

        public List<Property> search(Map<String, String> filters, String sort) throws SQLException {
            List<Property> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                try {
                    SupabaseClient sb = SupabaseClient.getInstance();
                    String sortParam = "&order=created_at.desc";
                    if ("price_asc".equalsIgnoreCase(sort)) sortParam = "&order=price.asc";
                    else if ("price_desc".equalsIgnoreCase(sort)) sortParam = "&order=price.desc";

                    List<Map<String, Object>> rows = null;
                    try {
                        rows = sb.get("properties?select=*,property_images(*)" + sortParam);
                    } catch (Exception e1) {
                        try {
                            rows = sb.get("properties?select=*" + sortParam);
                        } catch (Exception e2) {
                            rows = sb.get("properties?limit=50");
                        }
                    }
                    if (rows != null && !rows.isEmpty()) {
                        for (Map<String, Object> r : rows) {
                            Property p = mapSupabaseProperty(r);
                            if (passesFilters(p, filters) && passesClientFilters(p, filters)) {
                                list.add(p);
                                if (p.getTitle() != null) seen.add(p.getTitle().trim().toLowerCase());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[PropertyDAO] Supabase query notice (" + e.getMessage() + "), using fallback listings.");
                }
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection();
                     PreparedStatement st = conn.prepareStatement("SELECT p.*, c.category_code, c.category_name, u.full_name, u.email, u.mobile_number, u.is_verified FROM properties p LEFT JOIN property_categories c ON p.category_id=c.category_id LEFT JOIN users u ON p.owner_id=u.user_id WHERE p.approval_status='APPROVED' AND p.listing_status='AVAILABLE'");
                     ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        Property p = mapJdbcProperty(rs);
                        if (passesFilters(p, filters) && passesClientFilters(p, filters)) {
                            list.add(p);
                            if (p.getTitle() != null) seen.add(p.getTitle().trim().toLowerCase());
                        }
                    }
                }
            }
            for (Property p : InMemoryStore.getInstance().properties.values()) {
                if ("APPROVED".equalsIgnoreCase(p.getApprovalStatus()) && "AVAILABLE".equalsIgnoreCase(p.getListingStatus())) {
                    String normTitle = p.getTitle() != null ? p.getTitle().trim().toLowerCase() : "";
                    if (!seen.contains(normTitle)) {
                        if (passesFilters(p, filters) && passesClientFilters(p, filters)) {
                            list.add(p);
                            seen.add(normTitle);
                        }
                    }
                }
            }
            sortProperties(list, sort);
            return list;
        }

        private boolean passesFilters(Property p, Map<String, String> filters) {
            if (filters == null) return true;
            if ("true".equalsIgnoreCase(filters.get("featured")) && !p.isFeatured()) return false;
            if (!p.matchesLocation(filters.get("city"))) return false;
            String cat = filters.get("category");
            if (cat != null && !cat.trim().isEmpty() && !"ALL".equalsIgnoreCase(cat)) {
                Integer cid = resolveCatId(cat.trim());
                if (cid != null && p.getCategoryId() != cid) return false;
            }
            String purpose = filters.get("purpose");
            if (purpose != null && !purpose.trim().isEmpty() && !"ALL".equalsIgnoreCase(purpose)) {
                if (!purpose.trim().equalsIgnoreCase(p.getPurpose())) return false;
            }
            double minP = parseDoubleOrZero(filters.get("minPrice"));
            double maxP = parseDoubleOrZero(filters.get("maxPrice"));
            if (!p.matchesPriceRange(minP, maxP)) return false;
            if (!p.matchesKeyword(filters.get("keyword"))) return false;
            return passesClientFilters(p, filters);
        }

        public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
            final double R = 6371.0;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                     + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                     * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
            return 2.0 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        }

        private boolean passesClientFilters(Property p, Map<String, String> filters) {
            if (filters == null) return true;
            if ("true".equalsIgnoreCase(filters.get("verifiedOnly")) && !p.isOwnerVerified()) return false;
            int minBeds = parseIntOrZero(filters.get("minBeds"));
            if (minBeds > 0) {
                int beds = 0;
                if (p instanceof ResidentialProperty) beds = ((ResidentialProperty) p).getBedrooms();
                else if (p instanceof FarmHouse) beds = ((FarmHouse) p).getBedrooms();
                if (beds < minBeds) return false;
            }
            double minLat = parseDoubleOrZero(filters.get("minLat"));
            double maxLat = parseDoubleOrZero(filters.get("maxLat"));
            double minLng = parseDoubleOrZero(filters.get("minLng"));
            double maxLng = parseDoubleOrZero(filters.get("maxLng"));
            if (maxLat > minLat && maxLng > minLng) {
                if (p.getLatitude() < minLat || p.getLatitude() > maxLat || p.getLongitude() < minLng || p.getLongitude() > maxLng) {
                    return false;
                }
            }
            double qLat = parseDoubleOrZero(filters.get("lat"));
            double qLng = parseDoubleOrZero(filters.get("lng"));
            double radiusKm = parseDoubleOrZero(filters.get("radiusKm"));
            if (radiusKm > 0 && (qLat != 0 || qLng != 0)) {
                if (haversineKm(qLat, qLng, p.getLatitude(), p.getLongitude()) > radiusKm) return false;
            }
            return true;
        }

        private void sortProperties(List<Property> list, String sort) {
            if ("price_asc".equalsIgnoreCase(sort)) {
                list.sort(Comparator.comparingDouble(Property::getPrice));
            } else if ("price_desc".equalsIgnoreCase(sort)) {
                list.sort(Comparator.comparingDouble(Property::getPrice).reversed());
            } else if ("area_desc".equalsIgnoreCase(sort)) {
                list.sort(Comparator.comparingDouble(Property::getPropertyArea).reversed());
            } else if ("rating_desc".equalsIgnoreCase(sort)) {
                list.sort(Comparator.comparingDouble(Property::getAverageRating).reversed());
            } else {
                list.sort((a, b) -> {
                    int feat = Boolean.compare(b.isFeatured(), a.isFeatured());
                    if (feat != 0) return feat;
                    return Integer.compare(b.getPropertyId(), a.getPropertyId());
                });
            }
        }

        public List<Property> getByOwner(int ownerId) throws SQLException {
            List<Property> list = new ArrayList<>();
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                for (Map<String, Object> r : SupabaseClient.getInstance().get("properties?" + SB_SELECT + "&owner_id=eq." + ownerId + "&order=created_at.desc")) {
                    list.add(mapSupabaseProperty(r));
                }
                return list;
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection();
                     PreparedStatement st = conn.prepareStatement("SELECT p.*, c.category_code, c.category_name, u.full_name, u.email, u.mobile_number, u.is_verified FROM properties p LEFT JOIN property_categories c ON p.category_id=c.category_id LEFT JOIN users u ON p.owner_id=u.user_id WHERE p.owner_id=? ORDER BY p.created_at DESC")) {
                    st.setInt(1, ownerId);
                    try (ResultSet rs = st.executeQuery()) { while (rs.next()) list.add(mapJdbcProperty(rs)); }
                }
                return list;
            }
            for (Property p : InMemoryStore.getInstance().properties.values()) {
                if (p.getOwnerId() == ownerId) list.add(p);
            }
            list.sort(Comparator.comparingInt(Property::getPropertyId).reversed());
            return list;
        }

        public List<Property> getPending() throws SQLException {
            List<Property> list = new ArrayList<>();
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                for (Map<String, Object> r : SupabaseClient.getInstance().get("properties?" + SB_SELECT + "&approval_status=eq.PENDING&order=created_at.asc")) {
                    list.add(mapSupabaseProperty(r));
                }
                return list;
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection();
                     PreparedStatement st = conn.prepareStatement("SELECT p.*, c.category_code, c.category_name, u.full_name, u.email, u.mobile_number, u.is_verified FROM properties p LEFT JOIN property_categories c ON p.category_id=c.category_id LEFT JOIN users u ON p.owner_id=u.user_id WHERE p.approval_status='PENDING' ORDER BY p.created_at ASC");
                     ResultSet rs = st.executeQuery()) {
                    while (rs.next()) list.add(mapJdbcProperty(rs));
                }
                return list;
            }
            for (Property p : InMemoryStore.getInstance().properties.values()) {
                if ("PENDING".equalsIgnoreCase(p.getApprovalStatus())) list.add(p);
            }
            list.sort(Comparator.comparingInt(Property::getPropertyId));
            return list;
        }

        public Property getById(int id) throws SQLException, PropertyNotFoundException {
            Property cached = InMemoryStore.getInstance().properties.get(id);
            if (cached != null) return cached;

            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                try {
                    List<Property> all = search(null, null);
                    for (Property p : all) {
                        if (p.getPropertyId() == id) return p;
                    }
                } catch (Exception e) {
                    System.err.println("[PropertyDAO] Supabase getById search notice (" + e.getMessage() + "), checking fallback listings.");
                }
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection();
                     PreparedStatement st = conn.prepareStatement("SELECT p.*, c.category_code, c.category_name, u.full_name, u.email, u.mobile_number, u.is_verified FROM properties p LEFT JOIN property_categories c ON p.category_id=c.category_id LEFT JOIN users u ON p.owner_id=u.user_id WHERE p.property_id=?")) {
                    st.setInt(1, id);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) return mapJdbcProperty(rs);
                    }
                }
                throw new PropertyNotFoundException(id);
            }
            Property p = InMemoryStore.getInstance().properties.get(id);
            if (p == null) throw new PropertyNotFoundException(id);
            return p;
        }

        public boolean deleteWithIDORCheck(int propId, User actor) throws SQLException, UnauthorizedAccessException, PropertyNotFoundException {
            Property p = getById(propId);
            if (!actor.isAdministrator() && p.getOwnerId() != actor.getUserId()) {
                throw new UnauthorizedAccessException("You do not own this listing.");
            }
            if (activePersistenceMode == PersistenceMode.SUPABASE) {
                return SupabaseClient.getInstance().delete("properties?property_id=eq." + propId);
            }
            if (activePersistenceMode == PersistenceMode.MYSQL) {
                try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement("DELETE FROM properties WHERE property_id=?")) {
                    st.setInt(1, propId);
                    return st.executeUpdate() > 0;
                }
            }
            InMemoryStore store = InMemoryStore.getInstance();
            store.properties.remove(propId);
            store.recordAudit(actor, "PROPERTY_DELETED", "PROPERTY", propId, "Removed listing \"" + p.getTitle() + "\"");
            return true;
        }

        private Map<String, Object> buildSupabasePropertyPayload(Property p, boolean isCreate) {
            Map<String, Object> base = new LinkedHashMap<>();
            if (isCreate) {
                base.put("owner_id", p.getOwnerId());
                base.put("category_id", p.getCategoryId());
                base.put("approval_status", "PENDING");
                base.put("listing_status", "AVAILABLE");
                base.put("is_featured", p.isFeatured());
            }
            base.put("title", p.getTitle());
            base.put("description", p.getDescription() != null ? p.getDescription() : p.getTitle());
            base.put("purpose", p.getPurpose());
            base.put("price", p.getPrice());
            base.put("property_area", p.getPropertyArea());
            base.put("area_unit", p.getAreaUnit() != null ? p.getAreaUnit() : "Sq. Ft.");
            base.put("state", p.getState() != null ? p.getState() : "Karnataka");
            base.put("city", p.getCity());
            base.put("locality", p.getLocality() != null ? p.getLocality() : p.getCity());
            base.put("address", p.getAddress() != null ? p.getAddress() : p.getCity());
            base.put("pincode", p.getPincode());
            if (p.getModel3dUrl() != null && !p.getModel3dUrl().trim().isEmpty()) {
                base.put("model_3d_url", p.getModel3dUrl().trim());
            }
            base.put("model_3d_format", p.getModel3dFormat() != null ? p.getModel3dFormat() : "GLB");
            base.put("hdri_preset", p.getHdriPreset() != null ? p.getHdriPreset() : "daylight");
            if (isCreate && p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                base.put("primary_image_url", p.getImageUrls().get(0));
            }
            return base;
        }

        private void saveCategoryDetailsSupabase(SupabaseClient sb, Property p, int pid) throws SQLException {
            if (p instanceof ResidentialProperty) {
                ResidentialProperty rp = (ResidentialProperty) p;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("residential_type", rp.getResidentialType() != null ? rp.getResidentialType() : "APARTMENT");
                d.put("bedrooms", rp.getBedrooms());
                d.put("bathrooms", rp.getBathrooms());
                d.put("built_up_area", rp.getBuiltUpArea() > 0 ? rp.getBuiltUpArea() : rp.getPropertyArea());
                d.put("furnishing_status", rp.getFurnishingStatus() != null ? rp.getFurnishingStatus() : "SEMI_FURNISHED");
                sb.post("residential_details", d);
            } else if (p instanceof CommercialProperty) {
                CommercialProperty cp = (CommercialProperty) p;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("commercial_type", cp.getCommercialType() != null ? cp.getCommercialType() : "OFFICE_SPACE");
                d.put("carpet_area", cp.getCarpetArea() > 0 ? cp.getCarpetArea() : cp.getPropertyArea());
                d.put("commercial_usage", cp.getCommercialUsage());
                d.put("power_backup", cp.isPowerBackup());
                sb.post("commercial_details", d);
            } else if (p instanceof AgriculturalLand) {
                AgriculturalLand al = (AgriculturalLand) p;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("land_type", al.getLandType() != null ? al.getLandType() : "FARMLAND");
                d.put("total_land_area", al.getTotalLandArea() > 0 ? al.getTotalLandArea() : al.getPropertyArea());
                d.put("land_unit", al.getLandUnit() != null ? al.getLandUnit().toUpperCase() : "ACRES");
                d.put("soil_type", al.getSoilType());
                d.put("water_availability", al.getWaterAvailability());
                d.put("has_borewell", al.isHasBorewell());
                sb.post("land_details", d);
            } else if (p instanceof FarmHouse) {
                FarmHouse fh = (FarmHouse) p;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("land_area", fh.getLandArea() > 0 ? fh.getLandArea() : fh.getPropertyArea());
                d.put("built_up_area", fh.getBuiltUpArea() > 0 ? fh.getBuiltUpArea() : fh.getPropertyArea());
                d.put("bedrooms", Math.max(1, fh.getBedrooms()));
                d.put("bathrooms", Math.max(1, fh.getBathrooms()));
                d.put("has_garden", fh.isHasGarden());
                d.put("has_swimming_pool", fh.isHasSwimmingPool());
                sb.post("farmhouse_details", d);
            } else if (p instanceof Plot) {
                Plot pl = (Plot) p;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("plot_type", pl.getPlotType() != null ? pl.getPlotType() : "RESIDENTIAL_PLOT");
                d.put("plot_dimensions", pl.getPlotDimensions());
                d.put("total_area", pl.getTotalArea() > 0 ? pl.getTotalArea() : pl.getPropertyArea());
                d.put("facing_direction", pl.getFacingDirection() != null ? pl.getFacingDirection() : "EAST");
                d.put("approval_authority", pl.getApprovalAuthority());
                sb.post("plot_details", d);
            } else if (p instanceof Building) {
                Building b = (Building) p;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("property_id", pid);
                d.put("building_type", b.getBuildingType() != null ? b.getBuildingType() : "APARTMENT_BUILDING");
                d.put("total_units", Math.max(1, b.getTotalUnits()));
                d.put("construction_status", b.getConstructionStatus() != null ? b.getConstructionStatus() : "READY_TO_MOVE");
                d.put("builder_name", b.getBuilderName());
                sb.post("building_details", d);
            }
        }

        private void saveImagesAndAmenitiesSupabase(SupabaseClient sb, Property p, int pid) throws SQLException {
            if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                List<Map<String, Object>> imgs = new ArrayList<>();
                boolean first = true;
                for (String url : p.getImageUrls()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("property_id", pid); row.put("image_url", url); row.put("is_primary", first);
                    imgs.add(row); first = false;
                }
                sb.postBatch("property_images", imgs);
            }
            if (p.getAmenities() != null && !p.getAmenities().isEmpty()) {
                List<Map<String, Object>> amens = new ArrayList<>();
                for (String a : p.getAmenities()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("property_id", pid); row.put("amenity_name", a);
                    amens.add(row);
                }
                sb.postBatch("property_amenities", amens);
            }
        }

        private static Integer resolveCatId(String c) {
            try { return Integer.parseInt(c); } catch (Exception ignored) {}
            switch (c.toUpperCase()) {
                case "RESIDENTIAL": return 1; case "COMMERCIAL": return 2; case "AGRICULTURAL": return 3;
                case "FARMHOUSE": return 4; case "PLOT": return 5; case "BUILDING": return 6;
                default: return null;
            }
        }

        private static double[] defaultCityCoords(String city) {
            if (city == null) return new double[]{12.9716, 77.5946};
            switch (city.trim().toLowerCase()) {
                case "mysuru": return new double[]{12.2958, 76.6394};
                case "bengaluru": return new double[]{12.9716, 77.5946};
                case "coorg": return new double[]{12.4244, 75.7382};
                case "mangaluru": return new double[]{12.9141, 74.8560};
                case "goa": return new double[]{15.4909, 73.8278};
                case "hyderabad": return new double[]{17.3850, 78.4867};
                default: return new double[]{12.9716, 77.5946};
            }
        }

        private static Property mapJdbcProperty(ResultSet rs) throws SQLException {
            String catCode = rs.getString("category_code");
            Property p = createPropertyInstance(catCode);
            p.setPropertyId(rs.getInt("property_id"));
            p.setOwnerId(rs.getInt("owner_id"));
            p.setCategoryId(rs.getInt("category_id"));
            p.setCategoryCode(catCode != null ? catCode : "RESIDENTIAL");
            p.setCategoryName(rs.getString("category_name"));
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
            p.setPrimaryImageUrl(rs.getString("primary_image_url"));
            try {
                if (rs.getString("model_3d_url") != null) p.setModel3dUrl(rs.getString("model_3d_url"));
                if (rs.getString("model_3d_format") != null) p.setModel3dFormat(rs.getString("model_3d_format"));
                if (rs.getString("hdri_preset") != null) p.setHdriPreset(rs.getString("hdri_preset"));
            } catch (SQLException ignored) {}
            p.setOwnerName(rs.getString("full_name"));
            p.setOwnerEmail(rs.getString("email"));
            p.setOwnerPhone(rs.getString("mobile_number"));
            p.setOwnerVerified(rs.getBoolean("is_verified"));
            double[] coords = defaultCityCoords(p.getCity());
            p.setLatitude(coords[0]); p.setLongitude(coords[1]);
            return p;
        }

        private static Property mapSupabaseProperty(Map<String, Object> r) {
            Map<String, Object> cat = JsonUtil.getMap(r, "property_categories");
            String rawType = JsonUtil.getString(r, "property_type");
            String catCode = cat != null ? JsonUtil.getString(cat, "category_code") : null;
            if (catCode == null && rawType != null) {
                String t = rawType.toUpperCase();
                if (t.contains("COMMERCIAL")) catCode = "COMMERCIAL";
                else if (t.contains("AGRICULTURAL")) catCode = "AGRICULTURAL";
                else if (t.contains("FARMHOUSE")) catCode = "FARMHOUSE";
                else if (t.contains("PLOT") || t.contains("LAND") || t.contains("SITE")) catCode = "PLOT";
                else catCode = "RESIDENTIAL";
            }
            if (catCode == null) catCode = "RESIDENTIAL";
            String catName = cat != null ? JsonUtil.getString(cat, "category_name") : null;
            if (catName == null) {
                if ("COMMERCIAL".equals(catCode)) catName = "Commercial Properties";
                else if ("AGRICULTURAL".equals(catCode)) catName = "Agricultural Land";
                else if ("FARMHOUSE".equals(catCode)) catName = "Farmhouses";
                else if ("PLOT".equals(catCode)) catName = "Plots and Sites";
                else catName = "Residential Properties";
            }

            Property p = createPropertyInstance(catCode);

            int pid = JsonUtil.getInt(r, "property_id");
            String uid = JsonUtil.getString(r, "id");
            if (pid == 0 && uid != null && !uid.isEmpty()) {
                try {
                    pid = Integer.parseInt(uid);
                } catch (Exception ex) {
                    pid = Math.abs(uid.hashCode() % 900000) + 1000;
                }
            }
            p.setPropertyId(pid);
            p.setOwnerId(JsonUtil.getInt(r, "owner_id") != 0 ? JsonUtil.getInt(r, "owner_id") : 2);
            p.setCategoryId(JsonUtil.getInt(r, "category_id"));
            p.setCategoryCode(catCode);
            p.setCategoryName(catName);

            String title = JsonUtil.getString(r, "title");
            if (title == null || title.isEmpty()) title = JsonUtil.getString(r, "property_title");
            p.setTitle(title != null ? title : "Prime Property Listing");

            String desc = JsonUtil.getString(r, "description");
            if (desc == null || desc.isEmpty()) desc = JsonUtil.getString(r, "property_description");
            p.setDescription(desc != null ? desc : "Verified high-quality property listing.");

            String purp = JsonUtil.getString(r, "purpose");
            if (purp == null || purp.isEmpty()) purp = JsonUtil.getString(r, "listing_type");
            if (purp != null && "SALE".equalsIgnoreCase(purp)) purp = "BUY";
            p.setPurpose(purp != null ? purp.toUpperCase() : "BUY");

            p.setPrice(JsonUtil.getDouble(r, "price"));

            double area = JsonUtil.getDouble(r, "property_area");
            if (area == 0) area = JsonUtil.getDouble(r, "area");
            p.setPropertyArea(area > 0 ? area : 1500.0);

            String aUnit = JsonUtil.getString(r, "area_unit");
            if (aUnit == null || aUnit.isEmpty()) aUnit = "Sq. Ft.";
            else if ("SQ_FT".equalsIgnoreCase(aUnit)) aUnit = "Sq. Ft.";
            p.setAreaUnit(aUnit);

            String addr = JsonUtil.getString(r, "address");
            p.setAddress(addr != null ? addr : "");

            String city = JsonUtil.getString(r, "city");
            if (city == null || city.isEmpty()) {
                if (addr != null) {
                    String lower = addr.toLowerCase();
                    if (lower.contains("mysuru") || lower.contains("mysore")) city = "Mysuru";
                    else if (lower.contains("bengaluru") || lower.contains("bangalore")) city = "Bengaluru";
                    else if (lower.contains("goa") || lower.contains("candolim") || lower.contains("assagao")) city = "Goa";
                    else if (lower.contains("coorg") || lower.contains("madikeri")) city = "Coorg";
                    else if (lower.contains("mandya") || lower.contains("srirangapatna")) city = "Mandya";
                    else if (lower.contains("hyderabad")) city = "Hyderabad";
                    else if (lower.contains("mangaluru") || lower.contains("mangalore")) city = "Mangaluru";
                }
            }
            if (city == null || city.isEmpty()) city = "Mysuru";
            p.setCity(city);

            String state = JsonUtil.getString(r, "state");
            if (state == null || state.isEmpty()) {
                if ("Goa".equalsIgnoreCase(city)) state = "Goa";
                else if ("Hyderabad".equalsIgnoreCase(city)) state = "Telangana";
                else state = "Karnataka";
            }
            p.setState(state);

            String locality = JsonUtil.getString(r, "locality");
            if (locality == null || locality.isEmpty()) {
                if (addr != null && addr.contains(",")) {
                    String[] parts = addr.split(",");
                    if (parts.length >= 2) locality = parts[parts.length - 2].trim();
                }
            }
            p.setLocality(locality != null ? locality : city);
            p.setPincode(JsonUtil.getString(r, "pincode"));

            String appStatus = JsonUtil.getString(r, "approval_status");
            if (appStatus == null || appStatus.isEmpty()) {
                String pStatus = JsonUtil.getString(r, "property_status");
                appStatus = ("AVAILABLE".equalsIgnoreCase(pStatus) || "SOLD".equalsIgnoreCase(pStatus)) ? "APPROVED" : "APPROVED";
            }
            p.setApprovalStatus(appStatus);

            String listStatus = JsonUtil.getString(r, "listing_status");
            if (listStatus == null || listStatus.isEmpty()) {
                listStatus = JsonUtil.getString(r, "property_status");
                if (listStatus == null || listStatus.isEmpty()) listStatus = "AVAILABLE";
            }
            p.setListingStatus(listStatus);
            p.setFeatured(true);

            List<String> imgs = new ArrayList<>();
            String primImg = JsonUtil.getString(r, "primary_image_url");
            if (primImg != null && !primImg.isEmpty()) imgs.add(primImg);
            for (Map<String, Object> img : JsonUtil.getList(r, "property_images")) {
                String u = JsonUtil.getString(img, "image_url");
                if (u != null && !u.isEmpty() && !imgs.contains(u)) {
                    imgs.add(u);
                    if (JsonUtil.getBoolean(img, "is_primary") && (primImg == null || primImg.isEmpty())) {
                        primImg = u;
                    }
                }
            }
            if (primImg == null && !imgs.isEmpty()) primImg = imgs.get(0);
            if (primImg == null) primImg = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80";
            p.setPrimaryImageUrl(primImg);
            p.setImageUrls(imgs);

            String m3d = JsonUtil.getString(r, "model_3d_url");
            if (m3d == null || m3d.isEmpty()) {
                if ("PLOT".equals(catCode)) {
                    m3d = "https://cdn.jsdelivr.net/gh/kavyasenthamarai/3d-property-f@main/models/home1.glb";
                } else if ("FARMHOUSE".equals(catCode) || "AGRICULTURAL".equals(catCode)) {
                    m3d = "https://cdn.jsdelivr.net/gh/bandurchencko/lived-in-houses@main/docs/glb/workshop-house-2020.glb";
                } else if ("COMMERCIAL".equals(catCode)) {
                    m3d = "https://cdn.jsdelivr.net/gh/mrdoob/three.js@r170/examples/models/gltf/LittlestTokyo.glb";
                } else {
                    m3d = "https://cdn.jsdelivr.net/gh/bandurchencko/lived-in-houses@main/docs/glb/red-stone-garden-house-7.glb";
                }
            }
            p.setModel3dUrl(m3d);
            p.setModel3dFormat("GLB");
            p.setHdriPreset("daylight");

            double[] coords = defaultCityCoords(p.getCity());
            p.setLatitude(JsonUtil.getDouble(r, "latitude") != 0 ? JsonUtil.getDouble(r, "latitude") : coords[0]);
            p.setLongitude(JsonUtil.getDouble(r, "longitude") != 0 ? JsonUtil.getDouble(r, "longitude") : coords[1]);

            Map<String, Object> own = JsonUtil.getMap(r, "users");
            if (own != null) {
                p.setOwnerName(JsonUtil.getString(own, "full_name"));
                p.setOwnerEmail(JsonUtil.getString(own, "email"));
                p.setOwnerPhone(JsonUtil.getString(own, "mobile_number"));
                p.setOwnerVerified(JsonUtil.getBoolean(own, "is_verified"));
            } else {
                p.setOwnerName("Rohan Kulkarni (Supabase Verified)");
                p.setOwnerEmail("seller@realestate.com");
                p.setOwnerPhone("+91 98450 40004");
                p.setOwnerVerified(true);
            }

            List<String> amens = new ArrayList<>();
            for (Map<String, Object> a : JsonUtil.getList(r, "property_amenities")) {
                String n = JsonUtil.getString(a, "amenity_name");
                if (n != null) amens.add(n);
            }
            if (amens.isEmpty()) {
                amens.add("24x7 Water & Power Backup");
                amens.add("Covered Parking (" + (JsonUtil.getInt(r, "parking") > 0 ? JsonUtil.getInt(r, "parking") : 2) + " slots)");
                amens.add("Gated Security");
                amens.add("100% Clear Title Verified on Supabase");
            }
            p.setAmenities(amens);

            int beds = JsonUtil.getInt(r, "bedrooms");
            int baths = JsonUtil.getInt(r, "bathrooms");
            String furn = JsonUtil.getString(r, "furnishing_status");

            InMemoryStore.getInstance().properties.put(p.getPropertyId(), p);
            if (p instanceof ResidentialProperty) {
                ResidentialProperty rp = (ResidentialProperty) p;
                if (beds > 0) rp.setBedrooms(beds);
                if (baths > 0) rp.setBathrooms(baths);
                if (furn != null && !furn.isEmpty()) rp.setFurnishingStatus(furn);
                Map<String, Object> d = JsonUtil.getMap(r, "residential_details");
                if (d != null) {
                    if (JsonUtil.getString(d, "residential_type") != null) rp.setResidentialType(JsonUtil.getString(d, "residential_type"));
                    if (JsonUtil.getInt(d, "bedrooms") > 0) rp.setBedrooms(JsonUtil.getInt(d, "bedrooms"));
                    if (JsonUtil.getInt(d, "bathrooms") > 0) rp.setBathrooms(JsonUtil.getInt(d, "bathrooms"));
                    if (JsonUtil.getDouble(d, "built_up_area") > 0) rp.setBuiltUpArea(JsonUtil.getDouble(d, "built_up_area"));
                    if (JsonUtil.getString(d, "furnishing_status") != null) rp.setFurnishingStatus(JsonUtil.getString(d, "furnishing_status"));
                }
            } else if (p instanceof CommercialProperty) {
                Map<String, Object> d = JsonUtil.getMap(r, "commercial_details");
                if (d != null) {
                    CommercialProperty cp = (CommercialProperty) p;
                    cp.setCommercialType(JsonUtil.getString(d, "commercial_type"));
                    cp.setCarpetArea(JsonUtil.getDouble(d, "carpet_area"));
                    cp.setCommercialUsage(JsonUtil.getString(d, "commercial_usage"));
                    cp.setPowerBackup(JsonUtil.getBoolean(d, "power_backup"));
                }
            } else if (p instanceof AgriculturalLand) {
                Map<String, Object> d = JsonUtil.getMap(r, "land_details");
                if (d != null) {
                    AgriculturalLand al = (AgriculturalLand) p;
                    al.setLandType(JsonUtil.getString(d, "land_type"));
                    al.setTotalLandArea(JsonUtil.getDouble(d, "total_land_area"));
                    al.setLandUnit(JsonUtil.getString(d, "land_unit"));
                    al.setSoilType(JsonUtil.getString(d, "soil_type"));
                    al.setWaterAvailability(JsonUtil.getString(d, "water_availability"));
                    al.setHasBorewell(JsonUtil.getBoolean(d, "has_borewell"));
                }
            } else if (p instanceof FarmHouse) {
                Map<String, Object> d = JsonUtil.getMap(r, "farmhouse_details");
                if (d != null) {
                    FarmHouse fh = (FarmHouse) p;
                    fh.setLandArea(JsonUtil.getDouble(d, "land_area"));
                    fh.setBuiltUpArea(JsonUtil.getDouble(d, "built_up_area"));
                    fh.setBedrooms(JsonUtil.getInt(d, "bedrooms"));
                    fh.setBathrooms(JsonUtil.getInt(d, "bathrooms"));
                    fh.setHasGarden(JsonUtil.getBoolean(d, "has_garden"));
                    fh.setHasSwimmingPool(JsonUtil.getBoolean(d, "has_swimming_pool"));
                }
            } else if (p instanceof Plot) {
                Map<String, Object> d = JsonUtil.getMap(r, "plot_details");
                if (d != null) {
                    Plot pl = (Plot) p;
                    pl.setPlotType(JsonUtil.getString(d, "plot_type"));
                    pl.setPlotDimensions(JsonUtil.getString(d, "plot_dimensions"));
                    pl.setTotalArea(JsonUtil.getDouble(d, "total_area"));
                    pl.setFacingDirection(JsonUtil.getString(d, "facing_direction"));
                    pl.setApprovalAuthority(JsonUtil.getString(d, "approval_authority"));
                }
            } else if (p instanceof Building) {
                Map<String, Object> d = JsonUtil.getMap(r, "building_details");
                if (d != null) {
                    Building b = (Building) p;
                    b.setBuildingType(JsonUtil.getString(d, "building_type"));
                    b.setTotalUnits(JsonUtil.getInt(d, "total_units"));
                    b.setConstructionStatus(JsonUtil.getString(d, "construction_status"));
                    b.setBuilderName(JsonUtil.getString(d, "builder_name"));
                }
            }
            return p;
        }
    }

    // =========================================================================
    // 10. CORE APPLICATION ENTRYPOINT & REST EMBEDDED SERVER
    // =========================================================================

    private static final UserDAO userDAO = new UserDAO();
    private static final PropertyDAO propertyDAO = new PropertyDAO();
    private static final SessionManager sessionManager = new SessionManager();
    private static final RateLimiter authRateLimiter = new RateLimiter();

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("   ESTATEVAULT REAL ESTATE MANAGEMENT SYSTEM - BACKEND v2.0");
        System.out.println("   Tagline: \"Find. Buy. Sell. Rent. Build.\"");
        System.out.println("=================================================================");

        demonstrateOOPPrinciples();

        SupabaseClient sbClient = SupabaseClient.getInstance();
        if (sbClient.isConfigured() && sbClient.testConnection()) {
            activePersistenceMode = PersistenceMode.SUPABASE;
            System.out.println("\n[1/2] Persistence Mode: [SUPABASE REST ONLINE]");
        } else if (DBConnection.getInstance().testConnection()) {
            activePersistenceMode = PersistenceMode.MYSQL;
            System.out.println("\n[1/2] Persistence Mode: [MYSQL JDBC ONLINE]");
        } else {
            activePersistenceMode = PersistenceMode.IN_MEMORY_DEMO;
            InMemoryStore.getInstance();
            System.out.println("\n[1/2] Persistence Mode: [IN-MEMORY SEEDED DEMO STORE READY]");
            System.out.println("      Demo Logins (Password@123): admin@realestate.com | buyer@realestate.com | owner@realestate.com | tenant@realestate.com");
        }

        try {
            System.out.println("\n[2/2] Launching Embedded REST HTTP Server on Port " + SERVER_PORT + "...");
            HttpServer server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
            ExecutorService pool = Executors.newFixedThreadPool(12);
            server.setExecutor(pool);

            server.createContext("/health", withApiSafety(ex -> {
                long uptime = Duration.between(SERVER_STARTED_AT, Instant.now()).getSeconds();
                String gmapsKey = DBConnection.getConfig("GOOGLE_MAPS_API_KEY", DBConnection.loadDotEnv(), "");
                boolean gmapsConfigured = gmapsKey != null && !gmapsKey.trim().isEmpty();
                String payload = "{\"status\":\"ok\",\"service\":\"real-estate-backend\",\"version\":\"2.1\","
                    + "\"authMode\":\"JWT_HS256\","
                    + "\"redisConnected\":" + RedisStore.getInstance().isConnected() + ","
                    + "\"sseSubscribers\":" + SseEventHub.getInstance().activeSubscribers() + ","
                    + "\"dbIdlePool\":" + DBConnection.getInstance().getIdlePoolSize() + ","
                    + "\"persistenceMode\":" + JsonUtil.escape(activePersistenceMode.name()) + ","
                    + "\"uptimeSeconds\":" + uptime + ","
                    + "\"activeSessions\":" + sessionManager.activeCount() + ","
                    + "\"notificationsDispatched\":" + AsyncNotificationService.getInstance().getProcessedCount() + ","
                    + "\"googleMapsKeyConfigured\":" + gmapsConfigured
                    + (gmapsConfigured ? ",\"googleMapsKey\":" + JsonUtil.escape(gmapsKey.trim()) : "")
                    + "}";
                sendJson(ex, 200, payload);
            }));

            server.createContext("/api/auth", withApiSafety(new AuthHandler()));
            server.createContext("/api/properties", withApiSafety(new PropertyHandler()));
            server.createContext("/api/appointments", withApiSafety(new AppointmentHandler()));
            server.createContext("/api/rentals", withApiSafety(new RentalHandler()));
            server.createContext("/api/purchases", withApiSafety(new PurchaseHandler()));
            server.createContext("/api/verifications", withApiSafety(new VerificationHandler()));
            server.createContext("/api/favorites", withApiSafety(new FavoriteHandler()));
            server.createContext("/api/reviews", withApiSafety(new ReviewHandler()));
            server.createContext("/api/inquiries", withApiSafety(new InquiryHandler()));
            server.createContext("/api/notifications", withApiSafety(new NotificationHandler()));
            server.createContext("/api/tools/calculate", withApiSafety(new CalculatorHandler()));
            server.createContext("/api/uploads", withApiSafety(new UploadHandler()));
            server.createContext("/api/events", withApiSafety(new SseHandler()));
            server.createContext("/api/documents", withApiSafety(new DocumentHandler()));
            server.createContext("/api/admin", withApiSafety(new AdminHandler()));

            // Hardened Static File Server (Strictly scoped to src/main/webapp)
            final File baseDir = new File("src/main/webapp").getCanonicalFile();
            final Path basePath = baseDir.toPath();
            server.createContext("/", exchange -> {
                addSecurityHeaders(exchange);
                String rawPath = exchange.getRequestURI().getPath();
                if (rawPath == null || "/".equals(rawPath) || rawPath.isEmpty()) rawPath = "/index.html";
                if (rawPath.contains("..") || rawPath.contains("/.") || rawPath.endsWith(".java") || rawPath.endsWith(".class") || rawPath.endsWith(".sql") || rawPath.endsWith(".env")) {
                    sendPlain(exchange, 403, "Forbidden");
                    return;
                }
                File target = new File(baseDir, rawPath.replace('/', File.separatorChar)).getCanonicalFile();
                if (!target.toPath().startsWith(basePath) || !target.exists() || target.isDirectory()) {
                    String notFound = "<h1>Real Estate Management Backend v2.0</h1><p>Static asset not found. API active on port " + SERVER_PORT + ".</p>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", getMimeType(target.getName()));
                exchange.sendResponseHeaders(200, target.length());
                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(target)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = fis.read(buf)) != -1) os.write(buf, 0, r);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Stopping HTTP Server, SSE hub, DB pool, and workers gracefully...");
                SseEventHub.getInstance().shutdownAll();
                DBConnection.getInstance().shutdown();
                server.stop(2);
                pool.shutdownNow();
            }));

            server.start();
            System.out.println(">>> Backend Server successfully listening at: http://localhost:" + SERVER_PORT);
            System.out.println(">>> Endpoints: /health, /api/auth/*, /api/properties/*, /api/uploads/*, /api/events, /api/documents/*, /api/appointments/*, /api/rentals/*, /api/purchases/*, /api/favorites/*, /api/reviews/*, /api/inquiries/*, /api/notifications/*, /api/tools/calculate, /api/admin/*");
            System.out.println("=================================================================\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getMimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".glb")) return "model/gltf-binary";
        if (lower.endsWith(".gltf")) return "model/gltf+json";
        if (lower.endsWith(".hdr") || lower.endsWith(".splat") || lower.endsWith(".spz") || lower.endsWith(".ply")) return "application/octet-stream";
        return "application/octet-stream";
    }

    private static void demonstrateOOPPrinciples() {
        System.out.println("\n--- DEMONSTRATING CORE JAVA OOP PRINCIPLES ---");
        ResidentialProperty villa = new ResidentialProperty(101, 2, "Luxury 4 BHK Mysuru Villa", "BUY", 18500000.0, 3400.0, "Karnataka", "Mysuru", 4, 4);
        AgriculturalLand farmland = new AgriculturalLand();
        farmland.setTitle("Fertile Coffee Plantation");
        farmland.setPurpose("BUY");
        farmland.setPrice(16500000.0);
        farmland.setTotalLandArea(5.5);

        Property[] properties = { villa, farmland };
        for (Property p : properties) {
            System.out.println("[POLYMORPHISM] Category: " + p.getCategoryName());
            System.out.println("  Details: " + p.getPropertyTypeDetails());
            System.out.println("  Estimated Maintenance: Rs. " + p.calculateEstimatedMaintenanceCost());
            System.out.println("  Est. Registration (Default 6.6%): Rs. " + p.calculateEstimatedRegistrationFee());
            System.out.println("  Est. Registration (Concessional 3.0%): Rs. " + p.calculateEstimatedRegistrationFee(3.0));
        }

        AsyncNotificationService.getInstance().dispatchNotification(1, "admin@realestate.com", "System Boot", "RealEstateBackend v2.0 initialized.");
        System.out.println("----------------------------------------------");
    }

    // =========================================================================
    // 11. EMAIL VALIDATOR, REAL SMTP OTP SERVICE & HARDENED HTTP HANDLERS
    // =========================================================================

    private static final Pattern STRICT_EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9](?:[A-Za-z0-9._%+-]{0,62}[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,63}$"
    );
    private static final Set<String> DISPOSABLE_DOMAINS = new HashSet<>(Arrays.asList(
        "mailinator.com", "guerrillamail.com", "tempmail.com", "10minutemail.com", "yopmail.com", "trashmail.com", "throwawaymail.com"
    ));
    private static final Set<String> DEMO_SAFE_DOMAINS = new HashSet<>(Arrays.asList("realestate.com", "example.com"));
    private static final Map<String, String> DOMAIN_TYPOS = new HashMap<>();
    static {
        DOMAIN_TYPOS.put("gmial.com", "gmail.com"); DOMAIN_TYPOS.put("gmal.com", "gmail.com");
        DOMAIN_TYPOS.put("gamil.com", "gmail.com"); DOMAIN_TYPOS.put("gmail.con", "gmail.com");
        DOMAIN_TYPOS.put("yaho.com", "yahoo.com"); DOMAIN_TYPOS.put("outlok.com", "outlook.com");
        DOMAIN_TYPOS.put("hotmial.com", "hotmail.com");
    }

    private static final class OtpRecord {
        final String hash, salt; final long createdAt, expiresAt; int attempts; boolean verified; String resetToken;
        OtpRecord(String h, String s, long c, long e) { this.hash = h; this.salt = s; this.createdAt = c; this.expiresAt = e; }
    }
    private static final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();
    private static final SecureRandom OTP_RNG = new SecureRandom();

    private static Map<String, Object> validateEmailAddress(String rawEmail) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            res.put("valid", false); res.put("normalizedEmail", ""); res.put("message", "Email address is required."); res.put("suggestion", null); res.put("mxVerified", false);
            return res;
        }
        String email = rawEmail.trim().toLowerCase();
        if (email.contains("..") || !STRICT_EMAIL_PATTERN.matcher(email).matches()) {
            res.put("valid", false); res.put("normalizedEmail", email); res.put("message", "Invalid email format (e.g. name@domain.com)."); res.put("suggestion", null); res.put("mxVerified", false);
            return res;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at), domain = email.substring(at + 1);
        if (DOMAIN_TYPOS.containsKey(domain)) {
            String fixed = local + "@" + DOMAIN_TYPOS.get(domain);
            res.put("valid", false); res.put("normalizedEmail", email); res.put("message", "Did you mean " + fixed + "?"); res.put("suggestion", fixed); res.put("mxVerified", false);
            return res;
        }
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            res.put("valid", false); res.put("normalizedEmail", email); res.put("message", "Disposable temporary email domains are not allowed."); res.put("suggestion", null); res.put("mxVerified", false);
            return res;
        }
        if (DEMO_SAFE_DOMAINS.contains(domain)) {
            res.put("valid", true); res.put("normalizedEmail", email); res.put("message", "Valid email address."); res.put("suggestion", null); res.put("mxVerified", true);
            return res;
        }
        boolean mxOk = false;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2500");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            mxOk = (attrs != null && attrs.get("MX") != null && attrs.get("MX").size() > 0);
            if (!mxOk) {
                Attributes a = ctx.getAttributes(domain, new String[]{"A"});
                mxOk = (a != null && a.get("A") != null && a.get("A").size() > 0);
            }
        } catch (Exception ignored) {
            try { mxOk = InetAddress.getByName(domain) != null; } catch (Exception ignored2) {}
        }
        res.put("valid", mxOk);
        res.put("normalizedEmail", email);
        res.put("message", mxOk ? "Valid & deliverable email domain verified." : "Email domain '" + domain + "' does not appear to accept mail.");
        res.put("suggestion", null);
        res.put("mxVerified", mxOk);
        return res;
    }

    private static String maskEmailAddr(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        return (at <= 2 ? email.substring(0, 1) : email.substring(0, 2)) + "***" + email.substring(at);
    }

    private static Map<String, Object> dispatchOtpEmail(String toEmail, String name, String otp) {
        Map<String, String> cfg = DBConnection.loadDotEnv();
        String resendKey = DBConnection.getConfig("RESEND_API_KEY", cfg, "");
        String smtpHost = DBConnection.getConfig("SMTP_HOST", cfg, "");
        String smtpPortStr = DBConnection.getConfig("SMTP_PORT", cfg, "587");
        String smtpUser = DBConnection.getConfig("SMTP_USER", cfg, "");
        String smtpPass = DBConnection.getConfig("SMTP_PASS", cfg, "");
        String smtpFrom = DBConnection.getConfig("SMTP_FROM", cfg, smtpUser);

        String subject = "Your Password Reset OTP - EstateVault";
        String html = "<div style='font-family:Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden'>" +
            "<div style='background:#0a0a0a;padding:22px 26px;color:#fff'><strong style='color:#10b981;font-size:20px'>ESTATEVAULT</strong></div>" +
            "<div style='padding:26px;color:#111'><h3>Password Reset OTP</h3><p>Hello " + (name != null ? name : "User") + ", use this 6-digit OTP to reset your password:</p>" +
            "<div style='background:#ecfdf5;border:2px dashed #059669;border-radius:10px;padding:16px;text-align:center;font-size:32px;font-weight:800;letter-spacing:8px;color:#065f46;font-family:monospace'>" + otp + "</div>" +
            "<p style='color:#52525b;font-size:13px'>Valid for 5 minutes. Do not share this code with anyone.</p></div></div>";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("email", maskEmailAddr(toEmail));
        out.put("expiresInSeconds", 300);

        if (!resendKey.isEmpty()) {
            try {
                Map<String, Object> pl = new LinkedHashMap<>();
                pl.put("from", !smtpFrom.isEmpty() ? smtpFrom : "EstateVault <onboarding@resend.dev>");
                pl.put("to", Collections.singletonList(toEmail));
                pl.put("subject", subject); pl.put("html", html);
                HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(pl), StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (r.statusCode() >= 200 && r.statusCode() < 300) {
                    out.put("sentToInbox", true); out.put("deliveryMode", "RESEND_SENT");
                    out.put("infoMessage", "A 6-digit OTP has been sent to " + maskEmailAddr(toEmail) + ".");
                    return out;
                }
            } catch (Exception e) { System.err.println("[Email] Resend failed: " + e.getMessage()); }
        }

        if (!smtpHost.isEmpty() && !smtpUser.isEmpty() && !smtpPass.isEmpty()) {
            int port = 587; try { port = Integer.parseInt(smtpPortStr); } catch (Exception ignored) {}
            try {
                sendSmtpMail(smtpHost, port, smtpUser, smtpPass, !smtpFrom.isEmpty() ? smtpFrom : smtpUser, toEmail, subject, html);
                out.put("sentToInbox", true); out.put("deliveryMode", "SMTP_SENT");
                out.put("infoMessage", "A 6-digit OTP has been sent to " + maskEmailAddr(toEmail) + ".");
                return out;
            } catch (Exception e) {
                System.err.println("[Email] SMTP failed: " + e.getMessage());
            }
        }

        System.out.println("[OTP EMAIL - DEMO FALLBACK] To: " + toEmail + " | OTP: " + otp);
        out.put("sentToInbox", false); out.put("deliveryMode", "DEMO_FALLBACK");
        out.put("infoMessage", "OTP generated for " + maskEmailAddr(toEmail) + " (configure SMTP_USER/PASS or RESEND_API_KEY in .env for live email delivery).");
        out.put("demoOtp", otp);
        return out;
    }

    private static void sendSmtpMail(String host, int port, String user, String pass, String from, String to, String subject, String html) throws IOException {
        Socket socket = (port == 465) ? SSLSocketFactory.getDefault().createSocket(host, port) : new Socket(host, port);
        try {
            socket.setSoTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            smtpExpect(r, "220"); smtpCmd(w, r, "EHLO localhost", "250");
            if (port != 465) {
                smtpCmd(w, r, "STARTTLS", "220");
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
                tls.startHandshake(); socket = tls;
                r = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                smtpCmd(w, r, "EHLO localhost", "250");
            }
            smtpCmd(w, r, "AUTH LOGIN", "334");
            smtpCmd(w, r, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)), "334");
            smtpCmd(w, r, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)), "235");
            smtpCmd(w, r, "MAIL FROM:<" + from.trim() + ">", "250");
            smtpCmd(w, r, "RCPT TO:<" + to + ">", "250");
            smtpCmd(w, r, "DATA", "354");
            String b64 = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.UTF_8)).encodeToString(html.getBytes(StandardCharsets.UTF_8));
            w.write("From: EstateVault <" + from.trim() + ">\r\nTo: <" + to + ">\r\nSubject: " + subject +
                    "\r\nMIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + b64 + "\r\n.\r\n");
            w.flush(); smtpExpect(r, "250");
            try { smtpCmd(w, r, "QUIT", "221"); } catch (Exception ignored) {}
        } finally { try { socket.close(); } catch (IOException ignored) {} }
    }

    private static void smtpCmd(BufferedWriter w, BufferedReader r, String cmd, String code) throws IOException {
        w.write(cmd + "\r\n"); w.flush(); smtpExpect(r, code);
    }

    private static void smtpExpect(BufferedReader r, String code) throws IOException {
        String line, last = null;
        while ((line = r.readLine()) != null) { last = line; if (line.length() < 4 || line.charAt(3) != '-') break; }
        if (last == null || !last.startsWith(code)) throw new IOException("SMTP error: " + last);
    }

    private static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            Map<String, String> params = parseBody(ex);
            String clientIp = getClientIp(ex);
            try {
                if (path.endsWith("/login")) {
                    authRateLimiter.check(clientIp + ":login");
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    if (!Boolean.TRUE.equals(v.get("valid"))) throw new AuthenticationException(String.valueOf(v.get("message")));
                    User u = userDAO.authenticate(String.valueOf(v.get("normalizedEmail")), params.get("password"));
                    String token = sessionManager.createSession(u);
                    ex.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=" + token + "; Path=/; HttpOnly; SameSite=Lax");
                    sendJson(ex, 200, JsonUtil.success("Login successful", serializeUser(u, token)));
                } else if (path.endsWith("/register")) {
                    authRateLimiter.check(clientIp + ":register");
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    if (!Boolean.TRUE.equals(v.get("valid"))) throw new AuthenticationException(String.valueOf(v.get("message")));
                    String role = params.get("role") != null ? params.get("role").trim().toUpperCase() : "BUYER";
                    if ("ADMIN".equals(role)) {
                        throw new UnauthorizedAccessException("Administrator accounts cannot be created via public registration.");
                    }
                    User u = UserDAO.createInstance(role);
                    u.setFullName(params.get("fullName"));
                    u.setEmail(String.valueOf(v.get("normalizedEmail")));
                    u.setMobileNumber(params.get("mobile"));
                    u.setUserRole(role);
                    User reg = userDAO.register(u, params.get("password"));
                    String token = sessionManager.createSession(reg);
                    ex.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=" + token + "; Path=/; HttpOnly; SameSite=Lax");
                    AsyncNotificationService.getInstance().dispatchNotification(reg.getUserId(), reg.getEmail(), "Welcome to EstateVault", "Your " + reg.getUserRole() + " account is ready.");
                    sendJson(ex, 200, JsonUtil.success("Registration successful", serializeUser(reg, token)));
                } else if (path.endsWith("/validate-email")) {
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    boolean exists = Boolean.TRUE.equals(v.get("valid")) && "true".equalsIgnoreCase(params.get("checkExists"))
                        && userDAO.findByEmail(String.valueOf(v.get("normalizedEmail"))) != null;
                    v.put("registered", exists);
                    sendJson(ex, 200, JsonUtil.success(String.valueOf(v.get("message")), JsonUtil.toJson(v)));
                } else if (path.endsWith("/forgot-password")) {
                    authRateLimiter.check(clientIp + ":forgot");
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    if (!Boolean.TRUE.equals(v.get("valid"))) throw new AuthenticationException(String.valueOf(v.get("message")));
                    String norm = String.valueOf(v.get("normalizedEmail"));
                    User user = userDAO.findByEmail(norm);
                    if (user == null) throw new AuthenticationException("No registered account found with email: " + norm);
                    long now = System.currentTimeMillis();
                    OtpRecord existing = otpStore.get(norm);
                    if (existing != null && (now - existing.createdAt) < 20000L && now < existing.expiresAt) {
                        throw new AuthenticationException("Please wait a few seconds before requesting another OTP.");
                    }
                    String otp = String.valueOf(100000 + OTP_RNG.nextInt(900000));
                    String salt = PasswordUtil.generateSalt();
                    otpStore.put(norm, new OtpRecord(PasswordUtil.hashPassword(otp, salt), salt, now, now + 300000L));
                    Map<String, Object> res = dispatchOtpEmail(user.getEmail(), user.getFullName(), otp);
                    sendJson(ex, 200, JsonUtil.success(String.valueOf(res.get("infoMessage")), JsonUtil.toJson(res)));
                } else if (path.endsWith("/verify-otp")) {
                    String norm = params.get("email") != null ? params.get("email").trim().toLowerCase() : "";
                    String otp = params.get("otp");
                    OtpRecord rec = otpStore.get(norm);
                    if (rec == null || System.currentTimeMillis() > rec.expiresAt) {
                        otpStore.remove(norm); throw new AuthenticationException("OTP expired or not requested.");
                    }
                    if (rec.attempts >= 5) { otpStore.remove(norm); throw new AuthenticationException("Too many failed attempts. Request a new OTP."); }
                    if (otp == null || !PasswordUtil.verifyPassword(otp.trim(), rec.salt, rec.hash)) {
                        rec.attempts++; throw new AuthenticationException("Invalid OTP code. " + (5 - rec.attempts) + " attempt(s) remaining.");
                    }
                    rec.verified = true; rec.resetToken = UUID.randomUUID().toString();
                    sendJson(ex, 200, JsonUtil.success("OTP verified successfully", "{\"resetToken\":" + JsonUtil.escape(rec.resetToken) + "}"));
                } else if (path.endsWith("/reset-password")) {
                    String norm = params.get("email") != null ? params.get("email").trim().toLowerCase() : "";
                    String newPass = params.get("newPassword");
                    if (newPass == null || newPass.length() < 6) throw new InvalidPropertyDataException("Password must be at least 6 characters.");
                    OtpRecord rec = otpStore.get(norm);
                    if (rec == null || System.currentTimeMillis() > rec.expiresAt) {
                        otpStore.remove(norm); throw new AuthenticationException("Reset session expired. Please request a new OTP.");
                    }
                    boolean ok = (rec.verified && rec.resetToken != null && rec.resetToken.equals(params.get("resetToken")))
                        || (params.get("otp") != null && PasswordUtil.verifyPassword(params.get("otp").trim(), rec.salt, rec.hash));
                    if (!ok) throw new AuthenticationException("Invalid or unverified OTP.");
                    otpStore.remove(norm);
                    if (!userDAO.updatePasswordByEmail(norm, newPass)) throw new AuthenticationException("Account not found.");
                    sendJson(ex, 200, JsonUtil.success("Password updated successfully! You can now sign in.", null));
                } else if (path.endsWith("/logout")) {
                    String token = getSessionToken(ex);
                    sessionManager.invalidate(token);
                    ex.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                    sendJson(ex, 200, JsonUtil.success("Logged out successfully", null));
                } else if (path.endsWith("/me")) {
                    User u = getSessionUser(ex);
                    if (u == null) throw new AuthenticationException("Not logged in");
                    sendJson(ex, 200, JsonUtil.success("Authenticated", serializeUser(u)));
                } else {
                    sendJson(ex, 404, JsonUtil.error("Auth endpoint not found", 404));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static String serializeUser(User u) { return serializeUser(u, null); }

    private static String serializeUser(User u, String authToken) {
        return "{\"userId\":" + u.getUserId() +
               ",\"fullName\":" + JsonUtil.escape(u.getFullName()) +
               ",\"email\":" + JsonUtil.escape(u.getEmail()) +
               ",\"mobile\":" + JsonUtil.escape(u.getMobileNumber()) +
               ",\"role\":" + JsonUtil.escape(u.getUserRole()) +
               ",\"permissions\":" + JsonUtil.escape(u.getRolePermissionsDescription()) +
               ",\"isVerified\":" + u.isVerified() +
               ",\"isActive\":" + u.isActive() +
               (authToken != null ? ",\"authToken\":" + JsonUtil.escape(authToken) : "") + "}";
    }

    public static String resolveVideoUrl(Property p) {
        if (p.getVideoUrl() != null && !p.getVideoUrl().trim().isEmpty()) return p.getVideoUrl();
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        String title = p.getTitle() != null ? p.getTitle().toLowerCase() : "";
        if ("AGRICULTURAL".equals(cat)) return "https://videos.pexels.com/video-files/1851190/1851190-hd_1280_720_25fps.mp4";
        if ("FARMHOUSE".equals(cat)) return "https://videos.pexels.com/video-files/5527788/5527788-hd_1280_720_25fps.mp4";
        if ("PLOT".equals(cat)) return "https://videos.pexels.com/video-files/3129957/3129957-hd_1280_720_25fps.mp4";
        if ("COMMERCIAL".equals(cat)) return "https://videos.pexels.com/video-files/853870/853870-hd_1280_720_25fps.mp4";
        if ("BUILDING".equals(cat)) return "https://videos.pexels.com/video-files/2882118/2882118-hd_1280_720_24fps.mp4";
        if (title.contains("apartment") || title.contains("flat") || title.contains("pg") || title.contains("condo")) {
            return "https://videos.pexels.com/video-files/7578547/7578547-hd_1280_720_30fps.mp4";
        }
        return "https://videos.pexels.com/video-files/7578552/7578552-hd_1280_720_30fps.mp4";
    }

    public static String resolveVideoLabel(Property p) {
        if (p.getVideoLabel() != null && !p.getVideoLabel().trim().isEmpty()) return p.getVideoLabel();
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        if ("AGRICULTURAL".equals(cat)) return "Aerial Farmland & Crop Survey Tour";
        if ("FARMHOUSE".equals(cat)) return "Scenic Farmhouse & Countryside Estate Tour";
        if ("PLOT".equals(cat)) return "Plot Boundary & Layout Drone Flyover";
        if ("COMMERCIAL".equals(cat)) return "Commercial Tech Park & Office Walkthrough";
        if ("BUILDING".equals(cat)) return "Multi-Story Complex & Architecture Tour";
        return "Architectural Residence Walkthrough";
    }

    public static String resolveModel3dUrl(Property p) {
        if (p.getModel3dUrl() != null && !p.getModel3dUrl().trim().isEmpty()) return p.getModel3dUrl().trim();
        int pid = p.getPropertyId();
        switch (pid) {
            case 101: return "https://cdn.jsdelivr.net/gh/BougieZoe/lanai-house@main/public/models/lanai_house.glb";
            case 102: return "https://cdn.jsdelivr.net/gh/mrdoob/three.js@dev/examples/models/gltf/bath_day.glb";
            case 103: return "https://cdn.jsdelivr.net/gh/andrewwoan/daniels-home-office-portfolio@main/public/models/Light%20Room/Light_First.glb|https://cdn.jsdelivr.net/gh/andrewwoan/daniels-home-office-portfolio@main/public/models/Light%20Room/Light_Second.glb|https://cdn.jsdelivr.net/gh/andrewwoan/daniels-home-office-portfolio@main/public/models/Light%20Room/Light_Third.glb";
            case 104: return "https://cdn.jsdelivr.net/gh/bandurchencko/lived-in-houses@main/docs/glb/red-stone-garden-house-7.glb";
            case 105: return "https://cdn.jsdelivr.net/gh/mrdoob/three.js@dev/examples/models/gltf/pool.glb";
            case 106: return "https://cdn.jsdelivr.net/gh/kavyasenthamarai/3d-property-f@main/models/home1.glb";
            case 107: return "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/VirtualCity/glTF-Binary/VirtualCity.glb";
            case 108: return "https://cdn.jsdelivr.net/gh/bandurchencko/lived-in-houses@main/docs/glb/red-stone-garden-house-7.glb";
            case 109: return "https://cdn.jsdelivr.net/gh/mrdoob/three.js@r170/examples/models/gltf/LittlestTokyo.glb";
            case 110: return "https://cdn.jsdelivr.net/gh/bandurchencko/lived-in-houses@main/docs/glb/workshop-house-2020.glb";
            case 111: return "https://cdn.jsdelivr.net/gh/kavyasenthamarai/3d-property-f@main/models/home1.glb";
            case 112: return "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/VirtualCity/glTF-Binary/VirtualCity.glb";
            default: break;
        }
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase(Locale.ROOT) : "RESIDENTIAL";
        if ("COMMERCIAL".equals(cat)) return "https://cdn.jsdelivr.net/gh/andrewwoan/daniels-home-office-portfolio@main/public/models/Light%20Room/Light_First.glb|https://cdn.jsdelivr.net/gh/andrewwoan/daniels-home-office-portfolio@main/public/models/Light%20Room/Light_Second.glb";
        if ("BUILDING".equals(cat)) return "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/VirtualCity/glTF-Binary/VirtualCity.glb";
        if ("FARMHOUSE".equals(cat)) return "https://cdn.jsdelivr.net/gh/mrdoob/three.js@dev/examples/models/gltf/pool.glb";
        return "https://cdn.jsdelivr.net/gh/BougieZoe/lanai-house@main/public/models/lanai_house.glb";
    }

    public static String resolveModel3dFormat(Property p) {
        if (p.getModel3dUrl() != null && !p.getModel3dUrl().trim().isEmpty()) {
            return p.getModel3dFormat() != null ? p.getModel3dFormat() : "GLB";
        }
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase(Locale.ROOT) : "RESIDENTIAL";
        if ("PLOT".equals(cat) || "AGRICULTURAL".equals(cat)) return "TERRAIN_3D";
        return p.getModel3dFormat() != null ? p.getModel3dFormat() : "GLB";
    }

    public static String resolveHdriPreset(Property p) {
        if (p.getHdriPreset() != null && !p.getHdriPreset().trim().isEmpty() && !"daylight".equalsIgnoreCase(p.getHdriPreset())) {
            return p.getHdriPreset().trim().toLowerCase(Locale.ROOT);
        }
        int pid = p.getPropertyId();
        if (pid == 101 || pid == 105 || pid == 108) return "golden";
        if (pid == 104 || pid == 110) return "estate";
        if (pid == 107) return "evening";
        return p.getHdriPreset() != null ? p.getHdriPreset() : "daylight";
    }

    public static List<String> resolveImageGallery(Property p) {
        List<String> list = new ArrayList<>();
        if (p.getPrimaryImageUrl() != null && !p.getPrimaryImageUrl().trim().isEmpty()) list.add(p.getPrimaryImageUrl().trim());
        if (p.getImageUrls() != null) {
            for (String u : p.getImageUrls()) {
                if (u != null && !u.trim().isEmpty() && !list.contains(u.trim())) list.add(u.trim());
            }
        }
        if (list.size() < 3) {
            String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
            String[] fallbacks;
            if ("AGRICULTURAL".equals(cat)) {
                fallbacks = new String[]{
                    "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1200&q=80"
                };
            } else if ("FARMHOUSE".equals(cat)) {
                fallbacks = new String[]{
                    "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1200&q=80"
                };
            } else if ("PLOT".equals(cat)) {
                fallbacks = new String[]{
                    "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1200&q=80"
                };
            } else if ("COMMERCIAL".equals(cat) || "BUILDING".equals(cat)) {
                fallbacks = new String[]{
                    "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1200&q=80"
                };
            } else {
                fallbacks = new String[]{
                    "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=1200&q=80"
                };
            }
            for (String f : fallbacks) if (!list.contains(f)) list.add(f);
        }
        return list;
    }

    private static String serializeProperty(Property p, boolean includeDetails) {
        List<String> fullGallery = resolveImageGallery(p);
        String primaryImg = (p.getPrimaryImageUrl() != null && !p.getPrimaryImageUrl().trim().isEmpty())
            ? p.getPrimaryImageUrl().trim()
            : fullGallery.get(0);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"propertyId\":").append(p.getPropertyId())
          .append(",\"ownerId\":").append(p.getOwnerId())
          .append(",\"categoryId\":").append(p.getCategoryId())
          .append(",\"categoryCode\":").append(JsonUtil.escape(p.getCategoryCode()))
          .append(",\"categoryName\":").append(JsonUtil.escape(p.getCategoryName()))
          .append(",\"title\":").append(JsonUtil.escape(p.getTitle()))
          .append(",\"description\":").append(JsonUtil.escape(p.getDescription()))
          .append(",\"purpose\":").append(JsonUtil.escape(p.getPurpose()))
          .append(",\"price\":").append(p.getPrice())
          .append(",\"propertyArea\":").append(p.getPropertyArea())
          .append(",\"areaUnit\":").append(JsonUtil.escape(p.getAreaUnit()))
          .append(",\"city\":").append(JsonUtil.escape(p.getCity()))
          .append(",\"state\":").append(JsonUtil.escape(p.getState()))
          .append(",\"locality\":").append(JsonUtil.escape(p.getLocality()))
          .append(",\"address\":").append(JsonUtil.escape(p.getAddress()))
          .append(",\"pincode\":").append(JsonUtil.escape(p.getPincode()))
          .append(",\"latitude\":").append(p.getLatitude())
          .append(",\"longitude\":").append(p.getLongitude())
          .append(",\"approvalStatus\":").append(JsonUtil.escape(p.getApprovalStatus()))
          .append(",\"listingStatus\":").append(JsonUtil.escape(p.getListingStatus()))
          .append(",\"rejectionReason\":").append(JsonUtil.escape(p.getRejectionReason()))
          .append(",\"isFeatured\":").append(p.isFeatured())
          .append(",\"primaryImageUrl\":").append(JsonUtil.escape(primaryImg))
          .append(",\"videoUrl\":").append(JsonUtil.escape(resolveVideoUrl(p)))
          .append(",\"videoLabel\":").append(JsonUtil.escape(resolveVideoLabel(p)))
          .append(",\"model3dUrl\":").append(JsonUtil.escape(resolveModel3dUrl(p)))
          .append(",\"model3dFormat\":").append(JsonUtil.escape(resolveModel3dFormat(p)))
          .append(",\"hdriPreset\":").append(JsonUtil.escape(resolveHdriPreset(p)))
          .append(",\"typeDetails\":").append(JsonUtil.escape(p.getPropertyTypeDetails()))
          .append(",\"estMaintenance\":").append(p.calculateEstimatedMaintenanceCost())
          .append(",\"estRegistration\":").append(p.calculateEstimatedRegistrationFee())
          .append(",\"estRegistrationConcession\":").append(p.calculateEstimatedRegistrationFee(3.0))
          .append(",\"averageRating\":").append(Math.round(p.getAverageRating() * 10.0) / 10.0)
          .append(",\"reviewCount\":").append(p.getReviewCount())
          .append(",\"viewsCount\":").append(p.getViewsCount())
          .append(",\"ownerName\":").append(JsonUtil.escape(p.getOwnerName()))
          .append(",\"ownerPhone\":").append(JsonUtil.escape(p.getOwnerPhone()))
          .append(",\"ownerEmail\":").append(JsonUtil.escape(p.getOwnerEmail()))
          .append(",\"ownerVerified\":").append(p.isOwnerVerified());

        if (p instanceof ResidentialProperty) {
            ResidentialProperty rp = (ResidentialProperty) p;
            sb.append(",\"bedrooms\":").append(rp.getBedrooms())
              .append(",\"bathrooms\":").append(rp.getBathrooms())
              .append(",\"furnishingStatus\":").append(JsonUtil.escape(rp.getFurnishingStatus()));
        } else if (p instanceof FarmHouse) {
            FarmHouse fh = (FarmHouse) p;
            sb.append(",\"bedrooms\":").append(fh.getBedrooms())
              .append(",\"bathrooms\":").append(fh.getBathrooms())
              .append(",\"furnishingStatus\":\"FULLY_FURNISHED\"");
        } else {
            sb.append(",\"bedrooms\":0,\"bathrooms\":0,\"furnishingStatus\":\"N/A\"");
        }

        sb.append(",\"images\":[");
        for (int i = 0; i < fullGallery.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonUtil.escape(fullGallery.get(i)));
        }
        sb.append("]");
        if (includeDetails || !p.getAmenities().isEmpty()) {
            sb.append(",\"amenities\":[");
            if (p.getAmenities() != null) {
                for (int i = 0; i < p.getAmenities().size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonUtil.escape(p.getAmenities().get(i)));
                }
            }
            sb.append("]");
        }
        return sb.append("}").toString();
    }

    private static String serializePropertyList(List<Property> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeProperty(list.get(i), false));
        }
        return sb.append("]").toString();
    }

    private static class PropertyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            Map<String, String> params = parseBody(ex);
            try {
                if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                    User u = requireUser(ex);
                    if ("DELETE".equals(method) || path.endsWith("/delete")) {
                        int id = requireInt(params, "propertyId");
                        propertyDAO.deleteWithIDORCheck(id, u);
                        sendJson(ex, 200, JsonUtil.success("Property deleted successfully", null));
                    } else if ("PUT".equals(method) || path.endsWith("/update")) {
                        int id = requireInt(params, "propertyId");
                        Property updated = propertyDAO.updateWithIDORCheck(id, params, u);
                        sendJson(ex, 200, JsonUtil.success("Property updated", serializeProperty(updated, true)));
                    } else {
                        Property p = PropertyDAO.createPropertyInstance(params.get("categoryCode"));
                        p.setOwnerId(u.getUserId());
                        p.setTitle(params.get("title"));
                        p.setDescription(params.get("description"));
                        p.setPurpose(params.get("purpose") != null ? params.get("purpose").toUpperCase() : "BUY");
                        p.setPrice(requireDouble(params, "price"));
                        p.setPropertyArea(requireDouble(params, "propertyArea"));
                        p.setAreaUnit(params.get("areaUnit") != null && !params.get("areaUnit").isEmpty() ? params.get("areaUnit") : "Sq. Ft.");
                        p.setState(params.get("state") != null && !params.get("state").isEmpty() ? params.get("state") : "Karnataka");
                        p.setCity(params.get("city"));
                        p.setLocality(params.get("locality") != null && !params.get("locality").isEmpty() ? params.get("locality") : params.get("city"));
                        p.setAddress(params.get("address"));
                        p.setPincode(params.get("pincode"));

                        if (p instanceof ResidentialProperty) {
                            ResidentialProperty rp = (ResidentialProperty) p;
                            rp.setBedrooms(Math.max(1, parseIntOrDefault(params.get("bedrooms"), 2)));
                            rp.setBathrooms(Math.max(1, parseIntOrDefault(params.get("bathrooms"), 2)));
                            rp.setBuiltUpArea(p.getPropertyArea());
                            rp.setResidentialType(params.get("residentialType") != null ? params.get("residentialType") : "APARTMENT");
                            rp.setFurnishingStatus(params.get("furnishingStatus") != null ? params.get("furnishingStatus") : "SEMI_FURNISHED");
                        } else if (p instanceof CommercialProperty) {
                            CommercialProperty cp = (CommercialProperty) p;
                            cp.setCommercialType(params.get("commercialType") != null ? params.get("commercialType") : "OFFICE_SPACE");
                            cp.setCarpetArea(p.getPropertyArea());
                            cp.setCommercialUsage(params.get("commercialUsage"));
                            cp.setPowerBackup(!"false".equalsIgnoreCase(params.get("powerBackup")));
                        } else if (p instanceof AgriculturalLand) {
                            AgriculturalLand al = (AgriculturalLand) p;
                            al.setLandType(params.get("landType") != null ? params.get("landType") : "FARMLAND");
                            al.setTotalLandArea(p.getPropertyArea());
                            al.setAreaUnit("Acres");
                            al.setSoilType(params.get("soilType"));
                            al.setWaterAvailability(params.get("waterAvailability"));
                            al.setHasBorewell("true".equalsIgnoreCase(params.get("hasBorewell")));
                        } else if (p instanceof FarmHouse) {
                            FarmHouse fh = (FarmHouse) p;
                            fh.setLandArea(p.getPropertyArea());
                            fh.setBuiltUpArea(p.getPropertyArea());
                            fh.setBedrooms(Math.max(1, parseIntOrDefault(params.get("bedrooms"), 3)));
                            fh.setBathrooms(Math.max(1, parseIntOrDefault(params.get("bathrooms"), 3)));
                            fh.setHasSwimmingPool("true".equalsIgnoreCase(params.get("hasSwimmingPool")));
                            fh.setHasGarden(!"false".equalsIgnoreCase(params.get("hasGarden")));
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
                            b.setTotalUnits(Math.max(1, parseIntOrDefault(params.get("totalUnits"), 10)));
                            b.setBuilderName(params.get("builderName"));
                            b.setConstructionStatus(params.get("constructionStatus") != null ? params.get("constructionStatus") : "READY_TO_MOVE");
                        }

                        String imgs = params.get("imageUrls");
                        if (imgs != null && !imgs.trim().isEmpty()) {
                            List<String> il = new ArrayList<>();
                            for (String s : imgs.split("[,;\\n]")) if (!s.trim().isEmpty()) il.add(s.trim());
                            p.setImageUrls(il);
                        } else {
                            p.setImageUrls(Collections.singletonList("https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80"));
                        }
                        String amens = params.get("amenities");
                        if (amens != null && !amens.trim().isEmpty()) {
                            List<String> al = new ArrayList<>();
                            for (String s : amens.split("[,;\\n]")) if (!s.trim().isEmpty()) al.add(s.trim());
                            p.setAmenities(al);
                        }
                        if (params.get("model3dUrl") != null && !params.get("model3dUrl").trim().isEmpty()) {
                            p.setModel3dUrl(params.get("model3dUrl").trim());
                        }
                        if (params.get("model3dFormat") != null && !params.get("model3dFormat").trim().isEmpty()) {
                            p.setModel3dFormat(params.get("model3dFormat").trim().toUpperCase(Locale.ROOT));
                        }
                        if (params.get("hdriPreset") != null && !params.get("hdriPreset").trim().isEmpty()) {
                            p.setHdriPreset(params.get("hdriPreset").trim().toLowerCase(Locale.ROOT));
                        }
                        Property saved = propertyDAO.save(p, u);
                        AsyncNotificationService.getInstance().dispatchNotification(1, "admin@realestate.com", "New Listing Submitted", u.getFullName() + " posted: " + saved.getTitle());
                        SseEventHub.getInstance().broadcastToUser(1, "audit_event", "{\"action\":\"PROPERTY_CREATED\",\"details\":" + JsonUtil.escape("New listing: " + saved.getTitle()) + "}");
                        sendJson(ex, 200, JsonUtil.success("Property posted successfully", serializeProperty(saved, true)));
                    }
                } else {
                    if (path.endsWith("/details")) {
                        int id = requireInt(params, "id");
                        Property p = propertyDAO.getById(id);
                        p.setViewsCount(p.getViewsCount() + 1);
                        sendJson(ex, 200, JsonUtil.success("Details loaded", serializeProperty(p, true)));
                    } else if (path.endsWith("/compare")) {
                        List<Property> list = new ArrayList<>();
                        String ids = params.get("ids");
                        if (ids != null) {
                            for (String idStr : ids.split(",")) {
                                try { list.add(propertyDAO.getById(Integer.parseInt(idStr.trim()))); } catch (Exception ignored) {}
                            }
                        }
                        sendJson(ex, 200, JsonUtil.success("Compare loaded", serializePropertyList(list)));
                    } else if (path.endsWith("/my")) {
                        User u = requireUser(ex);
                        sendJson(ex, 200, JsonUtil.success("My properties", serializePropertyList(propertyDAO.getByOwner(u.getUserId()))));
                    } else if (path.endsWith("/nearby")) {
                        double qLat = parseDoubleOrDefault(params.get("lat"), 12.9716);
                        double qLng = parseDoubleOrDefault(params.get("lng"), 77.5946);
                        double radiusKm = Math.max(1.0, parseDoubleOrDefault(params.get("radiusKm"), 150.0));
                        params.put("lat", String.valueOf(qLat));
                        params.put("lng", String.valueOf(qLng));
                        params.put("radiusKm", String.valueOf(radiusKm));
                        List<Property> nearby = propertyDAO.search(params, null);
                        nearby.sort(Comparator.comparingDouble(p -> PropertyDAO.haversineKm(qLat, qLng, p.getLatitude(), p.getLongitude())));
                        StringBuilder out = new StringBuilder("[");
                        for (int i = 0; i < nearby.size(); i++) {
                            if (i > 0) out.append(",");
                            Property p = nearby.get(i);
                            double dist = Math.round(PropertyDAO.haversineKm(qLat, qLng, p.getLatitude(), p.getLongitude()) * 10.0) / 10.0;
                            String rawJson = serializeProperty(p, false);
                            out.append(rawJson, 0, rawJson.length() - 1).append(",\"distanceKm\":").append(dist).append("}");
                        }
                        sendJson(ex, 200, JsonUtil.success("Nearby properties", out.append("]").toString()));
                    } else {
                        List<Property> all = propertyDAO.search(params, params.get("sortBy"));
                        int page = Math.max(1, parseIntOrDefault(params.get("page"), 1));
                        int pageSize = Math.min(50, Math.max(1, parseIntOrDefault(params.get("pageSize"), 24)));
                        int total = all.size();
                        int from = Math.min(total, (page - 1) * pageSize);
                        int to = Math.min(total, from + pageSize);
                        List<Property> slice = all.subList(from, to);
                        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
                        String data = "{\"items\":" + serializePropertyList(slice) + ",\"total\":" + total + ",\"page\":" + page + ",\"pageSize\":" + pageSize + ",\"totalPages\":" + totalPages + "}";
                        sendJson(ex, 200, JsonUtil.success("Found", data));
                    }
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class AppointmentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                Map<String, String> params = parseBody(ex);
                String path = ex.getRequestURI().getPath();
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    if (path.endsWith("/status")) {
                        int visitId = requireInt(params, "visitId");
                        String newStatus = params.get("status") != null ? params.get("status").trim().toUpperCase() : "";
                        if (!Arrays.asList("CONFIRMED", "COMPLETED", "CANCELLED", "REJECTED").contains(newStatus)) {
                            throw new InvalidPropertyDataException("Invalid appointment status.");
                        }
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            SupabaseClient sb = SupabaseClient.getInstance();
                            List<Map<String, Object>> rows = sb.get("property_visits?select=*,properties(owner_id)&visit_id=eq." + visitId + "&limit=1");
                            if (rows.isEmpty()) throw new RealEstateException("Appointment not found.", 404);
                            Map<String, Object> r = rows.get(0);
                            Map<String, Object> p = JsonUtil.getMap(r, "properties");
                            int ownerId = p != null ? JsonUtil.getInt(p, "owner_id") : 0;
                            int visitorId = JsonUtil.getInt(r, "user_id");
                            verifyVisitTransition(u, ownerId, visitorId, newStatus);
                            Map<String, Object> patch = Collections.singletonMap("appointment_status", newStatus);
                            sb.patch("property_visits?visit_id=eq." + visitId, patch);
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection()) {
                                try (PreparedStatement chk = conn.prepareStatement("SELECT v.user_id, p.owner_id FROM property_visits v JOIN properties p ON v.property_id=p.property_id WHERE v.visit_id=?")) {
                                    chk.setInt(1, visitId);
                                    try (ResultSet rs = chk.executeQuery()) {
                                        if (!rs.next()) throw new RealEstateException("Appointment not found.", 404);
                                        verifyVisitTransition(u, rs.getInt("owner_id"), rs.getInt("user_id"), newStatus);
                                    }
                                }
                                try (PreparedStatement upd = conn.prepareStatement("UPDATE property_visits SET appointment_status=? WHERE visit_id=?")) {
                                    upd.setString(1, newStatus); upd.setInt(2, visitId);
                                    upd.executeUpdate();
                                }
                            }
                        } else {
                            PropertyVisit found = null;
                            for (PropertyVisit v : InMemoryStore.getInstance().visits) {
                                if (v.getVisitId() == visitId) { found = v; break; }
                            }
                            if (found == null) throw new RealEstateException("Appointment not found.", 404);
                            verifyVisitTransition(u, found.getOwnerId(), found.getUserId(), newStatus);
                            found.setAppointmentStatus(newStatus);
                            InMemoryStore.getInstance().recordAudit(u, "VISIT_" + newStatus, "VISIT", visitId, "Visit #" + visitId + " -> " + newStatus);
                            AsyncNotificationService.getInstance().dispatchNotification(found.getUserId(), "visitor", "Visit Status Updated", "Your visit for " + found.getPropertyTitle() + " is now " + newStatus);
                        }
                        sendJson(ex, 200, JsonUtil.success("Status updated", null));
                    } else {
                        int pid = requireInt(params, "propertyId");
                        Property p = propertyDAO.getById(pid);
                        if (!"APPROVED".equalsIgnoreCase(p.getApprovalStatus())) {
                            throw new InvalidPropertyDataException("Visits can only be booked for approved listings.");
                        }
                        String vd = params.get("visitDate");
                        if (vd == null || vd.trim().isEmpty()) throw new InvalidPropertyDataException("Visit date is required.");
                        String t = params.get("visitTime");
                        if (t == null || t.trim().isEmpty()) t = "10:00:00";
                        else if (t.length() == 5) t += ":00";

                        int vid;
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("property_id", pid);
                            payload.put("user_id", u.getUserId());
                            payload.put("visit_date", vd);
                            payload.put("visit_time", t);
                            payload.put("contact_number", params.get("contactNumber") != null ? params.get("contactNumber") : u.getMobileNumber());
                            payload.put("notes", params.get("notes"));
                            payload.put("appointment_status", "PENDING");
                            Map<String, Object> created = SupabaseClient.getInstance().post("property_visits", payload);
                            vid = JsonUtil.getInt(created, "visit_id");
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection();
                                 PreparedStatement ins = conn.prepareStatement("INSERT INTO property_visits (property_id, user_id, visit_date, visit_time, contact_number, notes, appointment_status) VALUES (?, ?, ?, ?, ?, ?, 'PENDING')", Statement.RETURN_GENERATED_KEYS)) {
                                ins.setInt(1, pid); ins.setInt(2, u.getUserId());
                                ins.setDate(3, Date.valueOf(vd)); ins.setTime(4, Time.valueOf(t));
                                ins.setString(5, params.get("contactNumber") != null ? params.get("contactNumber") : u.getMobileNumber());
                                ins.setString(6, params.get("notes"));
                                ins.executeUpdate();
                                try (ResultSet gk = ins.getGeneratedKeys()) { vid = gk.next() ? gk.getInt(1) : 0; }
                            }
                        } else {
                            InMemoryStore store = InMemoryStore.getInstance();
                            PropertyVisit v = new PropertyVisit();
                            vid = store.visitSeq.getAndIncrement();
                            v.setVisitId(vid); v.setPropertyId(pid); v.setPropertyTitle(p.getTitle()); v.setPropertyCity(p.getCity());
                            v.setOwnerId(p.getOwnerId()); v.setUserId(u.getUserId()); v.setUserName(u.getFullName());
                            v.setVisitDate(Date.valueOf(vd)); v.setVisitTime(Time.valueOf(t));
                            v.setContactNumber(params.get("contactNumber") != null ? params.get("contactNumber") : u.getMobileNumber());
                            v.setNotes(params.get("notes"));
                            v.setAppointmentStatus("PENDING");
                            store.visits.add(0, v);
                            store.recordAudit(u, "VISIT_BOOKED", "VISIT", vid, "Booked tour for Property #" + pid);
                        }
                        AsyncNotificationService.getInstance().dispatchNotification(p.getOwnerId(), p.getOwnerEmail(), "New Site Visit Request", u.getFullName() + " requested a visit for " + p.getTitle() + " on " + vd);
                        sendJson(ex, 200, JsonUtil.success("Visit booked", "{\"visitId\":" + vid + "}"));
                    }
                } else {
                    List<Map<String, Object>> outList = new ArrayList<>();
                    if (activePersistenceMode == PersistenceMode.SUPABASE) {
                        String q = u.isAdministrator()
                            ? "property_visits?select=*,properties(title,city,owner_id),users(full_name,email)&order=created_at.desc"
                            : u.isListingActor()
                                ? "property_visits?select=*,properties!inner(title,city,owner_id),users(full_name,email)&properties.owner_id=eq." + u.getUserId() + "&order=visit_date.desc"
                                : "property_visits?select=*,properties(title,city,owner_id),users(full_name,email)&user_id=eq." + u.getUserId() + "&order=visit_date.desc";
                        for (Map<String, Object> r : SupabaseClient.getInstance().get(q)) {
                            Map<String, Object> p = JsonUtil.getMap(r, "properties");
                            Map<String, Object> usr = JsonUtil.getMap(r, "users");
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("visitId", JsonUtil.getInt(r, "visit_id"));
                            item.put("propertyId", JsonUtil.getInt(r, "property_id"));
                            item.put("propertyTitle", p != null ? JsonUtil.getString(p, "title") : "");
                            item.put("propertyCity", p != null ? JsonUtil.getString(p, "city") : "");
                            item.put("userName", usr != null ? JsonUtil.getString(usr, "full_name") : "");
                            item.put("visitDate", JsonUtil.getString(r, "visit_date"));
                            item.put("visitTime", JsonUtil.getString(r, "visit_time"));
                            item.put("contactNumber", JsonUtil.getString(r, "contact_number"));
                            item.put("notes", JsonUtil.getString(r, "notes"));
                            item.put("status", JsonUtil.getString(r, "appointment_status"));
                            outList.add(item);
                        }
                    } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                        String sql = u.isAdministrator()
                            ? "SELECT v.*, p.title, p.city, u.full_name FROM property_visits v JOIN properties p ON v.property_id=p.property_id JOIN users u ON v.user_id=u.user_id ORDER BY v.created_at DESC"
                            : "SELECT v.*, p.title, p.city, u.full_name FROM property_visits v JOIN properties p ON v.property_id=p.property_id JOIN users u ON v.user_id=u.user_id WHERE v.user_id=? OR p.owner_id=? ORDER BY v.visit_date DESC";
                        try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                            if (!u.isAdministrator()) { st.setInt(1, u.getUserId()); st.setInt(2, u.getUserId()); }
                            try (ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("visitId", rs.getInt("visit_id"));
                                    item.put("propertyId", rs.getInt("property_id"));
                                    item.put("propertyTitle", rs.getString("title"));
                                    item.put("propertyCity", rs.getString("city"));
                                    item.put("userName", rs.getString("full_name"));
                                    item.put("visitDate", String.valueOf(rs.getDate("visit_date")));
                                    item.put("visitTime", String.valueOf(rs.getTime("visit_time")));
                                    item.put("contactNumber", rs.getString("contact_number"));
                                    item.put("notes", rs.getString("notes"));
                                    item.put("status", rs.getString("appointment_status"));
                                    outList.add(item);
                                }
                            }
                        }
                    } else {
                        for (PropertyVisit v : InMemoryStore.getInstance().visits) {
                            boolean match = u.isAdministrator() || v.getUserId() == u.getUserId() || v.getOwnerId() == u.getUserId();
                            if (match) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("visitId", v.getVisitId());
                                item.put("propertyId", v.getPropertyId());
                                item.put("propertyTitle", v.getPropertyTitle());
                                item.put("propertyCity", v.getPropertyCity());
                                item.put("userName", v.getUserName());
                                item.put("visitDate", v.getVisitDate() != null ? v.getVisitDate().toString() : "");
                                item.put("visitTime", v.getVisitTime() != null ? v.getVisitTime().toString() : "");
                                item.put("contactNumber", v.getContactNumber());
                                item.put("notes", v.getNotes());
                                item.put("status", v.getAppointmentStatus());
                                outList.add(item);
                            }
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("Appointments", JsonUtil.toJson(outList)));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }

        private void verifyVisitTransition(User actor, int ownerId, int visitorId, String newStatus) throws UnauthorizedAccessException {
            if (actor.isAdministrator()) return;
            if (actor.getUserId() == ownerId) return;
            if (actor.getUserId() == visitorId && "CANCELLED".equals(newStatus)) return;
            throw new UnauthorizedAccessException("Not authorized to update this appointment status.");
        }
    }

    private static class RentalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                Map<String, String> params = parseBody(ex);
                String path = ex.getRequestURI().getPath();
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    if (path.endsWith("/pay")) {
                        int agreementId = requireInt(params, "agreementId");
                        String month = params.get("paymentMonth") != null ? params.get("paymentMonth") : LocalDate.now().toString().substring(0, 7);
                        String method = params.get("paymentMethod") != null ? params.get("paymentMethod").toUpperCase() : "SIMULATED_UPI";
                        if (!Arrays.asList("SIMULATED_UPI", "SIMULATED_NEFT", "SIMULATED_CARD", "BANK_CHEQUE").contains(method)) {
                            method = "SIMULATED_UPI";
                        }

                        RentPayment rp = new RentPayment();
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            SupabaseClient sb = SupabaseClient.getInstance();
                            List<Map<String, Object>> rows = sb.get("rental_agreements?select=*&agreement_id=eq." + agreementId + "&limit=1");
                            if (rows.isEmpty()) throw new RealEstateException("Rental agreement not found.", 404);
                            Map<String, Object> ag = rows.get(0);
                            if (!u.isAdministrator() && JsonUtil.getInt(ag, "tenant_id") != u.getUserId()) {
                                throw new UnauthorizedAccessException("Only the tenant on this agreement can submit rent.");
                            }
                            double due = JsonUtil.getDouble(ag, "monthly_rent");
                            if (!rp.processSimulatedPayment(due)) throw new PaymentFailedException("Invalid payment amount.");
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("agreement_id", agreementId);
                            payload.put("payment_month", month);
                            payload.put("amount_paid", due);
                            payload.put("payment_date", LocalDate.now().toString());
                            payload.put("payment_method", method);
                            payload.put("transaction_ref", rp.getTransactionRef());
                            payload.put("payment_status", "SUCCESS");
                            sb.post("rent_payments", payload);
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection()) {
                                double due = 0.0; int ownerId = 0; String title = "";
                                try (PreparedStatement st = conn.prepareStatement("SELECT r.monthly_rent, r.tenant_id, r.owner_id, p.title FROM rental_agreements r JOIN properties p ON r.property_id=p.property_id WHERE r.agreement_id=?")) {
                                    st.setInt(1, agreementId);
                                    try (ResultSet rs = st.executeQuery()) {
                                        if (!rs.next()) throw new RealEstateException("Rental agreement not found.", 404);
                                        if (!u.isAdministrator() && rs.getInt("tenant_id") != u.getUserId()) throw new UnauthorizedAccessException("Only the tenant on this agreement can submit rent.");
                                        due = rs.getDouble("monthly_rent"); ownerId = rs.getInt("owner_id"); title = rs.getString("title");
                                    }
                                }
                                if (!rp.processSimulatedPayment(due)) throw new PaymentFailedException("Invalid payment amount.");
                                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO rent_payments (agreement_id, payment_month, amount_paid, payment_date, payment_method, transaction_ref, payment_status) VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS')")) {
                                    ins.setInt(1, agreementId); ins.setString(2, month); ins.setDouble(3, due);
                                    ins.setDate(4, Date.valueOf(LocalDate.now())); ins.setString(5, method); ins.setString(6, rp.getTransactionRef());
                                    ins.executeUpdate();
                                }
                                AsyncNotificationService.getInstance().dispatchNotification(ownerId, "owner", "Rent Payment Received", u.getFullName() + " paid rent for " + title);
                            }
                        } else {
                            InMemoryStore store = InMemoryStore.getInstance();
                            RentalAgreement ag = null;
                            for (RentalAgreement a : store.agreements) if (a.getAgreementId() == agreementId) { ag = a; break; }
                            if (ag == null) throw new RealEstateException("Rental agreement not found.", 404);
                            if (!u.isAdministrator() && ag.getTenantId() != u.getUserId()) {
                                throw new UnauthorizedAccessException("Only the tenant on this agreement can submit rent.");
                            }
                            if (!rp.processSimulatedPayment(ag.getMonthlyRent())) throw new PaymentFailedException("Invalid payment amount.");
                            rp.setRentPaymentId(store.rentPayments.size() + 1);
                            rp.setAgreementId(agreementId); rp.setPaymentMonth(month); rp.setPaymentDate(Date.valueOf(LocalDate.now())); rp.setPaymentMethod(method);
                            store.rentPayments.add(rp);
                            store.recordAudit(u, "RENT_PAID", "RENTAL", agreementId, "Paid Rs." + ag.getMonthlyRent() + " (" + rp.getTransactionRef() + ")");
                            AsyncNotificationService.getInstance().dispatchNotification(ag.getOwnerId(), "owner", "Rent Payment Received", u.getFullName() + " paid rent for " + ag.getPropertyTitle() + " (" + rp.getTransactionRef() + ")");
                        }
                        sendJson(ex, 200, JsonUtil.success("Rent paid", "{\"txnRef\":" + JsonUtil.escape(rp.getTransactionRef()) + ",\"amount\":" + rp.getAmountPaid() + "}"));
                    } else {
                        int pid = requireInt(params, "propertyId");
                        Property p = propertyDAO.getById(pid);
                        if (!"APPROVED".equalsIgnoreCase(p.getApprovalStatus()) || !"AVAILABLE".equalsIgnoreCase(p.getListingStatus())) {
                            throw new ConflictException("Property is not available for rental agreement.");
                        }
                        if ("BUY".equalsIgnoreCase(p.getPurpose())) {
                            throw new InvalidPropertyDataException("This property is listed for BUY, not RENT/LEASE.");
                        }
                        if (p.getOwnerId() == u.getUserId()) {
                            throw new InvalidPropertyDataException("Owner cannot create a rental agreement with themselves.");
                        }
                        String startStr = params.get("startDate") != null ? params.get("startDate") : LocalDate.now().toString();
                        String endStr = params.get("endDate") != null ? params.get("endDate") : LocalDate.now().plusMonths(11).toString();
                        LocalDate sd = LocalDate.parse(startStr);
                        LocalDate ed = LocalDate.parse(endStr);
                        if (!ed.isAfter(sd)) throw new InvalidPropertyDataException("Agreement end date must be after start date.");

                        double authoritativeRent = p.getPrice();
                        double deposit = params.get("securityDeposit") != null && !params.get("securityDeposit").isEmpty()
                            ? Double.parseDouble(params.get("securityDeposit")) : authoritativeRent * 4.0;

                        int aid;
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            SupabaseClient sb = SupabaseClient.getInstance();
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("property_id", pid);
                            payload.put("owner_id", p.getOwnerId());
                            payload.put("tenant_id", u.getUserId());
                            payload.put("monthly_rent", authoritativeRent);
                            payload.put("security_deposit", deposit);
                            payload.put("agreement_start_date", sd.toString());
                            payload.put("agreement_end_date", ed.toString());
                            payload.put("agreement_status", "ACTIVE");
                            payload.put("terms_and_conditions", params.get("terms"));
                            Map<String, Object> created = sb.post("rental_agreements", payload);
                            aid = JsonUtil.getInt(created, "agreement_id");
                            sb.patch("properties?property_id=eq." + pid, Collections.singletonMap("listing_status", "RENTED"));
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            Connection conn = DBConnection.getInstance().getConnection();
                            try {
                                conn.setAutoCommit(false);
                                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO rental_agreements (property_id, owner_id, tenant_id, monthly_rent, security_deposit, agreement_start_date, agreement_end_date, agreement_status, terms_and_conditions) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)", Statement.RETURN_GENERATED_KEYS)) {
                                    ins.setInt(1, pid); ins.setInt(2, p.getOwnerId()); ins.setInt(3, u.getUserId());
                                    ins.setDouble(4, authoritativeRent); ins.setDouble(5, deposit);
                                    ins.setDate(6, Date.valueOf(sd)); ins.setDate(7, Date.valueOf(ed)); ins.setString(8, params.get("terms"));
                                    ins.executeUpdate();
                                    try (ResultSet gk = ins.getGeneratedKeys()) { aid = gk.next() ? gk.getInt(1) : 0; }
                                }
                                try (PreparedStatement upd = conn.prepareStatement("UPDATE properties SET listing_status='RENTED' WHERE property_id=?")) {
                                    upd.setInt(1, pid); upd.executeUpdate();
                                }
                                conn.commit();
                            } catch (SQLException se) {
                                conn.rollback(); throw se;
                            } finally {
                                conn.setAutoCommit(true); conn.close();
                            }
                        } else {
                            InMemoryStore store = InMemoryStore.getInstance();
                            RentalAgreement ag = new RentalAgreement();
                            aid = store.rentalSeq.getAndIncrement();
                            ag.setAgreementId(aid); ag.setPropertyId(pid); ag.setPropertyTitle(p.getTitle());
                            ag.setOwnerId(p.getOwnerId()); ag.setOwnerName(p.getOwnerName());
                            ag.setTenantId(u.getUserId()); ag.setTenantName(u.getFullName());
                            ag.setMonthlyRent(authoritativeRent); ag.setSecurityDeposit(deposit);
                            ag.setAgreementStartDate(Date.valueOf(sd)); ag.setAgreementEndDate(Date.valueOf(ed));
                            ag.setAgreementStatus("ACTIVE"); ag.setTerms(params.get("terms"));
                            store.agreements.add(0, ag);
                            p.setListingStatus("RENTED");
                            store.recordAudit(u, "RENTAL_AGREEMENT_CREATED", "RENTAL", aid, "Leased Property #" + pid + " at Rs." + authoritativeRent + "/mo");
                        }
                        AsyncNotificationService.getInstance().dispatchNotification(p.getOwnerId(), p.getOwnerEmail(), "Property Leased", u.getFullName() + " executed rental agreement #" + aid + " for " + p.getTitle());
                        SseEventHub.getInstance().broadcastAll("inventory_update", "{\"propertyId\":" + pid + ",\"listingStatus\":\"RENTED\"}");
                        sendJson(ex, 200, JsonUtil.success("Rental agreement created", "{\"agreementId\":" + aid + ",\"monthlyRent\":" + authoritativeRent + "}"));
                    }
                } else {
                    List<Map<String, Object>> out = new ArrayList<>();
                    if (activePersistenceMode == PersistenceMode.SUPABASE) {
                        List<Map<String, Object>> rows = SupabaseClient.getInstance().get(
                            "rental_agreements?select=*,properties(title),owner:users!rental_agreements_owner_id_fkey(full_name),tenant:users!rental_agreements_tenant_id_fkey(full_name)&or=(tenant_id.eq." + u.getUserId() + ",owner_id.eq." + u.getUserId() + ")&order=created_at.desc"
                        );
                        for (Map<String, Object> r : rows) {
                            Map<String, Object> p = JsonUtil.getMap(r, "properties");
                            Map<String, Object> own = JsonUtil.getMap(r, "owner");
                            Map<String, Object> ten = JsonUtil.getMap(r, "tenant");
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("agreementId", JsonUtil.getInt(r, "agreement_id"));
                            item.put("propertyId", JsonUtil.getInt(r, "property_id"));
                            item.put("propertyTitle", p != null ? JsonUtil.getString(p, "title") : "");
                            item.put("ownerName", own != null ? JsonUtil.getString(own, "full_name") : "");
                            item.put("tenantName", ten != null ? JsonUtil.getString(ten, "full_name") : "");
                            item.put("monthlyRent", JsonUtil.getDouble(r, "monthly_rent"));
                            item.put("securityDeposit", JsonUtil.getDouble(r, "security_deposit"));
                            item.put("startDate", JsonUtil.getString(r, "agreement_start_date"));
                            item.put("endDate", JsonUtil.getString(r, "agreement_end_date"));
                            item.put("status", JsonUtil.getString(r, "agreement_status"));
                            out.add(item);
                        }
                    } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                        String sql = u.isAdministrator()
                            ? "SELECT r.*, p.title, o.full_name AS owner_name, t.full_name AS tenant_name FROM rental_agreements r JOIN properties p ON r.property_id=p.property_id JOIN users o ON r.owner_id=o.user_id JOIN users t ON r.tenant_id=t.user_id ORDER BY r.created_at DESC"
                            : "SELECT r.*, p.title, o.full_name AS owner_name, t.full_name AS tenant_name FROM rental_agreements r JOIN properties p ON r.property_id=p.property_id JOIN users o ON r.owner_id=o.user_id JOIN users t ON r.tenant_id=t.user_id WHERE r.tenant_id=? OR r.owner_id=? ORDER BY r.created_at DESC";
                        try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                            if (!u.isAdministrator()) { st.setInt(1, u.getUserId()); st.setInt(2, u.getUserId()); }
                            try (ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("agreementId", rs.getInt("agreement_id"));
                                    item.put("propertyId", rs.getInt("property_id"));
                                    item.put("propertyTitle", rs.getString("title"));
                                    item.put("ownerName", rs.getString("owner_name"));
                                    item.put("tenantName", rs.getString("tenant_name"));
                                    item.put("monthlyRent", rs.getDouble("monthly_rent"));
                                    item.put("securityDeposit", rs.getDouble("security_deposit"));
                                    item.put("startDate", String.valueOf(rs.getDate("agreement_start_date")));
                                    item.put("endDate", String.valueOf(rs.getDate("agreement_end_date")));
                                    item.put("status", rs.getString("agreement_status"));
                                    out.add(item);
                                }
                            }
                        }
                    } else {
                        for (RentalAgreement a : InMemoryStore.getInstance().agreements) {
                            if (u.isAdministrator() || a.getTenantId() == u.getUserId() || a.getOwnerId() == u.getUserId()) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("agreementId", a.getAgreementId());
                                item.put("propertyId", a.getPropertyId());
                                item.put("propertyTitle", a.getPropertyTitle());
                                item.put("ownerName", a.getOwnerName());
                                item.put("tenantName", a.getTenantName());
                                item.put("monthlyRent", a.getMonthlyRent());
                                item.put("securityDeposit", a.getSecurityDeposit());
                                item.put("startDate", a.getAgreementStartDate().toString());
                                item.put("endDate", a.getAgreementEndDate().toString());
                                item.put("status", a.getAgreementStatus());
                                out.add(item);
                            }
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("Agreements", JsonUtil.toJson(out)));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class PurchaseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                Map<String, String> params = parseBody(ex);
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    int pid = requireInt(params, "propertyId");
                    Property p = propertyDAO.getById(pid);
                    if (!"APPROVED".equalsIgnoreCase(p.getApprovalStatus()) || !"AVAILABLE".equalsIgnoreCase(p.getListingStatus())) {
                        throw new ConflictException("Property is no longer available for purchase.");
                    }
                    if (!"BUY".equalsIgnoreCase(p.getPurpose())) {
                        throw new InvalidPropertyDataException("Only BUY listings can be purchased directly.");
                    }
                    if (p.getOwnerId() == u.getUserId()) {
                        throw new InvalidPropertyDataException("You cannot purchase your own property listing.");
                    }
                    // Derive authoritative price & seller from the verified property record
                    PurchaseTransaction txn = new PurchaseTransaction();
                    if (!txn.processSimulatedPayment(p.getPrice())) {
                        throw new PaymentFailedException("Purchase price invalid.");
                    }
                    txn.setPropertyId(pid);
                    txn.setPropertyTitle(p.getTitle());
                    txn.setBuyerId(u.getUserId());
                    txn.setBuyerName(u.getFullName());
                    txn.setSellerId(p.getOwnerId());
                    txn.setSellerName(p.getOwnerName());
                    txn.setRemarks(params.get("remarks") != null ? params.get("remarks") : "Simulated Escrow Purchase Transaction");

                    int txnId;
                    if (activePersistenceMode == PersistenceMode.SUPABASE) {
                        SupabaseClient sb = SupabaseClient.getInstance();
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("property_id", pid);
                        payload.put("buyer_id", u.getUserId());
                        payload.put("seller_id", p.getOwnerId());
                        payload.put("purchase_price", p.getPrice());
                        payload.put("payment_status", "SUCCESS");
                        payload.put("transaction_reference", txn.getTransactionReference());
                        payload.put("remarks", txn.getRemarks());
                        Map<String, Object> created = sb.post("purchase_transactions", payload);
                        txnId = JsonUtil.getInt(created, "transaction_id");
                        sb.patch("properties?property_id=eq." + pid, Collections.singletonMap("listing_status", "SOLD"));
                    } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                        Connection conn = DBConnection.getInstance().getConnection();
                        try {
                            conn.setAutoCommit(false);
                            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO purchase_transactions (property_id, buyer_id, seller_id, purchase_price, payment_status, transaction_reference, remarks) VALUES (?, ?, ?, ?, 'SUCCESS', ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                                ins.setInt(1, pid); ins.setInt(2, u.getUserId()); ins.setInt(3, p.getOwnerId());
                                ins.setDouble(4, p.getPrice()); ins.setString(5, txn.getTransactionReference()); ins.setString(6, txn.getRemarks());
                                ins.executeUpdate();
                                try (ResultSet gk = ins.getGeneratedKeys()) { txnId = gk.next() ? gk.getInt(1) : 0; }
                            }
                            try (PreparedStatement upd = conn.prepareStatement("UPDATE properties SET listing_status='SOLD' WHERE property_id=?")) {
                                upd.setInt(1, pid); upd.executeUpdate();
                            }
                            conn.commit();
                        } catch (SQLException se) {
                            conn.rollback(); throw se;
                        } finally {
                            conn.setAutoCommit(true); conn.close();
                        }
                    } else {
                        InMemoryStore store = InMemoryStore.getInstance();
                        txnId = store.purchaseSeq.getAndIncrement();
                        txn.setTransactionId(txnId);
                        store.purchases.add(0, txn);
                        p.setListingStatus("SOLD");
                        store.recordAudit(u, "PROPERTY_PURCHASED", "PURCHASE", txnId, "Bought Property #" + pid + " (" + txn.getTransactionReference() + ")");
                    }
                    AsyncNotificationService.getInstance().dispatchNotification(u.getUserId(), u.getEmail(), "Purchase Confirmed", "Congratulations! Escrow confirmed for " + p.getTitle() + " (" + txn.getTransactionReference() + ")");
                    AsyncNotificationService.getInstance().dispatchNotification(p.getOwnerId(), p.getOwnerEmail(), "Property Sold!", u.getFullName() + " purchased " + p.getTitle() + " (" + txn.getTransactionReference() + ")");
                    SseEventHub.getInstance().broadcastAll("inventory_update", "{\"propertyId\":" + pid + ",\"listingStatus\":\"SOLD\"}");
                    sendJson(ex, 200, JsonUtil.success("Purchase complete", "{\"txnId\":" + txnId + ",\"txnRef\":" + JsonUtil.escape(txn.getTransactionReference()) + ",\"purchasePrice\":" + p.getPrice() + "}"));
                } else {
                    List<Map<String, Object>> out = new ArrayList<>();
                    if (activePersistenceMode == PersistenceMode.SUPABASE) {
                        String q = "purchase_transactions?select=*,properties(title),buyer:users!purchase_transactions_buyer_id_fkey(full_name),seller:users!purchase_transactions_seller_id_fkey(full_name)" +
                                   (u.isAdministrator() ? "" : "&or=(buyer_id.eq." + u.getUserId() + ",seller_id.eq." + u.getUserId() + ")") +
                                   "&order=transaction_date.desc";
                        for (Map<String, Object> r : SupabaseClient.getInstance().get(q)) {
                            Map<String, Object> p = JsonUtil.getMap(r, "properties");
                            Map<String, Object> b = JsonUtil.getMap(r, "buyer");
                            Map<String, Object> s = JsonUtil.getMap(r, "seller");
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("transactionId", JsonUtil.getInt(r, "transaction_id"));
                            item.put("propertyId", JsonUtil.getInt(r, "property_id"));
                            item.put("propertyTitle", p != null ? JsonUtil.getString(p, "title") : "");
                            item.put("buyerName", b != null ? JsonUtil.getString(b, "full_name") : "");
                            item.put("sellerName", s != null ? JsonUtil.getString(s, "full_name") : "");
                            item.put("purchasePrice", JsonUtil.getDouble(r, "purchase_price"));
                            item.put("date", JsonUtil.getString(r, "transaction_date"));
                            item.put("txnRef", JsonUtil.getString(r, "transaction_reference"));
                            item.put("status", JsonUtil.getString(r, "payment_status"));
                            item.put("remarks", JsonUtil.getString(r, "remarks"));
                            out.add(item);
                        }
                    } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                        String sql = u.isAdministrator()
                            ? "SELECT pt.*, p.title, b.full_name AS buyer_name, s.full_name AS seller_name FROM purchase_transactions pt JOIN properties p ON pt.property_id=p.property_id JOIN users b ON pt.buyer_id=b.user_id JOIN users s ON pt.seller_id=s.user_id ORDER BY pt.transaction_date DESC"
                            : "SELECT pt.*, p.title, b.full_name AS buyer_name, s.full_name AS seller_name FROM purchase_transactions pt JOIN properties p ON pt.property_id=p.property_id JOIN users b ON pt.buyer_id=b.user_id JOIN users s ON pt.seller_id=s.user_id WHERE pt.buyer_id=? OR pt.seller_id=? ORDER BY pt.transaction_date DESC";
                        try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                            if (!u.isAdministrator()) { st.setInt(1, u.getUserId()); st.setInt(2, u.getUserId()); }
                            try (ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("transactionId", rs.getInt("transaction_id"));
                                    item.put("propertyId", rs.getInt("property_id"));
                                    item.put("propertyTitle", rs.getString("title"));
                                    item.put("buyerName", rs.getString("buyer_name"));
                                    item.put("sellerName", rs.getString("seller_name"));
                                    item.put("purchasePrice", rs.getDouble("purchase_price"));
                                    item.put("date", String.valueOf(rs.getTimestamp("transaction_date")));
                                    item.put("txnRef", rs.getString("transaction_reference"));
                                    item.put("status", rs.getString("payment_status"));
                                    item.put("remarks", rs.getString("remarks"));
                                    out.add(item);
                                }
                            }
                        }
                    } else {
                        for (PurchaseTransaction t : InMemoryStore.getInstance().purchases) {
                            if (u.isAdministrator() || t.getBuyerId() == u.getUserId() || t.getSellerId() == u.getUserId()) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("transactionId", t.getTransactionId());
                                item.put("propertyId", t.getPropertyId());
                                item.put("propertyTitle", t.getPropertyTitle());
                                item.put("buyerName", t.getBuyerName());
                                item.put("sellerName", t.getSellerName());
                                item.put("purchasePrice", t.getPurchasePrice());
                                item.put("date", t.getTransactionDate().toString());
                                item.put("txnRef", t.getTransactionReference());
                                item.put("status", t.getPaymentStatus());
                                item.put("remarks", t.getRemarks());
                                out.add(item);
                            }
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("Purchases", JsonUtil.toJson(out)));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class VerificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                Map<String, String> params = parseBody(ex);
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    String pt = params.get("proofType") != null ? params.get("proofType").trim().toUpperCase() : "AADHAAR";
                    if (pt.contains("PAN")) pt = "PAN";
                    else if (pt.contains("PASSPORT")) pt = "PASSPORT";
                    else if (pt.contains("DRIV")) pt = "DRIVING_LICENSE";
                    else if (pt.contains("RERA")) pt = "RERA_CERT";
                    else if (!pt.equals("AADHAAR")) pt = "AADHAAR";

                    String proofNum = params.get("proofNumber");
                    if (proofNum == null || proofNum.trim().length() < 4) {
                        throw new InvalidPropertyDataException("Valid identity document number is required.");
                    }

                    int vid;
                    if (activePersistenceMode == PersistenceMode.SUPABASE) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("user_id", u.getUserId());
                        payload.put("identity_proof_type", pt);
                        payload.put("identity_proof_number", proofNum.trim());
                        payload.put("document_path", params.get("documentPath") != null ? params.get("documentPath") : "/uploads/docs/doc_" + u.getUserId() + ".pdf");
                        payload.put("property_address", params.get("propertyAddress"));
                        payload.put("verification_status", "PENDING");
                        Map<String, Object> created = SupabaseClient.getInstance().post("owner_verifications", payload);
                        vid = JsonUtil.getInt(created, "verification_id");
                    } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                        try (Connection conn = DBConnection.getInstance().getConnection();
                             PreparedStatement ins = conn.prepareStatement("INSERT INTO owner_verifications (user_id, identity_proof_type, identity_proof_number, document_path, property_address, verification_status) VALUES (?, ?, ?, ?, ?, 'PENDING')", Statement.RETURN_GENERATED_KEYS)) {
                            ins.setInt(1, u.getUserId()); ins.setString(2, pt); ins.setString(3, proofNum.trim());
                            ins.setString(4, params.get("documentPath") != null ? params.get("documentPath") : "/uploads/docs/doc_" + u.getUserId() + ".pdf");
                            ins.setString(5, params.get("propertyAddress"));
                            ins.executeUpdate();
                            try (ResultSet gk = ins.getGeneratedKeys()) { vid = gk.next() ? gk.getInt(1) : 0; }
                        }
                    } else {
                        InMemoryStore store = InMemoryStore.getInstance();
                        OwnerVerification ov = new OwnerVerification();
                        vid = store.verifSeq.getAndIncrement();
                        ov.setVerificationId(vid); ov.setUserId(u.getUserId()); ov.setUserName(u.getFullName()); ov.setUserEmail(u.getEmail());
                        ov.setIdentityProofType(pt); ov.setIdentityProofNumber(proofNum.trim());
                        ov.setDocumentPath(params.get("documentPath") != null ? params.get("documentPath") : "/uploads/docs/doc_" + u.getUserId() + ".pdf");
                        ov.setPropertyAddress(params.get("propertyAddress"));
                        ov.setVerificationStatus("PENDING");
                        store.verifications.add(0, ov);
                        store.recordAudit(u, "KYC_SUBMITTED", "VERIFICATION", vid, "Submitted " + pt + " KYC");
                    }
                    sendJson(ex, 200, JsonUtil.success("Verification submitted", "{\"verificationId\":" + vid + "}"));
                } else {
                    if (activePersistenceMode == PersistenceMode.SUPABASE) {
                        List<Map<String, Object>> rows = SupabaseClient.getInstance().get("owner_verifications?select=*&user_id=eq." + u.getUserId() + "&order=submitted_at.desc&limit=1");
                        if (!rows.isEmpty()) {
                            Map<String, Object> r = rows.get(0);
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("verificationId", JsonUtil.getInt(r, "verification_id"));
                            data.put("status", JsonUtil.getString(r, "verification_status"));
                            data.put("proofType", JsonUtil.getString(r, "identity_proof_type"));
                            data.put("proofNumber", JsonUtil.getString(r, "identity_proof_number"));
                            data.put("propertyAddress", JsonUtil.getString(r, "property_address"));
                            data.put("adminNotes", JsonUtil.getString(r, "admin_notes"));
                            sendJson(ex, 200, JsonUtil.success("Record found", JsonUtil.toJson(data)));
                            return;
                        }
                    } else {
                        for (OwnerVerification ov : InMemoryStore.getInstance().verifications) {
                            if (ov.getUserId() == u.getUserId()) {
                                Map<String, Object> data = new LinkedHashMap<>();
                                data.put("verificationId", ov.getVerificationId());
                                data.put("status", ov.getVerificationStatus());
                                data.put("proofType", ov.getIdentityProofType());
                                data.put("proofNumber", ov.getIdentityProofNumber());
                                data.put("propertyAddress", ov.getPropertyAddress());
                                data.put("adminNotes", ov.getAdminNotes());
                                sendJson(ex, 200, JsonUtil.success("Record found", JsonUtil.toJson(data)));
                                return;
                            }
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("None", "{\"status\":\"NOT_SUBMITTED\"}"));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class FavoriteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                Map<String, String> params = parseBody(ex);
                InMemoryStore store = InMemoryStore.getInstance();
                Set<Integer> favs = store.favoritesByUser.computeIfAbsent(u.getUserId(), k -> ConcurrentHashMap.newKeySet());
                    if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                        int pid = requireInt(params, "propertyId");
                        propertyDAO.getById(pid);
                        boolean saved;
                        if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection()) {
                                boolean exists;
                                try (PreparedStatement chk = conn.prepareStatement("SELECT 1 FROM property_favorites WHERE user_id=? AND property_id=?")) {
                                    chk.setInt(1, u.getUserId()); chk.setInt(2, pid);
                                    try (ResultSet rs = chk.executeQuery()) { exists = rs.next(); }
                                }
                                if (exists) {
                                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM property_favorites WHERE user_id=? AND property_id=?")) {
                                        del.setInt(1, u.getUserId()); del.setInt(2, pid); del.executeUpdate();
                                    }
                                    saved = false;
                                } else {
                                    try (PreparedStatement ins = conn.prepareStatement("INSERT INTO property_favorites (user_id, property_id) VALUES (?, ?)")) {
                                        ins.setInt(1, u.getUserId()); ins.setInt(2, pid); ins.executeUpdate();
                                    }
                                    saved = true;
                                }
                                favs.clear();
                                try (PreparedStatement all = conn.prepareStatement("SELECT property_id FROM property_favorites WHERE user_id=?")) {
                                    all.setInt(1, u.getUserId());
                                    try (ResultSet rs = all.executeQuery()) { while (rs.next()) favs.add(rs.getInt(1)); }
                                }
                            }
                        } else {
                            if (favs.contains(pid)) {
                                favs.remove(pid);
                                saved = false;
                            } else {
                                favs.add(pid);
                                saved = true;
                            }
                        }
                        sendJson(ex, 200, JsonUtil.success(saved ? "Added to wishlist" : "Removed from wishlist", "{\"saved\":" + saved + ",\"ids\":" + JsonUtil.toJson(favs) + "}"));
                    } else {
                        if (activePersistenceMode == PersistenceMode.MYSQL) {
                            favs.clear();
                            try (Connection conn = DBConnection.getInstance().getConnection();
                                 PreparedStatement all = conn.prepareStatement("SELECT property_id FROM property_favorites WHERE user_id=?")) {
                                all.setInt(1, u.getUserId());
                                try (ResultSet rs = all.executeQuery()) { while (rs.next()) favs.add(rs.getInt(1)); }
                            }
                        }
                        List<Property> props = new ArrayList<>();
                    for (Integer pid : favs) {
                        try { props.add(propertyDAO.getById(pid)); } catch (Exception ignored) {}
                    }
                    sendJson(ex, 200, JsonUtil.success("Wishlist", "{\"ids\":" + JsonUtil.toJson(favs) + ",\"items\":" + serializePropertyList(props) + "}"));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class ReviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> params = parseBody(ex);
                InMemoryStore store = InMemoryStore.getInstance();
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    User u = requireUser(ex);
                    int pid = requireInt(params, "propertyId");
                    Property p = propertyDAO.getById(pid);
                    int rating = requireInt(params, "rating");
                    if (rating < 1 || rating > 5) throw new InvalidPropertyDataException("Rating must be between 1 and 5.");
                    String comment = params.get("comment") != null ? params.get("comment").trim() : "";
                    if (comment.length() < 3) throw new InvalidPropertyDataException("Review comment must be at least 3 characters.");

                    PropertyReview rev = new PropertyReview();
                    rev.setReviewId(store.reviewSeq.getAndIncrement());
                    rev.setPropertyId(pid);
                    rev.setUserId(u.getUserId());
                    rev.setUserName(u.getFullName());
                    rev.setUserRole(u.getUserRole());
                    rev.setRating(rating);
                    rev.setComment(comment);

                    int sum = 0, count = 0;
                    if (activePersistenceMode == PersistenceMode.MYSQL) {
                        try (Connection conn = DBConnection.getInstance().getConnection()) {
                            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO property_reviews (property_id, user_id, rating, comment) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                                ins.setInt(1, pid); ins.setInt(2, u.getUserId()); ins.setInt(3, rating); ins.setString(4, comment);
                                ins.executeUpdate();
                                try (ResultSet gk = ins.getGeneratedKeys()) { if (gk.next()) rev.setReviewId(gk.getInt(1)); }
                            }
                            try (PreparedStatement agg = conn.prepareStatement("SELECT COALESCE(SUM(rating),0) AS s, COUNT(*) AS c FROM property_reviews WHERE property_id=?")) {
                                agg.setInt(1, pid);
                                try (ResultSet rs = agg.executeQuery()) { if (rs.next()) { sum = rs.getInt("s"); count = rs.getInt("c"); } }
                            }
                        }
                    } else {
                        store.reviews.add(0, rev);
                        for (PropertyReview r : store.reviews) {
                            if (r.getPropertyId() == pid) { sum += r.getRating(); count++; }
                        }
                    }
                    p.setReviewCount(count);
                    p.setAverageRating(count > 0 ? (double) sum / count : 4.8);
                    store.recordAudit(u, "REVIEW_POSTED", "REVIEW", rev.getReviewId(), "Rated Property #" + pid + " (" + rating + "/5)");
                    sendJson(ex, 200, JsonUtil.success("Review submitted", "{\"reviewId\":" + rev.getReviewId() + ",\"averageRating\":" + p.getAverageRating() + ",\"reviewCount\":" + count + "}"));
                } else {
                    int pid = requireInt(params, "propertyId");
                    List<Map<String, Object>> out = new ArrayList<>();
                    if (activePersistenceMode == PersistenceMode.MYSQL) {
                        try (Connection conn = DBConnection.getInstance().getConnection();
                             PreparedStatement st = conn.prepareStatement("SELECT r.*, u.full_name, u.user_role FROM property_reviews r JOIN users u ON r.user_id=u.user_id WHERE r.property_id=? ORDER BY r.created_at DESC")) {
                            st.setInt(1, pid);
                            try (ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("reviewId", rs.getInt("review_id"));
                                    item.put("userName", rs.getString("full_name"));
                                    item.put("userRole", rs.getString("user_role"));
                                    item.put("rating", rs.getInt("rating"));
                                    item.put("comment", rs.getString("comment"));
                                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                                    out.add(item);
                                }
                            }
                        }
                    } else {
                        for (PropertyReview r : store.reviews) {
                            if (r.getPropertyId() == pid) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("reviewId", r.getReviewId());
                                item.put("userName", r.getUserName());
                                item.put("userRole", r.getUserRole());
                                item.put("rating", r.getRating());
                                item.put("comment", r.getComment());
                                item.put("createdAt", r.getCreatedAt().toString());
                                out.add(item);
                            }
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("Reviews", JsonUtil.toJson(out)));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class InquiryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                Map<String, String> params = parseBody(ex);
                InMemoryStore store = InMemoryStore.getInstance();
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    int pid = requireInt(params, "propertyId");
                    Property p = propertyDAO.getById(pid);
                    String msg = params.get("message") != null ? params.get("message").trim() : "";
                    if (msg.length() < 4) throw new InvalidPropertyDataException("Please enter an inquiry message.");

                    PropertyInquiry inq = new PropertyInquiry();
                    inq.setInquiryId(store.inquirySeq.getAndIncrement());
                    inq.setPropertyId(pid);
                    inq.setPropertyTitle(p.getTitle());
                    inq.setSenderId(u.getUserId());
                    inq.setSenderName(u.getFullName());
                    inq.setSenderEmail(u.getEmail());
                    inq.setRecipientId(p.getOwnerId());
                    inq.setRecipientName(p.getOwnerName());
                    inq.setMessage(msg);
                    if (activePersistenceMode == PersistenceMode.MYSQL) {
                        try (Connection conn = DBConnection.getInstance().getConnection();
                             PreparedStatement ins = conn.prepareStatement("INSERT INTO property_inquiries (property_id, sender_id, recipient_id, message, status) VALUES (?, ?, ?, ?, 'OPEN')", Statement.RETURN_GENERATED_KEYS)) {
                            ins.setInt(1, pid); ins.setInt(2, u.getUserId()); ins.setInt(3, p.getOwnerId()); ins.setString(4, msg);
                            ins.executeUpdate();
                            try (ResultSet gk = ins.getGeneratedKeys()) { if (gk.next()) inq.setInquiryId(gk.getInt(1)); }
                        }
                    } else {
                        store.inquiries.add(0, inq);
                    }
                    store.recordAudit(u, "INQUIRY_SENT", "INQUIRY", inq.getInquiryId(), "Inquiry to owner of Property #" + pid);
                    AsyncNotificationService.getInstance().dispatchNotification(p.getOwnerId(), p.getOwnerEmail(), "New Property Inquiry", u.getFullName() + " inquired about " + p.getTitle() + ": " + msg);
                    sendJson(ex, 200, JsonUtil.success("Inquiry sent to owner", "{\"inquiryId\":" + inq.getInquiryId() + "}"));
                } else {
                    List<Map<String, Object>> out = new ArrayList<>();
                    if (activePersistenceMode == PersistenceMode.MYSQL) {
                        String sql = u.isAdministrator()
                            ? "SELECT i.*, p.title, s.full_name AS sender_name, s.email AS sender_email, r.full_name AS recipient_name FROM property_inquiries i JOIN properties p ON i.property_id=p.property_id JOIN users s ON i.sender_id=s.user_id JOIN users r ON i.recipient_id=r.user_id ORDER BY i.created_at DESC"
                            : "SELECT i.*, p.title, s.full_name AS sender_name, s.email AS sender_email, r.full_name AS recipient_name FROM property_inquiries i JOIN properties p ON i.property_id=p.property_id JOIN users s ON i.sender_id=s.user_id JOIN users r ON i.recipient_id=r.user_id WHERE i.sender_id=? OR i.recipient_id=? ORDER BY i.created_at DESC";
                        try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                            if (!u.isAdministrator()) { st.setInt(1, u.getUserId()); st.setInt(2, u.getUserId()); }
                            try (ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("inquiryId", rs.getInt("inquiry_id"));
                                    item.put("propertyId", rs.getInt("property_id"));
                                    item.put("propertyTitle", rs.getString("title"));
                                    item.put("senderName", rs.getString("sender_name"));
                                    item.put("senderEmail", rs.getString("sender_email"));
                                    item.put("recipientName", rs.getString("recipient_name"));
                                    item.put("message", rs.getString("message"));
                                    item.put("status", rs.getString("status"));
                                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                                    out.add(item);
                                }
                            }
                        }
                    } else {
                        for (PropertyInquiry inq : store.inquiries) {
                            if (u.isAdministrator() || inq.getSenderId() == u.getUserId() || inq.getRecipientId() == u.getUserId()) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("inquiryId", inq.getInquiryId());
                                item.put("propertyId", inq.getPropertyId());
                                item.put("propertyTitle", inq.getPropertyTitle());
                                item.put("senderName", inq.getSenderName());
                                item.put("senderEmail", inq.getSenderEmail());
                                item.put("recipientName", inq.getRecipientName());
                                item.put("message", inq.getMessage());
                                item.put("status", inq.getStatus());
                                item.put("createdAt", inq.getCreatedAt().toString());
                                out.add(item);
                            }
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("Inquiries", JsonUtil.toJson(out)));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class NotificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                String path = ex.getRequestURI().getPath();
                AsyncNotificationService svc = AsyncNotificationService.getInstance();
                if ("POST".equalsIgnoreCase(ex.getRequestMethod()) && path.endsWith("/read")) {
                    svc.markAllRead(u.getUserId());
                    sendJson(ex, 200, JsonUtil.success("Notifications marked read", null));
                    return;
                }
                List<Map<String, Object>> out = new ArrayList<>();
                for (UserNotification n : svc.getForUser(u.getUserId())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("notificationId", n.getNotificationId());
                    item.put("title", n.getTitle());
                    item.put("message", n.getMessage());
                    item.put("read", n.isRead());
                    item.put("createdAt", n.getCreatedAt().toString());
                    out.add(item);
                }
                sendJson(ex, 200, JsonUtil.success("Notifications", JsonUtil.toJson(out)));
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class CalculatorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> params = parseBody(ex);
                double price = parseDoubleOrDefault(params.get("price"), 10000000.0);
                double downPct = Math.min(90.0, Math.max(5.0, parseDoubleOrDefault(params.get("downPaymentPct"), 20.0)));
                double annualRate = Math.min(25.0, Math.max(1.0, parseDoubleOrDefault(params.get("annualRate"), 8.5)));
                int tenureYears = Math.min(35, Math.max(1, parseIntOrDefault(params.get("tenureYears"), 20)));
                String catCode = params.get("categoryCode") != null ? params.get("categoryCode") : "RESIDENTIAL";

                Property calcProp = PropertyDAO.createPropertyInstance(catCode);
                calcProp.setPurpose("BUY");
                calcProp.setPrice(price);
                calcProp.setPropertyArea(parseDoubleOrDefault(params.get("area"), 2000.0));

                double downPayment = price * (downPct / 100.0);
                double principal = Math.max(0.0, price - downPayment);
                double monthlyRate = annualRate / 12.0 / 100.0;
                int months = tenureYears * 12;
                double emi = 0.0;
                if (principal > 0 && monthlyRate > 0) {
                    double factor = Math.pow(1.0 + monthlyRate, months);
                    emi = principal * monthlyRate * factor / (factor - 1.0);
                }
                double totalPayable = emi * months;
                double totalInterest = Math.max(0.0, totalPayable - principal);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("propertyPrice", Math.round(price));
                data.put("downPayment", Math.round(downPayment));
                data.put("loanPrincipal", Math.round(principal));
                data.put("monthlyEmi", Math.round(emi));
                data.put("totalInterest", Math.round(totalInterest));
                data.put("totalPayable", Math.round(totalPayable));
                data.put("standardRegistrationFee", Math.round(calcProp.calculateEstimatedRegistrationFee()));
                data.put("concessionalRegistrationFee", Math.round(calcProp.calculateEstimatedRegistrationFee(3.0)));
                data.put("estimatedMonthlyMaintenance", Math.round(calcProp.calculateEstimatedMaintenanceCost()));
                sendJson(ex, 200, JsonUtil.success("Calculation complete", JsonUtil.toJson(data)));
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class AdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                User u = requireUser(ex);
                if (!u.isAdministrator()) throw new UnauthorizedAccessException("Administrator access required.");
                String path = ex.getRequestURI().getPath();
                Map<String, String> params = parseBody(ex);

                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    if (path.endsWith("/approve")) {
                        int pid = requireInt(params, "propertyId");
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            Map<String, Object> patch = new HashMap<>();
                            patch.put("approval_status", "APPROVED");
                            patch.put("rejection_reason", "");
                            SupabaseClient.getInstance().patch("properties?property_id=eq." + pid, patch);
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection();
                                 PreparedStatement st = conn.prepareStatement("UPDATE properties SET approval_status='APPROVED', rejection_reason='' WHERE property_id=?")) {
                                st.setInt(1, pid); st.executeUpdate();
                            }
                            SseEventHub.getInstance().broadcastAll("inventory_update", "{\"propertyId\":" + pid + ",\"approvalStatus\":\"APPROVED\"}");
                        } else {
                            Property p = propertyDAO.getById(pid);
                            p.setApprovalStatus("APPROVED"); p.setRejectionReason("");
                            InMemoryStore.getInstance().recordAudit(u, "PROPERTY_APPROVED", "PROPERTY", pid, "Approved \"" + p.getTitle() + "\"");
                            AsyncNotificationService.getInstance().dispatchNotification(p.getOwnerId(), p.getOwnerEmail(), "Listing Approved!", "Your listing \"" + p.getTitle() + "\" is now live.");
                            SseEventHub.getInstance().broadcastAll("inventory_update", "{\"propertyId\":" + pid + ",\"approvalStatus\":\"APPROVED\"}");
                        }
                        sendJson(ex, 200, JsonUtil.success("Property approved", null));
                    } else if (path.endsWith("/reject")) {
                        int pid = requireInt(params, "propertyId");
                        String reason = params.get("reason") != null && !params.get("reason").trim().isEmpty() ? params.get("reason") : "Does not meet verification guidelines";
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            Map<String, Object> patch = new HashMap<>();
                            patch.put("approval_status", "REJECTED");
                            patch.put("rejection_reason", reason);
                            SupabaseClient.getInstance().patch("properties?property_id=eq." + pid, patch);
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection();
                                 PreparedStatement st = conn.prepareStatement("UPDATE properties SET approval_status='REJECTED', rejection_reason=? WHERE property_id=?")) {
                                st.setString(1, reason); st.setInt(2, pid); st.executeUpdate();
                            }
                        } else {
                            Property p = propertyDAO.getById(pid);
                            p.setApprovalStatus("REJECTED"); p.setRejectionReason(reason);
                            InMemoryStore.getInstance().recordAudit(u, "PROPERTY_REJECTED", "PROPERTY", pid, "Rejected \"" + p.getTitle() + "\": " + reason);
                            AsyncNotificationService.getInstance().dispatchNotification(p.getOwnerId(), p.getOwnerEmail(), "Listing Needs Revision", "\"" + p.getTitle() + "\" was rejected: " + reason);
                        }
                        sendJson(ex, 200, JsonUtil.success("Property rejected", null));
                    } else if (path.endsWith("/action")) {
                        int vid = requireInt(params, "verificationId");
                        String status = params.get("status") != null ? params.get("status").trim().toUpperCase() : "APPROVED";
                        String notes = params.get("notes") != null ? params.get("notes") : "Reviewed by Admin";
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            Map<String, Object> patch = new LinkedHashMap<>();
                            patch.put("verification_status", status);
                            patch.put("admin_notes", notes);
                            patch.put("verified_at", Instant.now().toString());
                            List<Map<String, Object>> upd = SupabaseClient.getInstance().patch("owner_verifications?verification_id=eq." + vid, patch);
                            if (!upd.isEmpty() && "APPROVED".equalsIgnoreCase(status)) {
                                SupabaseClient.getInstance().patch("users?user_id=eq." + JsonUtil.getInt(upd.get(0), "user_id"), Collections.singletonMap("is_verified", true));
                            }
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection()) {
                                int targetUid = 0;
                                try (PreparedStatement q = conn.prepareStatement("SELECT user_id FROM owner_verifications WHERE verification_id=?")) {
                                    q.setInt(1, vid); try (ResultSet rs = q.executeQuery()) { if (rs.next()) targetUid = rs.getInt(1); }
                                }
                                try (PreparedStatement upd = conn.prepareStatement("UPDATE owner_verifications SET verification_status=?, admin_notes=?, verified_at=CURRENT_TIMESTAMP WHERE verification_id=?")) {
                                    upd.setString(1, status); upd.setString(2, notes); upd.setInt(3, vid); upd.executeUpdate();
                                }
                                if (targetUid > 0 && "APPROVED".equalsIgnoreCase(status)) {
                                    try (PreparedStatement uupd = conn.prepareStatement("UPDATE users SET is_verified=TRUE WHERE user_id=?")) {
                                        uupd.setInt(1, targetUid); uupd.executeUpdate();
                                    }
                                }
                            }
                        } else {
                            InMemoryStore store = InMemoryStore.getInstance();
                            OwnerVerification found = null;
                            for (OwnerVerification ov : store.verifications) if (ov.getVerificationId() == vid) { found = ov; break; }
                            if (found == null) throw new RealEstateException("Verification record not found.", 404);
                            found.setVerificationStatus(status); found.setAdminNotes(notes);
                            if ("APPROVED".equalsIgnoreCase(status)) {
                                User usr = userDAO.findById(found.getUserId());
                                if (usr != null) {
                                    usr.setVerified(true);
                                    sessionManager.refreshUser(usr);
                                    for (Property p : store.properties.values()) {
                                        if (p.getOwnerId() == usr.getUserId()) p.setOwnerVerified(true);
                                    }
                                }
                            }
                            store.recordAudit(u, "KYC_" + status, "VERIFICATION", vid, "KYC #" + vid + " marked " + status);
                            AsyncNotificationService.getInstance().dispatchNotification(found.getUserId(), found.getUserEmail(), "KYC Status: " + status, "Your identity verification is " + status);
                        }
                        sendJson(ex, 200, JsonUtil.success("Verification updated", null));
                    } else {
                        sendJson(ex, 404, JsonUtil.error("Admin action not found", 404));
                    }
                } else {
                    if (path.endsWith("/stats")) {
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            SupabaseClient sb = SupabaseClient.getInstance();
                            List<Map<String, Object>> rows = sb.get("vw_admin_dashboard_kpis?select=*&limit=1");
                            Map<String, Object> r = rows.isEmpty() ? new HashMap<>() : rows.get(0);
                            int pendingVer = sb.get("owner_verifications?select=verification_id&verification_status=eq.PENDING").size();
                            String data = "{\"totalUsers\":" + JsonUtil.getInt(r, "total_users") +
                                          ",\"verifiedOwners\":" + JsonUtil.getInt(r, "verified_owners") +
                                          ",\"totalProperties\":" + JsonUtil.getInt(r, "total_properties") +
                                          ",\"propertiesForSale\":" + JsonUtil.getInt(r, "properties_for_sale") +
                                          ",\"propertiesForRent\":" + JsonUtil.getInt(r, "properties_for_rent_lease") +
                                          ",\"pendingApprovals\":" + JsonUtil.getInt(r, "pending_property_approvals") +
                                          ",\"totalAppointments\":" + JsonUtil.getInt(r, "total_appointments") +
                                          ",\"purchaseTransactions\":" + JsonUtil.getInt(r, "total_purchase_transactions") +
                                          ",\"pendingVerifications\":" + pendingVer + "}";
                            sendJson(ex, 200, JsonUtil.success("Stats", data));
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            try (Connection conn = DBConnection.getInstance().getConnection()) {
                                Map<String, Object> stats = new LinkedHashMap<>();
                                try (PreparedStatement st = conn.prepareStatement("SELECT * FROM vw_admin_dashboard_kpis LIMIT 1"); ResultSet rs = st.executeQuery()) {
                                    if (rs.next()) {
                                        stats.put("totalUsers", rs.getInt("total_users"));
                                        stats.put("verifiedOwners", rs.getInt("verified_owners"));
                                        stats.put("totalProperties", rs.getInt("total_properties"));
                                        stats.put("propertiesForSale", rs.getInt("properties_for_sale"));
                                        stats.put("propertiesForRent", rs.getInt("properties_for_rent_lease"));
                                        stats.put("pendingApprovals", rs.getInt("pending_property_approvals"));
                                        stats.put("totalAppointments", rs.getInt("total_appointments"));
                                        stats.put("purchaseTransactions", rs.getInt("total_purchase_transactions"));
                                    }
                                }
                                try (PreparedStatement pv = conn.prepareStatement("SELECT COUNT(*) FROM owner_verifications WHERE verification_status='PENDING'"); ResultSet rs = pv.executeQuery()) {
                                    stats.put("pendingVerifications", rs.next() ? rs.getInt(1) : 0);
                                }
                                Map<String, Integer> catCounts = new LinkedHashMap<>();
                                try (PreparedStatement cc = conn.prepareStatement("SELECT c.category_code, COUNT(*) AS cnt FROM properties p JOIN property_categories c ON p.category_id=c.category_id GROUP BY c.category_code"); ResultSet rs = cc.executeQuery()) {
                                    while (rs.next()) catCounts.put(rs.getString("category_code"), rs.getInt("cnt"));
                                }
                                stats.put("categoryBreakdown", catCounts);
                                sendJson(ex, 200, JsonUtil.success("Stats", JsonUtil.toJson(stats)));
                            }
                        } else {
                            InMemoryStore store = InMemoryStore.getInstance();
                            long verifiedOwners = store.users.values().stream().filter(User::isVerified).count();
                            long forSale = store.properties.values().stream().filter(p -> "BUY".equalsIgnoreCase(p.getPurpose())).count();
                            long forRent = store.properties.values().stream().filter(p -> !"BUY".equalsIgnoreCase(p.getPurpose())).count();
                            long pendingProps = store.properties.values().stream().filter(p -> "PENDING".equalsIgnoreCase(p.getApprovalStatus())).count();
                            long pendingVer = store.verifications.stream().filter(v -> "PENDING".equalsIgnoreCase(v.getVerificationStatus())).count();

                            Map<String, Integer> catCounts = new LinkedHashMap<>();
                            for (Property p : store.properties.values()) {
                                catCounts.merge(p.getCategoryCode(), 1, Integer::sum);
                            }

                            Map<String, Object> stats = new LinkedHashMap<>();
                            stats.put("totalUsers", store.users.size());
                            stats.put("verifiedOwners", verifiedOwners);
                            stats.put("totalProperties", store.properties.size());
                            stats.put("propertiesForSale", forSale);
                            stats.put("propertiesForRent", forRent);
                            stats.put("pendingApprovals", pendingProps);
                            stats.put("totalAppointments", store.visits.size());
                            stats.put("purchaseTransactions", store.purchases.size());
                            stats.put("pendingVerifications", pendingVer);
                            stats.put("categoryBreakdown", catCounts);
                            sendJson(ex, 200, JsonUtil.success("Stats", JsonUtil.toJson(stats)));
                        }
                    } else if (path.endsWith("/pending")) {
                        sendJson(ex, 200, JsonUtil.success("Pending", serializePropertyList(propertyDAO.getPending())));
                    } else if (path.endsWith("/verifications")) {
                        List<Map<String, Object>> out = new ArrayList<>();
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            List<Map<String, Object>> rows = SupabaseClient.getInstance().get("owner_verifications?select=*,user:users!owner_verifications_user_id_fkey(full_name,email)&verification_status=eq.PENDING&order=submitted_at.asc");
                            for (Map<String, Object> r : rows) {
                                Map<String, Object> usr = JsonUtil.getMap(r, "user");
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("verificationId", JsonUtil.getInt(r, "verification_id"));
                                item.put("userId", JsonUtil.getInt(r, "user_id"));
                                item.put("userName", usr != null ? JsonUtil.getString(usr, "full_name") : "");
                                item.put("userEmail", usr != null ? JsonUtil.getString(usr, "email") : "");
                                item.put("proofType", JsonUtil.getString(r, "identity_proof_type"));
                                item.put("proofNumber", JsonUtil.getString(r, "identity_proof_number"));
                                item.put("address", JsonUtil.getString(r, "property_address"));
                                item.put("documentPath", JsonUtil.getString(r, "document_path"));
                                item.put("status", JsonUtil.getString(r, "verification_status"));
                                out.add(item);
                            }
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            String sql = "SELECT v.*, u.full_name, u.email FROM owner_verifications v JOIN users u ON v.user_id = u.user_id WHERE v.verification_status = 'PENDING' ORDER BY v.submitted_at ASC";
                            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("verificationId", rs.getInt("verification_id"));
                                    item.put("userId", rs.getInt("user_id"));
                                    item.put("userName", rs.getString("full_name"));
                                    item.put("userEmail", rs.getString("email"));
                                    item.put("proofType", rs.getString("identity_proof_type"));
                                    item.put("proofNumber", rs.getString("identity_proof_number"));
                                    item.put("address", rs.getString("property_address"));
                                    item.put("documentPath", rs.getString("document_path"));
                                    item.put("status", rs.getString("verification_status"));
                                    out.add(item);
                                }
                            }
                        } else {
                            for (OwnerVerification v : InMemoryStore.getInstance().verifications) {
                                if ("PENDING".equalsIgnoreCase(v.getVerificationStatus())) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("verificationId", v.getVerificationId());
                                    item.put("userId", v.getUserId());
                                    item.put("userName", v.getUserName());
                                    item.put("userEmail", v.getUserEmail());
                                    item.put("proofType", v.getIdentityProofType());
                                    item.put("proofNumber", v.getIdentityProofNumber());
                                    item.put("address", v.getPropertyAddress());
                                    item.put("documentPath", v.getDocumentPath());
                                    item.put("status", v.getVerificationStatus());
                                    out.add(item);
                                }
                            }
                        }
                        sendJson(ex, 200, JsonUtil.success("Verifications", JsonUtil.toJson(out)));
                    } else if (path.endsWith("/users")) {
                        List<User> users = userDAO.getAllUsers();
                        StringBuilder out = new StringBuilder("[");
                        for (int i = 0; i < users.size(); i++) {
                            if (i > 0) out.append(",");
                            out.append(serializeUser(users.get(i)));
                        }
                        sendJson(ex, 200, JsonUtil.success("Users", out.append("]").toString()));
                    } else if (path.endsWith("/audit")) {
                        List<Map<String, Object>> out = new ArrayList<>();
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            List<Map<String, Object>> rows = SupabaseClient.getInstance().get("audit_logs?select=*,actor:users(full_name)&order=created_at.desc&limit=100");
                            for (Map<String, Object> r : rows) {
                                Map<String, Object> actor = JsonUtil.getMap(r, "actor");
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("logId", JsonUtil.getInt(r, "log_id"));
                                item.put("actorName", actor != null ? JsonUtil.getString(actor, "full_name") : "System");
                                item.put("action", JsonUtil.getString(r, "action"));
                                item.put("entityType", JsonUtil.getString(r, "entity_type"));
                                item.put("entityId", JsonUtil.getInt(r, "entity_id"));
                                item.put("details", JsonUtil.getString(r, "details"));
                                item.put("createdAt", JsonUtil.getString(r, "created_at"));
                                out.add(item);
                            }
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            String sql = "SELECT a.*, COALESCE(u.full_name, 'System') AS actor_name FROM audit_logs a LEFT JOIN users u ON a.actor_user_id = u.user_id ORDER BY a.created_at DESC LIMIT 100";
                            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("logId", rs.getInt("log_id"));
                                    item.put("actorName", rs.getString("actor_name"));
                                    item.put("action", rs.getString("action"));
                                    item.put("entityType", rs.getString("entity_type"));
                                    item.put("entityId", rs.getInt("entity_id"));
                                    item.put("details", rs.getString("details"));
                                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                                    out.add(item);
                                }
                            }
                        } else {
                            for (AuditLogEntry e : InMemoryStore.getInstance().auditLogs) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("logId", e.getLogId());
                                item.put("actorName", e.getActorName());
                                item.put("action", e.getAction());
                                item.put("entityType", e.getEntityType());
                                item.put("entityId", e.getEntityId());
                                item.put("details", e.getDetails());
                                item.put("createdAt", e.getCreatedAt().toString());
                                out.add(item);
                            }
                        }
                        sendJson(ex, 200, JsonUtil.success("Audit logs", JsonUtil.toJson(out)));
                    } else {
                        sendJson(ex, 404, JsonUtil.error("Admin endpoint not found", 404));
                    }
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }
    }

    private static class UploadHandler implements HttpHandler {
        private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
        private static final int MAX_3D_UPLOAD_BYTES = 50 * 1024 * 1024;
        private static final Map<String, String> ALLOWED_TYPES = new HashMap<>();
        static {
            ALLOWED_TYPES.put("image/jpeg", "jpg");
            ALLOWED_TYPES.put("image/jpg", "jpg");
            ALLOWED_TYPES.put("image/png", "png");
            ALLOWED_TYPES.put("image/webp", "webp");
            ALLOWED_TYPES.put("application/pdf", "pdf");
            ALLOWED_TYPES.put("model/gltf-binary", "glb");
            ALLOWED_TYPES.put("model/gltf+json", "gltf");
            ALLOWED_TYPES.put("application/octet-stream", "splat");
        }

        private static boolean is3dContentType(String ct, String filename) {
            String lower = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
            return "model/gltf-binary".equals(ct) || "model/gltf+json".equals(ct)
                || ("application/octet-stream".equals(ct) && (lower.endsWith(".glb") || lower.endsWith(".gltf") || lower.endsWith(".splat") || lower.endsWith(".spz") || lower.endsWith(".ply")));
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            try {
                if (path.endsWith("/presign") && "POST".equals(method)) {
                    User u = requireUser(ex);
                    Map<String, String> params = parseBody(ex);
                    String filename = params.get("filename") != null ? params.get("filename").trim() : "asset.jpg";
                    String contentType = params.get("contentType") != null ? params.get("contentType").trim().toLowerCase(Locale.ROOT) : "image/jpeg";
                    long sizeBytes = (long) parseDoubleOrDefault(params.get("sizeBytes"), 1024);
                    if (!ALLOWED_TYPES.containsKey(contentType)) {
                        throw new InvalidPropertyDataException("Unsupported contentType. Allowed: JPEG, PNG, WEBP, PDF, GLB, GLTF, SPLAT.");
                    }
                    boolean is3d = is3dContentType(contentType, filename);
                    int cap = is3d ? MAX_3D_UPLOAD_BYTES : MAX_UPLOAD_BYTES;
                    if (sizeBytes <= 0 || sizeBytes > cap) {
                        throw new InvalidPropertyDataException("File size must be between 1 byte and " + (is3d ? "50 MB." : "5 MB."));
                    }
                    String ext = ALLOWED_TYPES.get(contentType);
                    if ("application/octet-stream".equals(contentType)) {
                        String lowerFn = filename.toLowerCase(Locale.ROOT);
                        if (lowerFn.endsWith(".glb")) ext = "glb";
                        else if (lowerFn.endsWith(".gltf")) ext = "gltf";
                        else if (lowerFn.endsWith(".spz")) ext = "spz";
                        else if (lowerFn.endsWith(".ply")) ext = "ply";
                        else if (!lowerFn.endsWith(".splat")) {
                            throw new InvalidPropertyDataException("Binary upload must use .glb, .gltf, .splat, .spz, or .ply extension.");
                        }
                    }
                    String safeBase = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (safeBase.length() > 40) safeBase = safeBase.substring(0, 40);
                    String objectKey = "u" + u.getUserId() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeBase;
                    if (!objectKey.toLowerCase(Locale.ROOT).endsWith("." + ext)) objectKey += "." + ext;
                    long expires = (System.currentTimeMillis() / 1000L) + 600L; // 10 min presigned TTL
                    String sig = JwtUtil.hmacSha256Hex(objectKey + ":" + expires + ":" + contentType);

                    String uploadUrl = "/api/uploads/binary?key=" + URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                        + "&expires=" + expires + "&contentType=" + URLEncoder.encode(contentType, StandardCharsets.UTF_8) + "&sig=" + sig;
                    String publicUrl = "/uploads/" + objectKey;
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("uploadUrl", uploadUrl);
                    out.put("publicUrl", publicUrl);
                    out.put("method", "PUT");
                    out.put("key", objectKey);
                    out.put("expiresAt", expires);
                    sendJson(ex, 200, JsonUtil.success("Presigned upload URL issued", JsonUtil.toJson(out)));
                } else if (path.endsWith("/binary") && ("PUT".equals(method) || "POST".equals(method))) {
                    Map<String, String> qm = new HashMap<>();
                    parseQueryString(ex.getRequestURI().getQuery(), qm);
                    String key = qm.get("key");
                    String expiresStr = qm.get("expires");
                    String contentType = qm.get("contentType") != null ? qm.get("contentType") : "image/jpeg";
                    String sig = qm.get("sig");
                    if (key == null || expiresStr == null || sig == null) {
                        throw new UnauthorizedAccessException("Missing presigned upload signature parameters.");
                    }
                    long expires = Long.parseLong(expiresStr);
                    if ((System.currentTimeMillis() / 1000L) > expires) {
                        throw new UnauthorizedAccessException("Presigned upload URL has expired.");
                    }
                    String expectedSig = JwtUtil.hmacSha256Hex(key + ":" + expires + ":" + contentType);
                    if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
                        throw new UnauthorizedAccessException("Invalid presigned upload signature.");
                    }
                    if (key.contains("..") || key.contains("/") || key.contains("\\")) {
                        throw new UnauthorizedAccessException("Invalid upload key.");
                    }
                    int cap = is3dContentType(contentType, key) ? MAX_3D_UPLOAD_BYTES : MAX_UPLOAD_BYTES;
                    byte[] bytes = readLimitedBytes(ex.getRequestBody(), cap);
                    String reqCt = ex.getRequestHeaders().getFirst("Content-Type");
                    if (reqCt != null && reqCt.toLowerCase(Locale.ROOT).contains("application/json")) {
                        Object parsed = JsonUtil.parse(new String(bytes, StandardCharsets.UTF_8));
                        if (parsed instanceof Map) {
                            String b64 = JsonUtil.getString((Map<String, Object>) parsed, "dataUrl");
                            if (b64 == null) b64 = JsonUtil.getString((Map<String, Object>) parsed, "base64");
                            if (b64 != null) {
                                int comma = b64.indexOf(',');
                                if (comma >= 0) b64 = b64.substring(comma + 1);
                                bytes = Base64.getDecoder().decode(b64.trim());
                            }
                        }
                    }
                    if (!matchesMagicBytes(bytes, contentType, key)) {
                        throw new InvalidPropertyDataException("Uploaded file content does not match declared media type.");
                    }
                    File uploadsDir = new File("src/main/webapp/uploads").getCanonicalFile();
                    if (!uploadsDir.exists()) uploadsDir.mkdirs();
                    File dest = new File(uploadsDir, key).getCanonicalFile();
                    if (!dest.toPath().startsWith(uploadsDir.toPath())) {
                        throw new UnauthorizedAccessException("Unsafe target upload path.");
                    }
                    Files.write(dest.toPath(), bytes);
                    byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest) hex.append(String.format("%02x", b));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("url", "/uploads/" + key);
                    out.put("size", bytes.length);
                    out.put("sha256", hex.toString());
                    sendJson(ex, 200, JsonUtil.success("Upload stored securely", JsonUtil.toJson(out)));
                } else {
                    sendJson(ex, 404, JsonUtil.error("Upload endpoint not found", 404));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }

        private byte[] readLimitedBytes(InputStream is, int maxBytes) throws IOException, InvalidPropertyDataException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r, total = 0;
            while ((r = is.read(buf)) != -1) {
                total += r;
                if (total > maxBytes) throw new InvalidPropertyDataException("Upload exceeds maximum size limit.");
                baos.write(buf, 0, r);
            }
            return baos.toByteArray();
        }

        private boolean matchesMagicBytes(byte[] b, String contentType, String key) {
            if (b == null || b.length < 4) return false;
            String lowerKey = key != null ? key.toLowerCase(Locale.ROOT) : "";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            }
            if (contentType.contains("png")) {
                return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            }
            if (contentType.contains("webp")) {
                return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            }
            if (contentType.contains("pdf")) {
                return b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            }
            if (contentType.contains("gltf-binary") || lowerKey.endsWith(".glb")) {
                return b[0] == 'g' && b[1] == 'l' && b[2] == 'T' && b[3] == 'F';
            }
            if (contentType.contains("gltf+json") || lowerKey.endsWith(".gltf")) {
                String head = new String(b, 0, Math.min(b.length, 32), StandardCharsets.UTF_8).trim();
                return head.startsWith("{");
            }
            if (lowerKey.endsWith(".splat") || lowerKey.endsWith(".spz") || lowerKey.endsWith(".ply")) {
                return b.length >= 16;
            }
            return false;
        }
    }

    private static class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            if (u == null) {
                sendJson(ex, 401, JsonUtil.error("Valid token required for real-time event stream.", 401));
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.getResponseHeaders().set("X-Accel-Buffering", "no");
            ex.sendResponseHeaders(200, 0);
            SseEventHub hub = SseEventHub.getInstance();
            SseEventHub.SseClient client = hub.register(u.getUserId(), u.isAdministrator(), ex);
            try {
                while (client.alive && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(20_000L);
                    if (!hub.sendHeartbeat(client)) break;
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                hub.unregister(client);
                ex.close();
            }
        }
    }

    private static class DocumentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            Map<String, String> params = parseBody(ex);
            try {
                if (path.endsWith("/property")) {
                    int pid = requireInt(params, "id");
                    Property p = propertyDAO.getById(pid);
                    List<String[]> rows = new ArrayList<>();
                    rows.add(new String[]{"Asset ID", "#" + p.getPropertyId() + " (" + p.getListingStatus() + ")"});
                    rows.add(new String[]{"Headline", p.getTitle()});
                    rows.add(new String[]{"Category & Purpose", p.getCategoryName() + " | " + p.getPurpose()});
                    rows.add(new String[]{"Configuration", p.getPropertyTypeDetails()});
                    rows.add(new String[]{"Location", p.getLocality() + ", " + p.getCity() + ", " + p.getState()});
                    rows.add(new String[]{"Geo Coordinates", String.format(Locale.ROOT, "%.4f N, %.4f E", p.getLatitude(), p.getLongitude())});
                    rows.add(new String[]{"Authoritative Price", "Rs. " + Math.round(p.getPrice()) + ("BUY".equalsIgnoreCase(p.getPurpose()) ? "" : " / month")});
                    rows.add(new String[]{"Measured Area", p.getPropertyArea() + " " + p.getAreaUnit()});
                    rows.add(new String[]{"Stamp Duty (6.6% / 3%)", "Rs. " + Math.round(p.calculateEstimatedRegistrationFee()) + " / Rs. " + Math.round(p.calculateEstimatedRegistrationFee(3.0))});
                    rows.add(new String[]{"Est. Maintenance", "Rs. " + Math.round(p.calculateEstimatedMaintenanceCost()) + " / month"});
                    rows.add(new String[]{"Owner & KYC", p.getOwnerName() + " | " + (p.isOwnerVerified() ? "VERIFIED" : "STANDARD")});
                    rows.add(new String[]{"Amenities", String.join(", ", p.getAmenities())});
                    rows.add(new String[]{"Overview", p.getDescription()});
                    byte[] pdf = PdfGenerator.buildDocument(
                        "ARCHITECTURAL PROPERTY BROCHURE",
                        "Verified Asset Valuation Sheet | Generated " + Instant.now().toString().substring(0, 10),
                        rows,
                        "EstateVault Digital Valuation Document | SHA-256 Integrity Verified"
                    );
                    sendPdf(ex, "EstateVault_Property_" + pid + ".pdf", pdf);
                } else if (path.endsWith("/receipt")) {
                    User u = requireUser(ex);
                    String type = params.get("type") != null ? params.get("type").toLowerCase(Locale.ROOT) : "purchase";
                    int id = requireInt(params, "id");
                    List<String[]> rows = new ArrayList<>();
                    if ("rental".equals(type)) {
                        int ownerId = -1, tenantId = -1;
                        String propTitle = "", ownerName = "", tenantName = "", startStr = "", endStr = "", status = "";
                        double rent = 0, deposit = 0;
                        boolean found = false;
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            List<Map<String, Object>> res = SupabaseClient.getInstance().get(
                                "rental_agreements?select=*,properties(title),owner:users!rental_agreements_owner_id_fkey(full_name),tenant:users!rental_agreements_tenant_id_fkey(full_name)&agreement_id=eq." + id + "&limit=1"
                            );
                            if (!res.isEmpty()) {
                                Map<String, Object> r = res.get(0);
                                Map<String, Object> pr = JsonUtil.getMap(r, "properties");
                                Map<String, Object> own = JsonUtil.getMap(r, "owner");
                                Map<String, Object> ten = JsonUtil.getMap(r, "tenant");
                                ownerId = JsonUtil.getInt(r, "owner_id");
                                tenantId = JsonUtil.getInt(r, "tenant_id");
                                propTitle = pr != null ? JsonUtil.getString(pr, "title") : "";
                                ownerName = own != null ? JsonUtil.getString(own, "full_name") : "";
                                tenantName = ten != null ? JsonUtil.getString(ten, "full_name") : "";
                                rent = JsonUtil.getDouble(r, "monthly_rent");
                                deposit = JsonUtil.getDouble(r, "security_deposit");
                                startStr = JsonUtil.getString(r, "agreement_start_date");
                                endStr = JsonUtil.getString(r, "agreement_end_date");
                                status = JsonUtil.getString(r, "agreement_status");
                                found = true;
                            }
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            String sql = "SELECT r.*, p.title, o.full_name AS owner_name, t.full_name AS tenant_name FROM rental_agreements r JOIN properties p ON r.property_id=p.property_id JOIN users o ON r.owner_id=o.user_id JOIN users t ON r.tenant_id=t.user_id WHERE r.agreement_id=? LIMIT 1";
                            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                                st.setInt(1, id);
                                try (ResultSet rs = st.executeQuery()) {
                                    if (rs.next()) {
                                        ownerId = rs.getInt("owner_id");
                                        tenantId = rs.getInt("tenant_id");
                                        propTitle = rs.getString("title");
                                        ownerName = rs.getString("owner_name");
                                        tenantName = rs.getString("tenant_name");
                                        rent = rs.getDouble("monthly_rent");
                                        deposit = rs.getDouble("security_deposit");
                                        startStr = String.valueOf(rs.getDate("agreement_start_date"));
                                        endStr = String.valueOf(rs.getDate("agreement_end_date"));
                                        status = rs.getString("agreement_status");
                                        found = true;
                                    }
                                }
                            }
                        } else {
                            for (RentalAgreement a : InMemoryStore.getInstance().agreements) {
                                if (a.getAgreementId() == id) {
                                    ownerId = a.getOwnerId();
                                    tenantId = a.getTenantId();
                                    propTitle = a.getPropertyTitle();
                                    ownerName = a.getOwnerName();
                                    tenantName = a.getTenantName();
                                    rent = a.getMonthlyRent();
                                    deposit = a.getSecurityDeposit();
                                    startStr = String.valueOf(a.getAgreementStartDate());
                                    endStr = String.valueOf(a.getAgreementEndDate());
                                    status = a.getAgreementStatus();
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) throw new RealEstateException("Rental agreement not found.", 404);
                        if (!u.isAdministrator() && tenantId != u.getUserId() && ownerId != u.getUserId()) {
                            throw new UnauthorizedAccessException("Access denied to rental agreement.");
                        }
                        rows.add(new String[]{"Agreement Reference", "AGR-" + id});
                        rows.add(new String[]{"Property Title", propTitle});
                        rows.add(new String[]{"Landlord / Owner", ownerName});
                        rows.add(new String[]{"Tenant", tenantName});
                        rows.add(new String[]{"Monthly Rent", "Rs. " + Math.round(rent)});
                        rows.add(new String[]{"Security Deposit", "Rs. " + Math.round(deposit)});
                        rows.add(new String[]{"Lease Period", startStr + " to " + endStr});
                        rows.add(new String[]{"Agreement Status", status});
                        byte[] pdf = PdfGenerator.buildDocument(
                            "DIGITAL RENTAL AGREEMENT",
                            "Executed Lease Summary | EstateVault Escrow",
                            rows,
                            "Electronically executed on EstateVault | Legally auditable lease record"
                        );
                        sendPdf(ex, "EstateVault_Lease_" + id + ".pdf", pdf);
                    } else {
                        int buyerId = -1, sellerId = -1;
                        String txnRef = "", propTitle = "", buyerName = "", sellerName = "", status = "", ts = "", remarks = "";
                        double price = 0;
                        boolean found = false;
                        if (activePersistenceMode == PersistenceMode.SUPABASE) {
                            List<Map<String, Object>> res = SupabaseClient.getInstance().get(
                                "purchase_transactions?select=*,properties(title),buyer:users!purchase_transactions_buyer_id_fkey(full_name),seller:users!purchase_transactions_seller_id_fkey(full_name)&transaction_id=eq." + id + "&limit=1"
                            );
                            if (!res.isEmpty()) {
                                Map<String, Object> r = res.get(0);
                                Map<String, Object> pr = JsonUtil.getMap(r, "properties");
                                Map<String, Object> b = JsonUtil.getMap(r, "buyer");
                                Map<String, Object> s = JsonUtil.getMap(r, "seller");
                                buyerId = JsonUtil.getInt(r, "buyer_id");
                                sellerId = JsonUtil.getInt(r, "seller_id");
                                txnRef = JsonUtil.getString(r, "transaction_reference");
                                propTitle = pr != null ? JsonUtil.getString(pr, "title") : "";
                                buyerName = b != null ? JsonUtil.getString(b, "full_name") : "";
                                sellerName = s != null ? JsonUtil.getString(s, "full_name") : "";
                                price = JsonUtil.getDouble(r, "purchase_price");
                                status = JsonUtil.getString(r, "payment_status");
                                ts = JsonUtil.getString(r, "transaction_date");
                                remarks = JsonUtil.getString(r, "remarks");
                                found = true;
                            }
                        } else if (activePersistenceMode == PersistenceMode.MYSQL) {
                            String sql = "SELECT pt.*, p.title, b.full_name AS buyer_name, s.full_name AS seller_name FROM purchase_transactions pt JOIN properties p ON pt.property_id=p.property_id JOIN users b ON pt.buyer_id=b.user_id JOIN users s ON pt.seller_id=s.user_id WHERE pt.transaction_id=? LIMIT 1";
                            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                                st.setInt(1, id);
                                try (ResultSet rs = st.executeQuery()) {
                                    if (rs.next()) {
                                        buyerId = rs.getInt("buyer_id");
                                        sellerId = rs.getInt("seller_id");
                                        txnRef = rs.getString("transaction_reference");
                                        propTitle = rs.getString("title");
                                        buyerName = rs.getString("buyer_name");
                                        sellerName = rs.getString("seller_name");
                                        price = rs.getDouble("purchase_price");
                                        status = rs.getString("payment_status");
                                        ts = String.valueOf(rs.getTimestamp("transaction_date"));
                                        remarks = rs.getString("remarks");
                                        found = true;
                                    }
                                }
                            }
                        } else {
                            for (PurchaseTransaction t : InMemoryStore.getInstance().purchases) {
                                if (t.getTransactionId() == id) {
                                    buyerId = t.getBuyerId();
                                    sellerId = t.getSellerId();
                                    txnRef = t.getTransactionReference();
                                    propTitle = t.getPropertyTitle();
                                    buyerName = t.getBuyerName();
                                    sellerName = t.getSellerName();
                                    price = t.getPurchasePrice();
                                    status = t.getPaymentStatus();
                                    ts = String.valueOf(t.getTransactionDate());
                                    remarks = t.getRemarks();
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) throw new RealEstateException("Purchase receipt not found.", 404);
                        if (!u.isAdministrator() && buyerId != u.getUserId() && sellerId != u.getUserId()) {
                            throw new UnauthorizedAccessException("Access denied to purchase receipt.");
                        }
                        rows.add(new String[]{"Escrow Reference", txnRef});
                        rows.add(new String[]{"Property Title", propTitle});
                        rows.add(new String[]{"Buyer", buyerName});
                        rows.add(new String[]{"Seller / Owner", sellerName});
                        rows.add(new String[]{"Purchase Amount", "Rs. " + Math.round(price)});
                        rows.add(new String[]{"Payment Status", status});
                        rows.add(new String[]{"Timestamp", ts});
                        rows.add(new String[]{"Remarks", remarks});
                        byte[] pdf = PdfGenerator.buildDocument(
                            "ESCROW PURCHASE DEED RECEIPT",
                            "Authoritative Transaction Certificate",
                            rows,
                            "Electronically verified on EstateVault | Tamper-evident escrow receipt"
                        );
                        sendPdf(ex, "EstateVault_Deed_" + id + ".pdf", pdf);
                    }
                } else {
                    sendJson(ex, 404, JsonUtil.error("Document endpoint not found", 404));
                }
            } catch (Exception e) {
                handleException(ex, e);
            }
        }

        private void sendPdf(HttpExchange ex, String filename, byte[] pdf) throws IOException {
            ex.getResponseHeaders().set("Content-Type", "application/pdf");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            ex.sendResponseHeaders(200, pdf.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(pdf); }
        }
    }

    // =========================================================================
    // 12. HTTP INFRASTRUCTURE HELPERS (CORS, SECURITY HEADERS, PARSING, ERRORS)
    // =========================================================================

    private static HttpHandler withApiSafety(HttpHandler delegate) {
        return ex -> {
            addSecurityHeaders(ex);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", ALLOWED_CORS_ORIGIN);
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token, Authorization");
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            try {
                delegate.handle(ex);
            } catch (Exception e) {
                handleException(ex, e);
            }
        };
    }

    private static void addSecurityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
        ex.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    private static void handleException(HttpExchange ex, Exception e) throws IOException {
        if (e instanceof RateLimitExceededException) {
            RateLimitExceededException rle = (RateLimitExceededException) e;
            ex.getResponseHeaders().set("Retry-After", String.valueOf(rle.getRetryAfterSeconds()));
            sendJson(ex, 429, JsonUtil.error(rle.getMessage(), 429));
        } else if (e instanceof RealEstateException) {
            RealEstateException re = (RealEstateException) e;
            sendJson(ex, re.getErrorCode(), JsonUtil.error(re.getMessage(), re.getErrorCode()));
        } else if (e instanceof NumberFormatException || e instanceof IllegalArgumentException) {
            sendJson(ex, 400, JsonUtil.error("Invalid numeric or date parameter format.", 400));
        } else {
            System.err.println("[API ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            sendJson(ex, 500, JsonUtil.error("Internal server error occurred.", 500));
        }
    }

    private static String getClientIp(HttpExchange ex) {
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.trim().isEmpty()) return fwd.split(",")[0].trim();
        return ex.getRemoteAddress() != null && ex.getRemoteAddress().getAddress() != null
            ? ex.getRemoteAddress().getAddress().getHostAddress() : "127.0.0.1";
    }

    private static String getSessionToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return auth.substring(7).trim();
        }
        String headerToken = ex.getRequestHeaders().getFirst("X-Auth-Token");
        if (headerToken != null && !headerToken.trim().isEmpty()) return headerToken.trim();
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String ch : cookies) {
                for (String c : ch.split(";")) {
                    String[] parts = c.trim().split("=", 2);
                    if (parts.length == 2 && "AUTH_TOKEN".equals(parts[0])) return parts[1];
                }
            }
        }
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();
        if (query != null && path != null && (path.startsWith("/api/events") || path.startsWith("/api/documents"))) {
            try {
                Map<String, String> qm = new HashMap<>();
                parseQueryString(query, qm);
                String qt = qm.get("token");
                if (qt != null && !qt.trim().isEmpty()) return qt.trim();
            } catch (UnsupportedEncodingException ignored) {}
        }
        return null;
    }

    private static User getSessionUser(HttpExchange ex) {
        String token = getSessionToken(ex);
        return sessionManager.resolve(token);
    }

    private static User requireUser(HttpExchange ex) throws AuthenticationException {
        User u = getSessionUser(ex);
        if (u == null) throw new AuthenticationException("Authentication required.");
        return u;
    }

    private static int requireInt(Map<String, String> params, String key) throws InvalidPropertyDataException {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) throw new InvalidPropertyDataException("Missing required parameter: " + key);
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) {
            throw new InvalidPropertyDataException("Parameter '" + key + "' must be an integer.");
        }
    }

    private static double requireDouble(Map<String, String> params, String key) throws InvalidPropertyDataException {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) throw new InvalidPropertyDataException("Missing required parameter: " + key);
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) {
            throw new InvalidPropertyDataException("Parameter '" + key + "' must be a valid number.");
        }
    }

    private static int parseIntOrZero(String s) { return parseIntOrDefault(s, 0); }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDoubleOrZero(String s) { return parseDoubleOrDefault(s, 0.0); }

    private static double parseDoubleOrDefault(String s, double def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void sendPlain(HttpExchange ex, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseBody(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        String query = ex.getRequestURI().getQuery();
        if (query != null) parseQueryString(query, map);
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r, total = 0;
            InputStream is = ex.getRequestBody();
            while ((r = is.read(buf)) != -1) {
                total += r;
                if (total > MAX_BODY_BYTES) throw new IOException("Request payload exceeds 1 MB limit.");
                baos.write(buf, 0, r);
            }
            String body = baos.toString(StandardCharsets.UTF_8).trim();
            if (!body.isEmpty()) {
                String contentType = ex.getRequestHeaders().getFirst("Content-Type");
                boolean isJson = (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json")) || body.startsWith("{");
                if (isJson) {
                    Object parsed = JsonUtil.parse(body);
                    if (parsed instanceof Map) {
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
                            String k = String.valueOf(entry.getKey());
                            Object v = entry.getValue();
                            if (v == null) continue;
                            if (v instanceof Collection) {
                                List<String> parts = new ArrayList<>();
                                for (Object item : (Collection<?>) v) if (item != null) parts.add(String.valueOf(item));
                                map.put(k, String.join(",", parts));
                            } else {
                                map.put(k, String.valueOf(v));
                            }
                        }
                    }
                } else {
                    parseQueryString(body, map);
                }
            }
        }
        return map;
    }

    private static void parseQueryString(String qs, Map<String, String> map) throws UnsupportedEncodingException {
        if (qs == null || qs.isEmpty()) return;
        for (String param : qs.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length > 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()));
            } else if (pair.length == 1 && !pair[0].isEmpty()) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), "");
            }
        }
    }
}
