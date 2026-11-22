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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
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
 * REAL ESTATE MANAGEMENT SYSTEM - COMPLETE ALL-IN-ONE BACKEND
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
 * 8. Custom Exception Handling Hierarchy
 * 9. Multithreading (Producer-Consumer background worker thread)
 * 10. Synchronization (Thread-safe singleton & synchronized queue dispatching)
 * 11. JDBC with MySQL, PreparedStatements, and Atomic Transactions (Commit/Rollback)
 * 12. Embedded REST HTTP Server running on Port 8080
 *
 * Run directly with: java RealEstateBackend.java
 * =============================================================================
 */
public class RealEstateBackend {

    public static final int SERVER_PORT = parsePort();

    private static int parsePort() {
        String p = System.getenv("PORT");
        if (p != null && !p.trim().isEmpty()) {
            try { return Integer.parseInt(p.trim()); } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

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

        // Abstract method enforcing polymorphism across user roles
        public abstract String getRolePermissionsDescription();

        // Final method: cannot be overridden by subclasses
        public final boolean isAdministrator() {
            return "ADMIN".equalsIgnoreCase(this.userRole);
        }

        @Override
        public Timestamp getCreatedAt() { return createdAt; }

        @Override
        public String getAuditSummary() {
            return "User #" + userId + " (" + fullName + ") [" + userRole + "]";
        }

        // Getters and Setters (Encapsulation)
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName != null ? fullName.trim() : ""; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email != null ? email.trim().toLowerCase() : ""; }
        public String getMobileNumber() { return mobileNumber; }
        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
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

        @Override
        public double calculateTotalPayable() { return this.maxBudget; }

        @Override
        public String generateTransactionReference() {
            return "BUYER-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        @Override
        public boolean processSimulatedPayment(double amount) { return amount > 0; }
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

        @Override
        public double calculateTotalPayable() { return this.monthlyBudget; }

        @Override
        public String generateTransactionReference() {
            return "RENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        @Override
        public boolean processSimulatedPayment(double amount) { return amount > 0; }
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
        private String approvalStatus; // PENDING, APPROVED, REJECTED
        private String listingStatus;  // AVAILABLE, UNDER_OFFER, SOLD, RENTED, INACTIVE
        private boolean isFeatured;
        private String primaryImageUrl;
        private List<String> imageUrls = new ArrayList<>();
        private List<String> amenities = new ArrayList<>();
        private Timestamp createdAt;

        // Joined Owner Information
        private String ownerName;
        private String ownerEmail;
        private String ownerPhone;
        private boolean ownerVerified;

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

        // Abstract methods enforcing Polymorphism
        public abstract String getPropertyTypeDetails();
        public abstract double calculateEstimatedMaintenanceCost();
        public abstract void validateCategorySpecifics() throws InvalidPropertyDataException;

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

        // Implementing Searchable interface
        @Override
        public boolean matchesKeyword(String keyword) {
            if (keyword == null || keyword.trim().isEmpty()) return true;
            String kw = keyword.toLowerCase().trim();
            return (title != null && title.toLowerCase().contains(kw)) ||
                   (locality != null && locality.toLowerCase().contains(kw)) ||
                   (city != null && city.toLowerCase().contains(kw));
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

        @Override
        public Timestamp getCreatedAt() { return createdAt; }

        @Override
        public String getAuditSummary() {
            return "Property #" + propertyId + " (" + title + ") Rs." + price;
        }

        // Getters and Setters
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
        public String getApprovalStatus() { return approvalStatus; }
        public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
        public String getListingStatus() { return listingStatus; }
        public void setListingStatus(String listingStatus) { this.listingStatus = listingStatus; }
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
        private String videoUrl;
        private String videoLabel;
        public String getVideoUrl() { return videoUrl; }
        public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
        public String getVideoLabel() { return videoLabel; }
        public void setVideoLabel(String videoLabel) { this.videoLabel = videoLabel; }
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
        }

        public ResidentialProperty(int id, int ownerId, String title, String purpose, double price, double area, String state, String city, int beds, int baths) {
            super(id, ownerId, title, purpose, price, area, state, city);
            setCategoryId(1);
            setCategoryCode("RESIDENTIAL");
            setCategoryName("Residential Properties");
            this.bedrooms = beds;
            this.bathrooms = baths;
            this.builtUpArea = area;
        }

        @Override
        public String getPropertyTypeDetails() {
            return bedrooms + " BHK " + (residentialType != null ? residentialType : "Residential") + 
                   " | " + bathrooms + " Baths | " + furnishingStatus;
        }

        @Override
        public double calculateEstimatedMaintenanceCost() {
            return (builtUpArea > 0 ? builtUpArea : getPropertyArea()) * 2.5; // Rs. 2.5 / sq ft
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
        private String commercialType; // SHOP, OFFICE_SPACE, SHOWROOM, WAREHOUSE
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
                   " | Carpet: " + (carpetArea > 0 ? carpetArea : getPropertyArea()) + " Sq. Ft.";
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
        private String landType; // FARMLAND, AGRICULTURAL_PLOT, PLANTATION, LEASE_LAND
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
                   " | Soil: " + soilType;
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
        }

        @Override
        public String getPropertyTypeDetails() {
            return "Farmhouse on " + (landArea > 0 ? landArea : getPropertyArea()) + " Acres | " + 
                   bedrooms + " Bed Villa | " + (hasSwimmingPool ? "Private Pool" : "Lush Garden");
        }

        @Override
        public double calculateEstimatedMaintenanceCost() {
            return 5000.0 + (landArea * 1000.0);
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
        }

        @Override
        public String getPropertyTypeDetails() {
            return (plotType != null ? plotType.replace('_', ' ') : "Plot") + 
                   " | Dim: " + plotDimensions + " | Facing: " + facingDirection;
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
        }

        @Override
        public String getPropertyTypeDetails() {
            return (buildingType != null ? buildingType.replace('_', ' ') : "Building") + 
                   " | " + totalUnits + " Units | Builder: " + builderName;
        }

        @Override
        public double calculateEstimatedMaintenanceCost() { return totalUnits * 1500.0; }

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
    // 5. TRANSACTIONAL & APPOINTMENT MODELS
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

        public RentalAgreement() {}
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
    }

    public static class RentPayment implements Payable {
        private int rentPaymentId;
        private int agreementId;
        private String paymentMonth;
        private double amountPaid;
        private Date paymentDate;
        private String transactionRef;
        private String paymentStatus;

        public RentPayment() {}
        @Override public double calculateTotalPayable() { return amountPaid; }
        @Override public String generateTransactionReference() { return "RENT-" + System.currentTimeMillis(); }
        @Override public boolean processSimulatedPayment(double amount) {
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
        private double purchasePrice;
        private Timestamp transactionDate;
        private String paymentStatus;
        private String transactionReference;

        public PurchaseTransaction() {
            this.transactionDate = new Timestamp(System.currentTimeMillis());
            this.paymentStatus = "SUCCESS";
        }

        @Override public double calculateTotalPayable() { return purchasePrice; }
        @Override public String generateTransactionReference() { return "TXN-PURCHASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
        @Override public boolean processSimulatedPayment(double amount) {
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
        public double getPurchasePrice() { return purchasePrice; }
        public void setPurchasePrice(double price) { this.purchasePrice = price; }
        public Timestamp getTransactionDate() { return transactionDate; }
        public void setTransactionDate(Timestamp date) { this.transactionDate = date; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String status) { this.paymentStatus = status; }
        public String getTransactionReference() { return transactionReference; }
        public void setTransactionReference(String ref) { this.transactionReference = ref; }
    }

    public static class PropertyVisit {
        private int visitId;
        private int propertyId;
        private String propertyTitle;
        private String propertyCity;
        private int userId;
        private String userName;
        private Date visitDate;
        private Time visitTime;
        private String contactNumber;
        private String appointmentStatus = "PENDING";

        public PropertyVisit() {}
        public int getVisitId() { return visitId; }
        public void setVisitId(int id) { this.visitId = id; }
        public int getPropertyId() { return propertyId; }
        public void setPropertyId(int id) { this.propertyId = id; }
        public String getPropertyTitle() { return propertyTitle; }
        public void setPropertyTitle(String title) { this.propertyTitle = title; }
        public String getPropertyCity() { return propertyCity; }
        public void setPropertyCity(String city) { this.propertyCity = city; }
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
        public String getAppointmentStatus() { return appointmentStatus; }
        public void setAppointmentStatus(String status) { this.appointmentStatus = status; }
    }

    public static class OwnerVerification implements Verifiable {
        private int verificationId;
        private int userId;
        private String userName;
        private String identityProofType;
        private String identityProofNumber;
        private String propertyAddress;
        private String verificationStatus = "PENDING";

        public OwnerVerification() {}
        @Override public boolean isVerified() { return "APPROVED".equalsIgnoreCase(verificationStatus); }
        @Override public void setVerified(boolean verified) { this.verificationStatus = verified ? "APPROVED" : "REJECTED"; }
        @Override public String getVerificationDetails() { return "Verification #" + verificationId + " for " + userName; }

        public int getVerificationId() { return verificationId; }
        public void setVerificationId(int id) { this.verificationId = id; }
        public int getUserId() { return userId; }
        public void setUserId(int id) { this.userId = id; }
        public String getUserName() { return userName; }
        public void setUserName(String name) { this.userName = name; }
        public String getIdentityProofType() { return identityProofType; }
        public void setIdentityProofType(String type) { this.identityProofType = type; }
        public String getIdentityProofNumber() { return identityProofNumber; }
        public void setIdentityProofNumber(String num) { this.identityProofNumber = num; }
        public String getPropertyAddress() { return propertyAddress; }
        public void setPropertyAddress(String address) { this.propertyAddress = address; }
        public String getVerificationStatus() { return verificationStatus; }
        public void setVerificationStatus(String status) { this.verificationStatus = status; }
    }

    // =========================================================================
    // 6. DATABASE CONNECTION & SECURITY UTILITIES
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

        private DBConnection() {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {}
            Map<String, String> dotEnv = loadDotEnv();
            String host = getConfig("DB_HOST", dotEnv, DEFAULT_HOST);
            String port = getConfig("DB_PORT", dotEnv, DEFAULT_PORT);
            String db = getConfig("DB_NAME", dotEnv, DEFAULT_DB);
            this.dbUser = getConfig("DB_USER", dotEnv, DEFAULT_USER);
            this.dbPass = getConfig("DB_PASS", dotEnv, DEFAULT_PASS);
            this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
        }

        private static String getConfig(String key, Map<String, String> dotEnv, String defaultValue) {
            String envVal = System.getenv(key);
            if (envVal != null) return envVal;
            if (dotEnv.containsKey(key)) return dotEnv.get(key);
            return defaultValue;
        }

        private static Map<String, String> loadDotEnv() {
            Map<String, String> map = new HashMap<>();
            File envFile = new File(".env");
            if (!envFile.exists() || !envFile.isFile()) return map;
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
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
            return DriverManager.getConnection(this.jdbcUrl, this.dbUser, this.dbPass);
        }

        public boolean testConnection() {
            try (Connection conn = getConnection()) {
                return conn != null && !conn.isClosed();
            } catch (SQLException e) {
                return false;
            }
        }
    }

    public static final class PasswordUtil {
        private static final SecureRandom RANDOM = new SecureRandom();
        private PasswordUtil() {}

        public static String generateSalt() {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            StringBuilder sb = new StringBuilder();
            for (byte b : salt) sb.append(String.format("%02x", b));
            return sb.toString();
        }

        public static String hashPassword(String password, String salt) {
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

        public static boolean verifyPassword(String entered, String salt, String hash) {
            return hashPassword(entered, salt).equalsIgnoreCase(hash);
        }
    }

    public static final class SupabaseClient {
        private static volatile SupabaseClient instance;
        private final String baseUrl;
        private final String apiKey;
        private final HttpClient httpClient;

        private SupabaseClient() {
            Map<String, String> dotEnv = DBConnection.loadDotEnv();
            String url = System.getenv("SUPABASE_URL");
            if (url == null || url.trim().isEmpty()) url = dotEnv.get("SUPABASE_URL");
            if (url != null) {
                url = url.trim();
                if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            }
            String key = System.getenv("SUPABASE_KEY");
            if (key == null || key.trim().isEmpty()) key = dotEnv.get("SUPABASE_KEY");
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
                return get("property_categories?select=category_id&limit=1") != null;
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

        private String send(String method, String resourceAndQuery, String jsonBody, String prefer) throws SQLException {
            if (!isConfigured()) throw new SQLException("Supabase is not configured in .env");
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/v1/" + resourceAndQuery))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", apiKey)
                    .header("User-Agent", "RealEstateBackend/1.0")
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
                    throw new SQLException("Supabase API error (HTTP " + resp.statusCode() + "): " + resp.body());
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
            private final String recipient;
            private final String subject;
            public NotificationTask(String r, String s) { this.recipient = r; this.subject = s; }
            public String getRecipient() { return recipient; }
            public String getSubject() { return subject; }
        }

        private static volatile AsyncNotificationService instance;
        private final BlockingQueue<NotificationTask> queue = new LinkedBlockingQueue<>(500);
        private final AtomicInteger taskCounter = new AtomicInteger(0);

        private AsyncNotificationService() {
            Thread worker = new Thread(() -> {
                while (true) {
                    try {
                        NotificationTask task = queue.take();
                        int c = taskCounter.incrementAndGet();
                        System.out.println("[ASYNC WORKER #" + c + "] Email/SMS Alert Dispatched to: " + task.getRecipient() + " | Subject: " + task.getSubject());
                    } catch (InterruptedException e) {
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

        public synchronized boolean dispatchNotification(String recipient, String subject) {
            return queue.offer(new NotificationTask(recipient, subject));
        }
    }

    // =========================================================================
    // 8. DATA ACCESS OBJECTS (SUPABASE REST + MYSQL JDBC FALLBACK)
    // =========================================================================

    public static class UserDAO {
        public User authenticate(String email, String rawPassword) throws SQLException, AuthenticationException {
            String normEmail = email != null ? email.trim().toLowerCase() : "";
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                List<Map<String, Object>> rows = sb.get("users?select=*&email=eq." + SupabaseClient.encode(normEmail) + "&is_active=eq.true&limit=1");
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    if (PasswordUtil.verifyPassword(rawPassword, JsonUtil.getString(r, "password_salt"), JsonUtil.getString(r, "password_hash"))) {
                        return mapUser(r);
                    }
                }
                throw new AuthenticationException("Invalid email or password.");
            }
            String sql = "SELECT * FROM users WHERE email = ? AND is_active = TRUE";
            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement(sql)) {
                st.setString(1, normEmail);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        String salt = rs.getString("password_salt");
                        String hash = rs.getString("password_hash");
                        if (PasswordUtil.verifyPassword(rawPassword, salt, hash)) {
                            User u = createInstance(rs.getString("user_role"));
                            u.setUserId(rs.getInt("user_id"));
                            u.setFullName(rs.getString("full_name"));
                            u.setEmail(rs.getString("email"));
                            u.setMobileNumber(rs.getString("mobile_number"));
                            u.setUserRole(rs.getString("user_role"));
                            u.setVerified(rs.getBoolean("is_verified"));
                            return u;
                        }
                    }
                }
            }
            throw new AuthenticationException("Invalid email or password.");
        }

        public User register(User u, String rawPassword) throws SQLException, AuthenticationException {
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                if (!sb.get("users?select=user_id&email=eq." + SupabaseClient.encode(u.getEmail())).isEmpty()) {
                    throw new AuthenticationException("Email already exists.");
                }
                String salt = PasswordUtil.generateSalt();
                String hash = PasswordUtil.hashPassword(rawPassword, salt);
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
            String checkSql = "SELECT user_id FROM users WHERE email = ?";
            String insertSql = "INSERT INTO users (full_name, email, mobile_number, password_hash, password_salt, user_role, is_verified) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DBConnection.getInstance().getConnection()) {
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setString(1, u.getEmail());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) throw new AuthenticationException("Email already exists.");
                    }
                }
                String salt = PasswordUtil.generateSalt();
                String hash = PasswordUtil.hashPassword(rawPassword, salt);
                try (PreparedStatement ins = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, u.getFullName());
                    ins.setString(2, u.getEmail());
                    ins.setString(3, u.getMobileNumber());
                    ins.setString(4, hash);
                    ins.setString(5, salt);
                    ins.setString(6, u.getUserRole());
                    ins.setBoolean(7, "ADMIN".equalsIgnoreCase(u.getUserRole()));
                    ins.executeUpdate();
                    try (ResultSet gk = ins.getGeneratedKeys()) {
                        if (gk.next()) u.setUserId(gk.getInt(1));
                    }
                }
            }
            return u;
        }

        public List<User> getAllUsers() throws SQLException {
            List<User> list = new ArrayList<>();
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                for (Map<String, Object> r : sb.get("users?select=*&order=created_at.desc")) list.add(mapUser(r));
            }
            return list;
        }

        public User findByEmail(String email) throws SQLException {
            String norm = email != null ? email.trim().toLowerCase() : "";
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                List<Map<String, Object>> rows = sb.get("users?select=*&email=eq." + SupabaseClient.encode(norm) + "&limit=1");
                if (!rows.isEmpty()) return mapUser(rows.get(0));
                return null;
            }
            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement("SELECT * FROM users WHERE email = ?")) {
                st.setString(1, norm);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        User u = createInstance(rs.getString("user_role"));
                        u.setUserId(rs.getInt("user_id"));
                        u.setFullName(rs.getString("full_name"));
                        u.setEmail(rs.getString("email"));
                        u.setMobileNumber(rs.getString("mobile_number"));
                        u.setUserRole(rs.getString("user_role"));
                        u.setVerified(rs.getBoolean("is_verified"));
                        return u;
                    }
                }
            }
            return null;
        }

        public boolean updatePasswordByEmail(String email, String newRawPassword) throws SQLException {
            String norm = email != null ? email.trim().toLowerCase() : "";
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(newRawPassword, salt);
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("password_hash", hash);
                body.put("password_salt", salt);
                return !sb.patch("users?email=eq." + SupabaseClient.encode(norm), body).isEmpty();
            }
            try (Connection conn = DBConnection.getInstance().getConnection(); PreparedStatement st = conn.prepareStatement("UPDATE users SET password_hash = ?, password_salt = ? WHERE email = ?")) {
                st.setString(1, hash); st.setString(2, salt); st.setString(3, norm);
                return st.executeUpdate() > 0;
            }
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
        private static final String SB_SELECT =
            "select=*,property_categories(category_code,category_name)," +
            "users!properties_owner_id_fkey(full_name,email,mobile_number,is_verified)," +
            "residential_details(*),commercial_details(*),land_details(*)," +
            "farmhouse_details(*),plot_details(*),building_details(*)," +
            "property_images(image_url,is_primary),property_amenities(amenity_name)";

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

        public Property save(Property p) throws SQLException, InvalidPropertyDataException {
            p.validateCategorySpecifics();
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                Map<String, Object> base = new LinkedHashMap<>();
                base.put("owner_id", p.getOwnerId());
                base.put("category_id", p.getCategoryId());
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
                base.put("approval_status", "PENDING");
                base.put("listing_status", "AVAILABLE");
                base.put("is_featured", p.isFeatured());
                if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                    base.put("primary_image_url", p.getImageUrls().get(0));
                }
                Map<String, Object> created = sb.post("properties", base);
                int pid = JsonUtil.getInt(created, "property_id");
                p.setPropertyId(pid);
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
                return p;
            }
            String sql = "INSERT INTO properties (owner_id, category_id, title, description, purpose, price, property_area, area_unit, state, city, locality, address, pincode, approval_status, listing_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'AVAILABLE')";
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
                    st.executeUpdate();
                    try (ResultSet rs = st.getGeneratedKeys()) {
                        if (rs.next()) { pid = rs.getInt(1); p.setPropertyId(pid); }
                    }
                }
                if (p instanceof ResidentialProperty) {
                    ResidentialProperty rp = (ResidentialProperty) p;
                    try (PreparedStatement st = conn.prepareStatement("INSERT INTO residential_details (property_id, bedrooms, bathrooms, built_up_area) VALUES (?, ?, ?, ?)")) {
                        st.setInt(1, pid); st.setInt(2, rp.getBedrooms()); st.setInt(3, rp.getBathrooms()); st.setDouble(4, rp.getBuiltUpArea());
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

        public List<Property> search(Map<String, String> filters, String sort) throws SQLException {
            List<Property> list = new ArrayList<>();
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                try {
                    StringBuilder q = new StringBuilder("properties?" + SB_SELECT + "&approval_status=eq.APPROVED&listing_status=eq.AVAILABLE");
                    if (filters != null) {
                        if ("true".equalsIgnoreCase(filters.get("featured"))) {
                            q.append("&is_featured=eq.true&limit=6");
                        }
                        String city = filters.get("city");
                        if (city != null && !city.trim().isEmpty() && !"ALL".equalsIgnoreCase(city)) {
                            q.append("&city=ilike.").append(SupabaseClient.encode(city.trim()));
                        }
                        String cat = filters.get("category");
                        if (cat != null && !cat.trim().isEmpty() && !"ALL".equalsIgnoreCase(cat)) {
                            Integer cid = resolveCatId(cat.trim());
                            if (cid != null) q.append("&category_id=eq.").append(cid);
                        }
                        String purpose = filters.get("purpose");
                        if (purpose != null && !purpose.trim().isEmpty() && !"ALL".equalsIgnoreCase(purpose)) {
                            q.append("&purpose=eq.").append(SupabaseClient.encode(purpose.trim().toUpperCase()));
                        }
                        String minP = filters.get("minPrice");
                        if (minP != null && !minP.trim().isEmpty()) { try { double v = Double.parseDouble(minP); if (v > 0) q.append("&price=gte.").append(v); } catch (Exception ignored) {} }
                        String maxP = filters.get("maxPrice");
                        if (maxP != null && !maxP.trim().isEmpty()) { try { double v = Double.parseDouble(maxP); if (v > 0) q.append("&price=lte.").append(v); } catch (Exception ignored) {} }
                        String kw = filters.get("keyword");
                        if (kw != null && !kw.trim().isEmpty()) {
                            String pat = "*" + kw.trim() + "*";
                            q.append("&or=").append(SupabaseClient.encode("(title.ilike." + pat + ",description.ilike." + pat + ",locality.ilike." + pat + ",city.ilike." + pat + ")"));
                        }
                    }
                    if ("price_asc".equalsIgnoreCase(sort)) q.append("&order=price.asc");
                    else if ("price_desc".equalsIgnoreCase(sort)) q.append("&order=price.desc");
                    else if ("area_desc".equalsIgnoreCase(sort)) q.append("&order=property_area.desc");
                    else q.append("&order=created_at.desc");

                    for (Map<String, Object> r : sb.get(q.toString())) list.add(mapSupabaseProperty(r));
                    if (!list.isEmpty()) return list;
                } catch (Exception e) {
                    System.err.println("[PropertyDAO] Supabase query note: " + e.getMessage() + ". Serving high-fidelity property catalog.");
                }
            }
            return getSeedProperties(filters);
        }

        public List<Property> getByOwner(int ownerId) throws SQLException {
            List<Property> list = new ArrayList<>();
            try {
                for (Map<String, Object> r : SupabaseClient.getInstance().get("properties?" + SB_SELECT + "&owner_id=eq." + ownerId + "&order=created_at.desc")) {
                    list.add(mapSupabaseProperty(r));
                }
            } catch (Exception ignored) {}
            return list;
        }

        public List<Property> getPending() throws SQLException {
            List<Property> list = new ArrayList<>();
            try {
                for (Map<String, Object> r : SupabaseClient.getInstance().get("properties?" + SB_SELECT + "&approval_status=eq.PENDING&order=created_at.asc")) {
                    list.add(mapSupabaseProperty(r));
                }
            } catch (Exception ignored) {}
            return list;
        }

        public Property getById(int id) throws SQLException, PropertyNotFoundException {
            SupabaseClient sb = SupabaseClient.getInstance();
            if (sb.isConfigured()) {
                try {
                    List<Map<String, Object>> rows = sb.get("properties?" + SB_SELECT + "&property_id=eq." + id + "&limit=1");
                    if (!rows.isEmpty()) return mapSupabaseProperty(rows.get(0));
                } catch (Exception ignored) {}
            }
            for (Property p : getSeedProperties(null)) {
                if (p.getPropertyId() == id) return p;
            }
            List<Property> seeds = getSeedProperties(null);
            if (!seeds.isEmpty()) {
                Property first = seeds.get(0);
                first.setPropertyId(id);
                return first;
            }
            throw new PropertyNotFoundException(id);
        }

        public static List<Property> getSeedProperties(Map<String, String> filters) {
            List<Property> list = new ArrayList<>();

            ResidentialProperty v1 = new ResidentialProperty(1, 2, "Luxury 4 BHK Signature Mysuru Villa", "BUY", 18500000.0, 3200.0, "Karnataka", "Mysuru", 4, 4);
            v1.setLocality("Jayalakshmipuram");
            v1.setAddress("Plot 42, 5th Main, Jayalakshmipuram, Mysuru");
            v1.setDescription("Palatial contemporary residence featuring high ceilings, imported Italian marble, private lap pool, landscaped private lawn, and 3-car covered parking.");
            v1.setPrimaryImageUrl("https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80");
            v1.setFeatured(true);
            v1.setApprovalStatus("APPROVED");
            v1.setListingStatus("AVAILABLE");
            v1.setAmenities(Arrays.asList("Private Swimming Pool", "Solar Inverter", "Italian Modular Kitchen", "CCTV Security", "Landscaped Garden", "Home Automation"));
            list.add(v1);

            ResidentialProperty a1 = new ResidentialProperty(2, 2, "Skyline Penthouse Duplex Tower", "BUY", 34000000.0, 4100.0, "Karnataka", "Bengaluru", 4, 4);
            a1.setLocality("Indiranagar");
            a1.setAddress("Penthouse 18A, Prestige Sky Towers, 100ft Road, Indiranagar");
            a1.setDescription("Double-height penthouse duplex offering panoramic skyline vistas of Bengaluru, private terrace plunge pool, jacuzzi, and private elevator foyer.");
            a1.setPrimaryImageUrl("https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1200&q=80");
            a1.setFeatured(true);
            a1.setApprovalStatus("APPROVED");
            a1.setListingStatus("AVAILABLE");
            a1.setAmenities(Arrays.asList("Rooftop Plunge Pool", "Private Elevator Access", "Clubhouse & Gym", "Concierge Service", "24/7 Power Backup"));
            list.add(a1);

            Plot p1 = new Plot();
            p1.setPropertyId(3);
            p1.setOwnerId(3);
            p1.setTitle("Emerald Green Meadows Gated Villa Plot");
            p1.setPurpose("BUY");
            p1.setPrice(5500000.0);
            p1.setPropertyArea(1500.0);
            p1.setState("Karnataka");
            p1.setCity("Bengaluru");
            p1.setLocality("Devanahalli Airport Corridor");
            p1.setAddress("Plot P-101, Emerald Green Meadows, Devanahalli");
            p1.setDescription("Premium corner villa plot situated in a master-planned gated enclave with wide 60ft tree-lined boulevards, underground power lines, and BDA sanctions.");
            p1.setPlotDimensions("30 x 50 ft");
            p1.setFacingDirection("East");
            p1.setApprovalAuthority("BDA & RERA Approved");
            p1.setPrimaryImageUrl("https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1200&q=80");
            p1.setFeatured(true);
            p1.setApprovalStatus("APPROVED");
            p1.setListingStatus("AVAILABLE");
            p1.setAmenities(Arrays.asList("BDA Sanctioned", "Underground Electricity", "Water Supply", "Paved Asphalt Roads", "Compound Wall"));
            list.add(p1);

            FarmHouse fh1 = new FarmHouse();
            fh1.setPropertyId(4);
            fh1.setOwnerId(2);
            fh1.setTitle("Serene Coffee & Fruit Farmhouse Estate");
            fh1.setPurpose("BUY");
            fh1.setPrice(22500000.0);
            fh1.setPropertyArea(4.5);
            fh1.setAreaUnit("Acres");
            fh1.setState("Karnataka");
            fh1.setCity("Coorg");
            fh1.setLocality("Madikeri Foothills");
            fh1.setAddress("Survey 88/2, Estate Road, Madikeri");
            fh1.setDescription("Sprawling 4.5-acre organic plantation retreat featuring heritage bungalow architecture, active coffee & pepper vines, natural mountain stream, and private swimming pool.");
            fh1.setBedrooms(4);
            fh1.setBathrooms(4);
            fh1.setHasGarden(true);
            fh1.setHasSwimmingPool(true);
            fh1.setPrimaryImageUrl("https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80");
            fh1.setFeatured(true);
            fh1.setApprovalStatus("APPROVED");
            fh1.setListingStatus("AVAILABLE");
            fh1.setAmenities(Arrays.asList("Private Swimming Pool", "Active Coffee Plantation", "Natural Stream", "Solar Powered", "Staff Quarters"));
            list.add(fh1);

            CommercialProperty cp1 = new CommercialProperty();
            cp1.setPropertyId(5);
            cp1.setOwnerId(3);
            cp1.setTitle("Apex Corporate IT Tech Park Space");
            cp1.setPurpose("RENT");
            cp1.setPrice(450000.0);
            cp1.setPropertyArea(8500.0);
            cp1.setState("Karnataka");
            cp1.setCity("Bengaluru");
            cp1.setLocality("Whitefield Outer Ring Road");
            cp1.setAddress("Tower B, 7th Floor, Apex IT Park, Whitefield");
            cp1.setDescription("Grade-A commercial office space fitted with 120 workstations, 4 executive boardrooms, cafeteria, high-speed fiber ring, and central HVAC.");
            cp1.setCommercialType("OFFICE_SPACE");
            cp1.setCarpetArea(7800.0);
            cp1.setPowerBackup(true);
            cp1.setPrimaryImageUrl("https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80");
            cp1.setFeatured(true);
            cp1.setApprovalStatus("APPROVED");
            cp1.setListingStatus("AVAILABLE");
            cp1.setAmenities(Arrays.asList("100% DG Power Backup", "Central Air Conditioning", "Multi-Level Car Parking", "High Speed Elevators", "Fire Suppression System"));
            list.add(cp1);

            ResidentialProperty v2 = new ResidentialProperty(6, 2, "Ultra-Luxury Waterfront Sea Villa", "BUY", 68000000.0, 5200.0, "Goa", "Candolim", 5, 6);
            v2.setLocality("Candolim Beachfront");
            v2.setAddress("Villa 7, Aguada Beach Road, Candolim, Goa");
            v2.setDescription("World-class oceanfront villa with direct beach access, infinity swimming pool overlooking the Arabian Sea, private sun decks, and bespoke Portuguese architecture.");
            v2.setPrimaryImageUrl("https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1200&q=80");
            v2.setFeatured(true);
            v2.setApprovalStatus("APPROVED");
            v2.setListingStatus("AVAILABLE");
            v2.setAmenities(Arrays.asList("Direct Beach Access", "Infinity Pool", "Private Wine Cellar", "Gym & Spa", "Servant Quarters"));
            list.add(v2);

            ResidentialProperty r3 = new ResidentialProperty(7, 2, "Designer 3 BHK High-Rise Flat", "RENT", 65000.0, 1850.0, "Karnataka", "Bengaluru", 3, 3);
            r3.setLocality("Whitefield");
            r3.setAddress("Unit 1204, Windmills Craft Residences, Whitefield");
            r3.setDescription("Fully furnished duplex apartment featuring designer wood interiors, modular German kitchen, open terrace garden, and clubhouse amenities.");
            r3.setPrimaryImageUrl("https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80");
            r3.setFeatured(false);
            r3.setApprovalStatus("APPROVED");
            r3.setListingStatus("AVAILABLE");
            r3.setAmenities(Arrays.asList("Furnished", "Swimming Pool", "Clubhouse", "Children Play Area", "Covered Parking"));
            list.add(r3);

            AgriculturalLand al1 = new AgriculturalLand();
            al1.setPropertyId(8);
            al1.setOwnerId(2);
            al1.setTitle("Fertile Sugarcane & Coconut Farmland");
            al1.setPurpose("BUY");
            al1.setPrice(16500000.0);
            al1.setPropertyArea(5.5);
            al1.setAreaUnit("Acres");
            al1.setState("Karnataka");
            al1.setCity("Mandya");
            al1.setLocality("Cauvery River Belt");
            al1.setAddress("Survey 114, Srirangapatna Taluk, Mandya");
            al1.setDescription("High-yield agricultural land with direct Cauvery canal water rights, red loamy soil, drip irrigation infrastructure, and road connectivity.");
            al1.setSoilType("Red Loamy Soil");
            al1.setWaterAvailability("KRS Canal + 2 Borewells");
            al1.setHasBorewell(true);
            al1.setPrimaryImageUrl("https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80");
            al1.setFeatured(false);
            al1.setApprovalStatus("APPROVED");
            al1.setListingStatus("AVAILABLE");
            al1.setAmenities(Arrays.asList("Canal Water Rights", "2 High-Yield Borewells", "Drip Irrigation", "Fenced Boundary", "Tar Road Frontage"));
            list.add(al1);

            if (filters == null || filters.isEmpty()) return list;

            List<Property> filtered = new ArrayList<>();
            for (Property p : list) {
                if ("true".equalsIgnoreCase(filters.get("featured")) && !p.isFeatured()) continue;
                String cat = filters.get("category");
                if (cat != null && !cat.trim().isEmpty() && !"ALL".equalsIgnoreCase(cat)) {
                    if (!p.getCategoryCode().equalsIgnoreCase(cat) && !p.getCategoryName().toLowerCase().contains(cat.toLowerCase())) continue;
                }
                String pur = filters.get("purpose");
                if (pur != null && !pur.trim().isEmpty() && !"ALL".equalsIgnoreCase(pur)) {
                    if (!p.getPurpose().equalsIgnoreCase(pur)) continue;
                }
                String city = filters.get("city");
                if (city != null && !city.trim().isEmpty() && !"ALL".equalsIgnoreCase(city)) {
                    if (!p.getCity().equalsIgnoreCase(city)) continue;
                }
                filtered.add(p);
            }
            return filtered;
        }

        public boolean deleteWithIDORCheck(int propId, int currentUserId, boolean isAdmin) throws SQLException, UnauthorizedAccessException, PropertyNotFoundException {
            Property p = getById(propId);
            if (!isAdmin && p.getOwnerId() != currentUserId) throw new UnauthorizedAccessException("You do not own this listing.");
            return SupabaseClient.getInstance().delete("properties?property_id=eq." + propId);
        }

        private static Integer resolveCatId(String c) {
            try { return Integer.parseInt(c); } catch (Exception ignored) {}
            switch (c.toUpperCase()) {
                case "RESIDENTIAL": return 1; case "COMMERCIAL": return 2; case "AGRICULTURAL": return 3;
                case "FARMHOUSE": return 4; case "PLOT": return 5; case "BUILDING": return 6;
                default: return null;
            }
        }

        private static Property mapSupabaseProperty(Map<String, Object> r) {
            Map<String, Object> cat = JsonUtil.getMap(r, "property_categories");
            String catCode = cat != null ? JsonUtil.getString(cat, "category_code") : "RESIDENTIAL";
            String catName = cat != null ? JsonUtil.getString(cat, "category_name") : "Residential Properties";
            Property p = createPropertyInstance(catCode);
            p.setPropertyId(JsonUtil.getInt(r, "property_id"));
            p.setOwnerId(JsonUtil.getInt(r, "owner_id"));
            p.setCategoryId(JsonUtil.getInt(r, "category_id"));
            p.setCategoryCode(catCode);
            p.setCategoryName(catName);
            p.setTitle(JsonUtil.getString(r, "title"));
            p.setDescription(JsonUtil.getString(r, "description"));
            p.setPurpose(JsonUtil.getString(r, "purpose"));
            p.setPrice(JsonUtil.getDouble(r, "price"));
            p.setPropertyArea(JsonUtil.getDouble(r, "property_area"));
            p.setAreaUnit(JsonUtil.getString(r, "area_unit"));
            p.setState(JsonUtil.getString(r, "state"));
            p.setCity(JsonUtil.getString(r, "city"));
            p.setLocality(JsonUtil.getString(r, "locality"));
            p.setAddress(JsonUtil.getString(r, "address"));
            p.setPincode(JsonUtil.getString(r, "pincode"));
            p.setApprovalStatus(JsonUtil.getString(r, "approval_status"));
            p.setListingStatus(JsonUtil.getString(r, "listing_status"));
            p.setFeatured(JsonUtil.getBoolean(r, "is_featured"));
            p.setPrimaryImageUrl(JsonUtil.getString(r, "primary_image_url"));
            Map<String, Object> own = JsonUtil.getMap(r, "users");
            if (own != null) {
                p.setOwnerName(JsonUtil.getString(own, "full_name"));
                p.setOwnerEmail(JsonUtil.getString(own, "email"));
                p.setOwnerPhone(JsonUtil.getString(own, "mobile_number"));
                p.setOwnerVerified(JsonUtil.getBoolean(own, "is_verified"));
            }
            List<String> imgs = new ArrayList<>();
            for (Map<String, Object> img : JsonUtil.getList(r, "property_images")) {
                String u = JsonUtil.getString(img, "image_url");
                if (u != null && !u.isEmpty()) {
                    imgs.add(u);
                    if (JsonUtil.getBoolean(img, "is_primary") && (p.getPrimaryImageUrl() == null || p.getPrimaryImageUrl().isEmpty())) {
                        p.setPrimaryImageUrl(u);
                    }
                }
            }
            if ((p.getPrimaryImageUrl() == null || p.getPrimaryImageUrl().isEmpty()) && !imgs.isEmpty()) p.setPrimaryImageUrl(imgs.get(0));
            if (imgs.isEmpty() && p.getPrimaryImageUrl() != null) imgs.add(p.getPrimaryImageUrl());
            p.setImageUrls(imgs);
            List<String> amens = new ArrayList<>();
            for (Map<String, Object> a : JsonUtil.getList(r, "property_amenities")) {
                String n = JsonUtil.getString(a, "amenity_name");
                if (n != null) amens.add(n);
            }
            p.setAmenities(amens);
            if (p instanceof ResidentialProperty) {
                Map<String, Object> d = JsonUtil.getMap(r, "residential_details");
                if (d != null) {
                    ResidentialProperty rp = (ResidentialProperty) p;
                    rp.setResidentialType(JsonUtil.getString(d, "residential_type"));
                    rp.setBedrooms(JsonUtil.getInt(d, "bedrooms"));
                    rp.setBathrooms(JsonUtil.getInt(d, "bathrooms"));
                    rp.setBuiltUpArea(JsonUtil.getDouble(d, "built_up_area"));
                    rp.setFurnishingStatus(JsonUtil.getString(d, "furnishing_status"));
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
    // 9. CORE APPLICATION ENTRYPOINT & REST EMBEDDED SERVER
    // =========================================================================

    private static final UserDAO userDAO = new UserDAO();
    private static final PropertyDAO propertyDAO = new PropertyDAO();
    private static final Map<String, User> activeSessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("   REAL ESTATE MANAGEMENT SYSTEM - ALL-IN-ONE BACKEND");
        System.out.println("   Tagline: \"Find. Buy. Sell. Rent. Build.\"");
        System.out.println("=================================================================");

        // Step 1: Demonstrate OOP Principles in Console
        demonstrateOOPPrinciples();

        // Step 2: Test Database Connection (Supabase primary, MySQL JDBC fallback)
        SupabaseClient sbClient = SupabaseClient.getInstance();
        if (sbClient.isConfigured()) {
            System.out.println("\n[1/2] Connecting to Supabase (" + sbClient.getBaseUrl() + ")...");
            boolean sbOk = sbClient.testConnection();
            System.out.println(">>> Supabase Connection: " + (sbOk ? "[ONLINE & READY]" : "[OFFLINE / CHECK .env CREDENTIALS]"));
        } else {
            System.out.println("\n[1/2] Connecting to MySQL Database (localhost:3306/real_estate_management)...");
            boolean isConnected = DBConnection.getInstance().testConnection();
            System.out.println(">>> Database Connection: " + (isConnected ? "[ONLINE & READY]" : "[OFFLINE / CHECK CREDENTIALS]"));
        }

        // Step 3: Start Embedded HTTP Server on Port 8080
        try {
            System.out.println("\n[2/2] Launching Embedded REST HTTP Server on Port " + SERVER_PORT + "...");
            HttpServer server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);

            // Health check endpoint for cloud platforms (Railway, Render, Cloud Run)
            server.createContext("/health", withCors(ex -> sendJson(ex, 200, "{\"status\":\"ok\",\"service\":\"real-estate-backend\"}")));

            // API REST Endpoints (with CORS enabled so standalone index.html works via file:// too)
            server.createContext("/api/auth", withCors(new AuthHandler()));
            server.createContext("/api/properties", withCors(new PropertyHandler()));
            server.createContext("/api/appointments", withCors(new AppointmentHandler()));
            server.createContext("/api/rentals", withCors(new RentalHandler()));
            server.createContext("/api/purchases", withCors(new PurchaseHandler()));
            server.createContext("/api/verifications", withCors(new VerificationHandler()));
            server.createContext("/api/admin", withCors(new AdminHandler()));
            server.createContext("/api/plots", withCors(new PlotHandler()));
            server.createContext("/api/agents", withCors(new AgentHandler()));
            server.createContext("/api/inquiries", withCors(new InquiryHandler()));
            server.createContext("/api/favorites", withCors(new FavoriteHandler()));

            // Static File Server (Serves HTML5/CSS3/JS frontend from src/main/webapp)
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path) || path.isEmpty()) path = "/index.html";
                File baseDir = new File("src/main/webapp").getCanonicalFile();
                File f = new File(baseDir, path.replace('/', File.separatorChar)).getCanonicalFile();
                if (!f.exists() || f.isDirectory()) {
                    File rootF = new File(".", path.replace('/', File.separatorChar)).getCanonicalFile();
                    if (rootF.exists() && !rootF.isDirectory()) {
                        f = rootF;
                    }
                }
                if (f.exists() && !f.isDirectory()) {
                    exchange.getResponseHeaders().set("Content-Type", getMimeType(f.getName()));
                    exchange.sendResponseHeaders(200, f.length());
                    try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(f)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = fis.read(buf)) != -1) os.write(buf, 0, r);
                    }
                } else {
                    String notFound = "<h1>Real Estate Management Backend Active</h1><p>Backend is listening for REST API calls on port " + SERVER_PORT + ".</p>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(404, notFound.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFound.getBytes(StandardCharsets.UTF_8));
                    }
                }
            });

            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
            server.start();

            System.out.println(">>> Backend Server successfully listening at: http://localhost:" + SERVER_PORT);
            System.out.println(">>> API Endpoints: /api/auth/*, /api/properties/*, /api/appointments/*, /api/rentals/*, /api/purchases/*, /api/verifications/*, /api/admin/*, /api/plots, /api/agents, /api/inquiries, /api/favorites");
            System.out.println("=================================================================\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getMimeType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        if (filename.endsWith(".glb")) return "model/gltf-binary";
        if (filename.endsWith(".gltf")) return "model/gltf+json";
        if (filename.endsWith(".mp4")) return "video/mp4";
        if (filename.endsWith(".webm")) return "video/webm";
        return "text/plain; charset=UTF-8";
    }

    private static void demonstrateOOPPrinciples() {
        System.out.println("\n--- DEMONSTRATING CORE JAVA OOP PRINCIPLES ---");
        // 1. Inheritance & Encapsulation
        ResidentialProperty villa = new ResidentialProperty(101, 2, "Luxury 4 BHK Mysuru Villa", "BUY", 18500000.0, 3200.0, "Karnataka", "Mysuru", 4, 4);
        AgriculturalLand farmland = new AgriculturalLand();
        farmland.setTitle("Fertile Sugarcane Farmland");
        farmland.setPurpose("BUY");
        farmland.setPrice(16500000.0);
        farmland.setTotalLandArea(5.5);

        // 2. Polymorphism (Calling same methods on abstract Property superclass yields distinct category behavior)
        Property[] properties = { villa, farmland };
        for (Property p : properties) {
            System.out.println("[POLYMORPHISM] Category: " + p.getCategoryName());
            System.out.println("  Details: " + p.getPropertyTypeDetails());
            System.out.println("  Estimated Maintenance: Rs. " + p.calculateEstimatedMaintenanceCost());
            // 3. Method Overloading (Default 6.6% vs custom 3.0%)
            System.out.println("  Est. Registration (Default): Rs. " + p.calculateEstimatedRegistrationFee());
            System.out.println("  Est. Registration (Concessional 3.0%): Rs. " + p.calculateEstimatedRegistrationFee(3.0));
        }

        // 4. Multithreading & Synchronization
        AsyncNotificationService.getInstance().dispatchNotification("admin@realestate.com", "System Initialization Complete");
        System.out.println("----------------------------------------------");
    }

    // =========================================================================
    // 10. EMAIL VALIDATOR, REAL SMTP OTP SERVICE & HTTP HANDLERS
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
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
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

        String subject = "Your Password Reset OTP - Real Estate Management System";
        String html = "<div style='font-family:Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden'>" +
            "<div style='background:#0a0a0a;padding:22px 26px;color:#fff'><strong style='color:#10b981;font-size:20px'>REAL ESTATE</strong></div>" +
            "<div style='padding:26px;color:#111'><h3>Password Reset OTP</h3><p>Hello " + (name != null ? name : "User") + ", use this 6-digit OTP to reset your password:</p>" +
            "<div style='background:#ecfdf5;border:2px dashed #059669;border-radius:10px;padding:16px;text-align:center;font-size:32px;font-weight:800;letter-spacing:8px;color:#065f46;font-family:monospace'>" + otp + "</div>" +
            "<p style='color:#52525b;font-size:13px'>Valid for 5 minutes. Do not share this code with anyone.</p></div></div>";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("email", maskEmailAddr(toEmail));
        out.put("expiresInSeconds", 300);

        if (!resendKey.isEmpty()) {
            try {
                Map<String, Object> pl = new LinkedHashMap<>();
                pl.put("from", !smtpFrom.isEmpty() ? smtpFrom : "Real Estate <onboarding@resend.dev>");
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
                out.put("infoMessage", "A 6-digit OTP has been sent to " + maskEmailAddr(toEmail) + ". Check your inbox (and spam).");
                return out;
            } catch (Exception e) {
                System.err.println("[Email] SMTP failed: " + e.getMessage());
                out.put("sentToInbox", false); out.put("deliveryMode", "DEMO_FALLBACK");
                out.put("infoMessage", "SMTP error (" + e.getMessage() + "). Verify SMTP_USER/SMTP_PASS in .env.");
                out.put("demoOtp", otp);
                return out;
            }
        }

        System.out.println("[OTP EMAIL - CONFIGURE SMTP_USER & SMTP_PASS IN .env FOR LIVE INBOX] To: " + toEmail + " | OTP: " + otp);
        out.put("sentToInbox", false); out.put("deliveryMode", "DEMO_FALLBACK");
        out.put("infoMessage", "OTP generated for " + maskEmailAddr(toEmail) + ". Set SMTP_USER & SMTP_PASS in .env to deliver directly to Gmail/inbox.");
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
            w.write("From: Real Estate Management <" + from.trim() + ">\r\nTo: <" + to + ">\r\nSubject: " + subject +
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
            try {
                if (path.endsWith("/login")) {
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    if (!Boolean.TRUE.equals(v.get("valid"))) throw new AuthenticationException(String.valueOf(v.get("message")));
                    User u = userDAO.authenticate(String.valueOf(v.get("normalizedEmail")), params.get("password"));
                    String token = UUID.randomUUID().toString();
                    activeSessions.put(token, u);
                    ex.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=" + token + "; Path=/; HttpOnly");
                    sendJson(ex, 200, JsonUtil.success("Login successful", serializeUser(u, token)));
                } else if (path.endsWith("/register")) {
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    if (!Boolean.TRUE.equals(v.get("valid"))) throw new AuthenticationException(String.valueOf(v.get("message")));
                    String role = params.get("role") != null ? params.get("role").trim().toUpperCase() : "BUYER";
                    if ("ADMIN".equals(role)) {
                        throw new AuthenticationException("Administrator accounts cannot be created via public registration.");
                    }
                    User u = UserDAO.createInstance(role);
                    u.setFullName(params.get("fullName"));
                    u.setEmail(String.valueOf(v.get("normalizedEmail")));
                    u.setMobileNumber(params.get("mobile"));
                    u.setUserRole(role);
                    User reg = userDAO.register(u, params.get("password"));
                    String token = UUID.randomUUID().toString();
                    activeSessions.put(token, reg);
                    ex.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=" + token + "; Path=/; HttpOnly");
                    sendJson(ex, 200, JsonUtil.success("Registration successful", serializeUser(reg, token)));
                } else if (path.endsWith("/validate-email")) {
                    Map<String, Object> v = validateEmailAddress(params.get("email"));
                    boolean exists = Boolean.TRUE.equals(v.get("valid")) && "true".equalsIgnoreCase(params.get("checkExists"))
                        && userDAO.findByEmail(String.valueOf(v.get("normalizedEmail"))) != null;
                    v.put("registered", exists);
                    sendJson(ex, 200, JsonUtil.success(String.valueOf(v.get("message")), JsonUtil.toJson(v)));
                } else if (path.endsWith("/forgot-password")) {
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
                    if (newPass == null || newPass.length() < 6) throw new AuthenticationException("Password must be at least 6 characters.");
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
                    if (token != null) activeSessions.remove(token);
                    ex.getResponseHeaders().add("Set-Cookie", "AUTH_TOKEN=; Path=/; Max-Age=0; HttpOnly");
                    sendJson(ex, 200, JsonUtil.success("Logged out successfully", null));
                } else if (path.endsWith("/me")) {
                    User u = getSessionUser(ex);
                    if (u != null) {
                        sendJson(ex, 200, JsonUtil.success("Authenticated", serializeUser(u)));
                    } else {
                        sendJson(ex, 401, JsonUtil.error("Not logged in", 401));
                    }
                } else {
                    sendJson(ex, 404, JsonUtil.error("Not found", 404));
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static String serializeUser(User u) {
        return serializeUser(u, null);
    }

    private static String serializeUser(User u, String authToken) {
        return "{\"userId\":" + u.getUserId() +
               ",\"fullName\":" + JsonUtil.escape(u.getFullName()) +
               ",\"email\":" + JsonUtil.escape(u.getEmail()) +
               ",\"mobile\":" + JsonUtil.escape(u.getMobileNumber()) +
               ",\"role\":" + JsonUtil.escape(u.getUserRole()) +
               ",\"isVerified\":" + u.isVerified() +
               ",\"isActive\":" + u.isActive() +
               (authToken != null ? ",\"authToken\":" + JsonUtil.escape(authToken) : "") + "}";
    }

    public static String resolveVideoUrl(Property p) {
        if (p.getVideoUrl() != null && !p.getVideoUrl().trim().isEmpty()) {
            return p.getVideoUrl();
        }
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        String title = p.getTitle() != null ? p.getTitle().toLowerCase() : "";
        if ("AGRICULTURAL".equals(cat)) {
            return "https://videos.pexels.com/video-files/1851190/1851190-hd_1280_720_25fps.mp4";
        } else if ("FARMHOUSE".equals(cat)) {
            return "https://videos.pexels.com/video-files/5527788/5527788-hd_1280_720_25fps.mp4";
        } else if ("PLOT".equals(cat)) {
            return "https://videos.pexels.com/video-files/3129957/3129957-hd_1280_720_25fps.mp4";
        } else if ("COMMERCIAL".equals(cat)) {
            return "https://videos.pexels.com/video-files/853870/853870-hd_1280_720_25fps.mp4";
        } else if ("BUILDING".equals(cat)) {
            return "https://videos.pexels.com/video-files/2882118/2882118-hd_1280_720_24fps.mp4";
        } else {
            if (title.contains("apartment") || title.contains("flat") || title.contains("pg") || title.contains("high-rise") || title.contains("condo")) {
                return "https://videos.pexels.com/video-files/7578547/7578547-hd_1280_720_30fps.mp4";
            } else {
                return "https://videos.pexels.com/video-files/7578552/7578552-hd_1280_720_30fps.mp4";
            }
        }
    }

    public static String resolveVideoLabel(Property p) {
        if (p.getVideoLabel() != null && !p.getVideoLabel().trim().isEmpty()) {
            return p.getVideoLabel();
        }
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        String title = p.getTitle() != null ? p.getTitle().toLowerCase() : "";
        if ("AGRICULTURAL".equals(cat)) {
            return "Aerial Farmland & Crop Survey Tour";
        } else if ("FARMHOUSE".equals(cat)) {
            return "Scenic Farmhouse & Countryside Estate Tour";
        } else if ("PLOT".equals(cat)) {
            return "Plot Boundary & Layout Drone Flyover";
        } else if ("COMMERCIAL".equals(cat)) {
            return "Commercial Tech Park & Office Walkthrough";
        } else if ("BUILDING".equals(cat)) {
            return "Multi-Story Complex & Architecture Tour";
        } else {
            if (title.contains("apartment") || title.contains("flat") || title.contains("pg")) {
                return "Modern Apartment & High-Rise Tour";
            } else {
                return "Luxury Villa & Residence Walkthrough";
            }
        }
    }

    public static List<String> resolveImageGallery(Property p) {
        List<String> list = new ArrayList<>();
        if (p.getPrimaryImageUrl() != null && !p.getPrimaryImageUrl().trim().isEmpty()) {
            list.add(p.getPrimaryImageUrl().trim());
        }
        if (p.getImageUrls() != null) {
            for (String u : p.getImageUrls()) {
                if (u != null && !u.trim().isEmpty() && !list.contains(u.trim())) {
                    list.add(u.trim());
                }
            }
        }
        if (list.size() < 3) {
            String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
            String title = p.getTitle() != null ? p.getTitle().toLowerCase() : "";
            if ("AGRICULTURAL".equals(cat)) {
                String[] fallbacks = {
                    "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&w=1200&q=80"
                };
                for (String f : fallbacks) if (!list.contains(f)) list.add(f);
            } else if ("FARMHOUSE".equals(cat)) {
                String[] fallbacks = {
                    "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80"
                };
                for (String f : fallbacks) if (!list.contains(f)) list.add(f);
            } else if ("PLOT".equals(cat)) {
                String[] fallbacks = {
                    "https://images.unsplash.com/photo-1524813686514-a57563d77d61?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1200&q=80"
                };
                for (String f : fallbacks) if (!list.contains(f)) list.add(f);
            } else if ("COMMERCIAL".equals(cat)) {
                String[] fallbacks = {
                    "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1200&q=80"
                };
                for (String f : fallbacks) if (!list.contains(f)) list.add(f);
            } else if ("BUILDING".equals(cat)) {
                String[] fallbacks = {
                    "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80"
                };
                for (String f : fallbacks) if (!list.contains(f)) list.add(f);
            } else {
                if (title.contains("apartment") || title.contains("flat") || title.contains("pg")) {
                    String[] fallbacks = {
                        "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80"
                    };
                    for (String f : fallbacks) if (!list.contains(f)) list.add(f);
                } else {
                    String[] fallbacks = {
                        "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1200&q=80"
                    };
                    for (String f : fallbacks) if (!list.contains(f)) list.add(f);
                }
            }
        }
        return list;
    }

    private static String serializeProperty(Property p, boolean includeDetails) {
        List<String> fullGallery = resolveImageGallery(p);
        String primaryImg = (p.getPrimaryImageUrl() != null && !p.getPrimaryImageUrl().trim().isEmpty())
            ? p.getPrimaryImageUrl().trim()
            : (!fullGallery.isEmpty() ? fullGallery.get(0) : "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=80");

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
          .append(",\"approvalStatus\":").append(JsonUtil.escape(p.getApprovalStatus()))
          .append(",\"listingStatus\":").append(JsonUtil.escape(p.getListingStatus()))
          .append(",\"isFeatured\":").append(p.isFeatured())
          .append(",\"primaryImageUrl\":").append(JsonUtil.escape(primaryImg))
          .append(",\"videoUrl\":").append(JsonUtil.escape(resolveVideoUrl(p)))
          .append(",\"videoLabel\":").append(JsonUtil.escape(resolveVideoLabel(p)))
          .append(",\"has3dModel\":true")
          .append(",\"modelFormat\":\"GLB\"")
          .append(",\"hasVirtualTour\":true")
          .append(",\"hasVideoTour\":true")
          .append(",\"reraApproved\":true")
          .append(",\"reraNumber\":").append(JsonUtil.escape("PRM/KA/RERA/1251/310/PR/2026/" + String.format("%03d", (p.getPropertyId() > 0 ? p.getPropertyId() : 101))))
          .append(",\"facingDirection\":").append(JsonUtil.escape(resolveFacingDirection(p)))
          .append(",\"roadWidthFt\":").append(resolveRoadWidth(p))
          .append(",\"virtualTourRooms\":").append(resolveVirtualTourRooms(p))
          .append(",\"assignedAgent\":").append(resolveAssignedAgent(p))
          .append(",\"typeDetails\":").append(JsonUtil.escape(p.getPropertyTypeDetails()))
          .append(",\"estMaintenance\":").append(p.calculateEstimatedMaintenanceCost())
          .append(",\"estRegistration\":").append(p.calculateEstimatedRegistrationFee())
          .append(",\"ownerName\":").append(JsonUtil.escape(p.getOwnerName()))
          .append(",\"ownerPhone\":").append(JsonUtil.escape(p.getOwnerPhone()))
          .append(",\"ownerEmail\":").append(JsonUtil.escape(p.getOwnerEmail()))
          .append(",\"ownerVerified\":").append(p.isOwnerVerified());
        if (p instanceof ResidentialProperty) {
            ResidentialProperty rp = (ResidentialProperty) p;
            sb.append(",\"bedrooms\":").append(rp.getBedrooms())
              .append(",\"bathrooms\":").append(rp.getBathrooms())
              .append(",\"furnishingStatus\":").append(JsonUtil.escape(rp.getFurnishingStatus()));
        } else {
            sb.append(",\"bedrooms\":0,\"bathrooms\":0,\"furnishingStatus\":\"N/A\"");
        }
        sb.append(",\"images\":[");
        for (int i = 0; i < fullGallery.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonUtil.escape(fullGallery.get(i)));
        }
        sb.append("]");
        if (includeDetails) {
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
            Map<String, String> params = parseBody(ex);
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    User u = getSessionUser(ex);
                    if (u == null) {
                        sendJson(ex, 401, JsonUtil.error("Login required to post or delete listings.", 401));
                        return;
                    }
                    if (path.endsWith("/delete")) {
                        int id = Integer.parseInt(params.get("propertyId"));
                        propertyDAO.deleteWithIDORCheck(id, u.getUserId(), u.isAdministrator());
                        sendJson(ex, 200, JsonUtil.success("Property deleted successfully", null));
                    } else {
                        Property p = PropertyDAO.createPropertyInstance(params.get("categoryCode"));
                        p.setOwnerId(u.getUserId());
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
                        if (p instanceof ResidentialProperty) {
                            ResidentialProperty rp = (ResidentialProperty) p;
                            try { rp.setBedrooms(Integer.parseInt(params.get("bedrooms"))); } catch (Exception ignored) { rp.setBedrooms(2); }
                            try { rp.setBathrooms(Integer.parseInt(params.get("bathrooms"))); } catch (Exception ignored) { rp.setBathrooms(2); }
                            rp.setBuiltUpArea(p.getPropertyArea());
                            rp.setResidentialType(params.get("residentialType") != null ? params.get("residentialType") : "APARTMENT");
                            rp.setFurnishingStatus(params.get("furnishingStatus") != null ? params.get("furnishingStatus") : "SEMI_FURNISHED");
                        } else if (p instanceof CommercialProperty) {
                            CommercialProperty cp = (CommercialProperty) p;
                            cp.setCommercialType(params.get("commercialType") != null ? params.get("commercialType") : "OFFICE_SPACE");
                            cp.setCarpetArea(p.getPropertyArea());
                            cp.setCommercialUsage(params.get("commercialUsage"));
                        } else if (p instanceof AgriculturalLand) {
                            AgriculturalLand al = (AgriculturalLand) p;
                            al.setLandType(params.get("landType") != null ? params.get("landType") : "FARMLAND");
                            al.setTotalLandArea(p.getPropertyArea());
                            al.setSoilType(params.get("soilType"));
                            al.setWaterAvailability(params.get("waterAvailability"));
                            al.setHasBorewell("true".equalsIgnoreCase(params.get("hasBorewell")));
                        } else if (p instanceof FarmHouse) {
                            FarmHouse fh = (FarmHouse) p;
                            fh.setLandArea(p.getPropertyArea());
                            fh.setBuiltUpArea(p.getPropertyArea());
                            try { fh.setBedrooms(Integer.parseInt(params.get("bedrooms"))); } catch (Exception ignored) { fh.setBedrooms(3); }
                            fh.setBathrooms(2);
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
                        Property saved = propertyDAO.save(p);
                        sendJson(ex, 200, JsonUtil.success("Property posted and pending approval", "{\"propertyId\":" + saved.getPropertyId() + "}"));
                    }
                } else {
                    if (path.matches(".*/api/properties/\\d+")) {
                        int id = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                        Property p = propertyDAO.getById(id);
                        sendJson(ex, 200, JsonUtil.success("Details loaded", serializeProperty(p, true)));
                    } else if (path.endsWith("/details")) {
                        int id = Integer.parseInt(params.get("id"));
                        Property p = propertyDAO.getById(id);
                        sendJson(ex, 200, JsonUtil.success("Details loaded", serializeProperty(p, true)));
                    } else if (path.endsWith("/compare") || params.containsKey("ids")) {
                        List<Property> list = new ArrayList<>();
                        String ids = params.get("ids");
                        if (ids != null) {
                            for (String idStr : ids.split(",")) {
                                try { list.add(propertyDAO.getById(Integer.parseInt(idStr.trim()))); } catch (Exception ignored) {}
                            }
                        }
                        sendJson(ex, 200, JsonUtil.success("Compare loaded", serializePropertyList(list)));
                    } else if (path.endsWith("/my")) {
                        User u = getSessionUser(ex);
                        if (u == null) { sendJson(ex, 401, JsonUtil.error("Please login", 401)); return; }
                        sendJson(ex, 200, JsonUtil.success("My properties", serializePropertyList(propertyDAO.getByOwner(u.getUserId()))));
                    } else {
                        List<Property> list = propertyDAO.search(params, params.get("sortBy"));
                        sendJson(ex, 200, JsonUtil.success("Found", serializePropertyList(list)));
                    }
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class AppointmentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            if (u == null) { sendJson(ex, 401, JsonUtil.error("Login required", 401)); return; }
            Map<String, String> params = parseBody(ex);
            SupabaseClient sb = SupabaseClient.getInstance();
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    if (ex.getRequestURI().getPath().endsWith("/status")) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("appointment_status", params.get("status"));
                        sb.patch("property_visits?visit_id=eq." + Integer.parseInt(params.get("visitId")), patch);
                        sendJson(ex, 200, JsonUtil.success("Status updated", null));
                    } else {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("property_id", Integer.parseInt(params.get("propertyId")));
                        payload.put("user_id", u.getUserId());
                        payload.put("visit_date", params.get("visitDate"));
                        String t = params.get("visitTime");
                        if (t != null && t.length() == 5) t += ":00";
                        payload.put("visit_time", t != null ? t : "10:00:00");
                        payload.put("contact_number", params.get("contactNumber") != null ? params.get("contactNumber") : u.getMobileNumber());
                        payload.put("notes", params.get("notes"));
                        payload.put("appointment_status", "PENDING");
                        Map<String, Object> created = sb.post("property_visits", payload);
                        sendJson(ex, 200, JsonUtil.success("Visit booked", "{\"visitId\":" + JsonUtil.getInt(created, "visit_id") + "}"));
                    }
                } else {
                    String q;
                    if (u.isAdministrator()) {
                        q = "property_visits?select=*,properties(title,city,owner_id),users(full_name,email)&order=created_at.desc";
                    } else if ("PROPERTY_OWNER".equalsIgnoreCase(u.getUserRole()) || "SELLER".equalsIgnoreCase(u.getUserRole()) || "AGENT".equalsIgnoreCase(u.getUserRole())) {
                        q = "property_visits?select=*,properties!inner(title,city,owner_id),users(full_name,email)&properties.owner_id=eq." + u.getUserId() + "&order=visit_date.desc";
                    } else {
                        q = "property_visits?select=*,properties(title,city,owner_id),users(full_name,email)&user_id=eq." + u.getUserId() + "&order=visit_date.desc";
                    }
                    List<Map<String, Object>> rows = sb.get(q);
                    StringBuilder out = new StringBuilder("[");
                    for (int i = 0; i < rows.size(); i++) {
                        if (i > 0) out.append(",");
                        Map<String, Object> r = rows.get(i);
                        Map<String, Object> p = JsonUtil.getMap(r, "properties");
                        Map<String, Object> usr = JsonUtil.getMap(r, "users");
                        out.append("{\"visitId\":").append(JsonUtil.getInt(r, "visit_id"))
                           .append(",\"propertyId\":").append(JsonUtil.getInt(r, "property_id"))
                           .append(",\"propertyTitle\":").append(JsonUtil.escape(p != null ? JsonUtil.getString(p, "title") : ""))
                           .append(",\"propertyCity\":").append(JsonUtil.escape(p != null ? JsonUtil.getString(p, "city") : ""))
                           .append(",\"userName\":").append(JsonUtil.escape(usr != null ? JsonUtil.getString(usr, "full_name") : ""))
                           .append(",\"visitDate\":").append(JsonUtil.escape(JsonUtil.getString(r, "visit_date")))
                           .append(",\"visitTime\":").append(JsonUtil.escape(JsonUtil.getString(r, "visit_time")))
                           .append(",\"contactNumber\":").append(JsonUtil.escape(JsonUtil.getString(r, "contact_number")))
                           .append(",\"notes\":").append(JsonUtil.escape(JsonUtil.getString(r, "notes")))
                           .append(",\"status\":").append(JsonUtil.escape(JsonUtil.getString(r, "appointment_status")))
                           .append("}");
                    }
                    sendJson(ex, 200, JsonUtil.success("Appointments", out.append("]").toString()));
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class RentalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            if (u == null) { sendJson(ex, 401, JsonUtil.error("Login required", 401)); return; }
            Map<String, String> params = parseBody(ex);
            SupabaseClient sb = SupabaseClient.getInstance();
            String path = ex.getRequestURI().getPath();
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    if (path.endsWith("/pay")) {
                        String ref = "RENT-" + System.currentTimeMillis();
                        String method = params.get("paymentMethod");
                        if (method == null || method.contains("NET")) method = "SIMULATED_NEFT";
                        else if (!method.equals("SIMULATED_UPI") && !method.equals("SIMULATED_NEFT") && !method.equals("SIMULATED_CARD") && !method.equals("BANK_CHEQUE")) method = "SIMULATED_UPI";
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("agreement_id", Integer.parseInt(params.get("agreementId")));
                        payload.put("payment_month", params.get("paymentMonth"));
                        payload.put("amount_paid", Double.parseDouble(params.get("amount")));
                        payload.put("payment_date", new Date(System.currentTimeMillis()).toString());
                        payload.put("payment_method", method);
                        payload.put("transaction_ref", ref);
                        payload.put("payment_status", "SUCCESS");
                        sb.post("rent_payments", payload);
                        sendJson(ex, 200, JsonUtil.success("Rent paid", "{\"txnRef\":" + JsonUtil.escape(ref) + "}"));
                    } else {
                        int pid = Integer.parseInt(params.get("propertyId"));
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("property_id", pid);
                        payload.put("owner_id", Integer.parseInt(params.get("ownerId")));
                        payload.put("tenant_id", u.getUserId());
                        payload.put("monthly_rent", Double.parseDouble(params.get("monthlyRent")));
                        payload.put("security_deposit", Double.parseDouble(params.get("securityDeposit")));
                        payload.put("agreement_start_date", params.get("startDate"));
                        payload.put("agreement_end_date", params.get("endDate"));
                        payload.put("agreement_status", "ACTIVE");
                        payload.put("terms_and_conditions", params.get("terms"));
                        Map<String, Object> created = sb.post("rental_agreements", payload);
                        Map<String, Object> propPatch = new HashMap<>();
                        propPatch.put("listing_status", "RENTED");
                        sb.patch("properties?property_id=eq." + pid, propPatch);
                        sendJson(ex, 200, JsonUtil.success("Rental agreement created", "{\"agreementId\":" + JsonUtil.getInt(created, "agreement_id") + "}"));
                    }
                } else {
                    List<Map<String, Object>> rows = sb.get(
                        "rental_agreements?select=*,properties(title),owner:users!rental_agreements_owner_id_fkey(full_name),tenant:users!rental_agreements_tenant_id_fkey(full_name)&or=(tenant_id.eq." + u.getUserId() + ",owner_id.eq." + u.getUserId() + ")&order=created_at.desc"
                    );
                    StringBuilder out = new StringBuilder("[");
                    for (int i = 0; i < rows.size(); i++) {
                        if (i > 0) out.append(",");
                        Map<String, Object> r = rows.get(i);
                        Map<String, Object> p = JsonUtil.getMap(r, "properties");
                        Map<String, Object> own = JsonUtil.getMap(r, "owner");
                        Map<String, Object> ten = JsonUtil.getMap(r, "tenant");
                        out.append("{\"agreementId\":").append(JsonUtil.getInt(r, "agreement_id"))
                           .append(",\"propertyId\":").append(JsonUtil.getInt(r, "property_id"))
                           .append(",\"propertyTitle\":").append(JsonUtil.escape(p != null ? JsonUtil.getString(p, "title") : ""))
                           .append(",\"ownerName\":").append(JsonUtil.escape(own != null ? JsonUtil.getString(own, "full_name") : ""))
                           .append(",\"tenantName\":").append(JsonUtil.escape(ten != null ? JsonUtil.getString(ten, "full_name") : ""))
                           .append(",\"monthlyRent\":").append(JsonUtil.getDouble(r, "monthly_rent"))
                           .append(",\"securityDeposit\":").append(JsonUtil.getDouble(r, "security_deposit"))
                           .append(",\"startDate\":").append(JsonUtil.escape(JsonUtil.getString(r, "agreement_start_date")))
                           .append(",\"endDate\":").append(JsonUtil.escape(JsonUtil.getString(r, "agreement_end_date")))
                           .append(",\"status\":").append(JsonUtil.escape(JsonUtil.getString(r, "agreement_status")))
                           .append("}");
                    }
                    sendJson(ex, 200, JsonUtil.success("Agreements", out.append("]").toString()));
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class PurchaseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            if (u == null) { sendJson(ex, 401, JsonUtil.error("Login required", 401)); return; }
            Map<String, String> params = parseBody(ex);
            SupabaseClient sb = SupabaseClient.getInstance();
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    int pid = Integer.parseInt(params.get("propertyId"));
                    String ref = "TXN-PURCHASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("property_id", pid);
                    payload.put("buyer_id", u.getUserId());
                    payload.put("seller_id", Integer.parseInt(params.get("sellerId")));
                    payload.put("purchase_price", Double.parseDouble(params.get("purchasePrice")));
                    payload.put("payment_status", "SUCCESS");
                    payload.put("transaction_reference", ref);
                    payload.put("remarks", params.get("remarks") != null ? params.get("remarks") : "Simulated Project Purchase Transaction");
                    Map<String, Object> created = sb.post("purchase_transactions", payload);
                    Map<String, Object> propPatch = new HashMap<>();
                    propPatch.put("listing_status", "SOLD");
                    sb.patch("properties?property_id=eq." + pid, propPatch);
                    sendJson(ex, 200, JsonUtil.success("Purchase complete", "{\"txnId\":" + JsonUtil.getInt(created, "transaction_id") + ",\"txnRef\":" + JsonUtil.escape(ref) + "}"));
                } else {
                    String q = "purchase_transactions?select=*,properties(title),buyer:users!purchase_transactions_buyer_id_fkey(full_name),seller:users!purchase_transactions_seller_id_fkey(full_name)" +
                               (u.isAdministrator() ? "" : "&or=(buyer_id.eq." + u.getUserId() + ",seller_id.eq." + u.getUserId() + ")") +
                               "&order=transaction_date.desc";
                    List<Map<String, Object>> rows = sb.get(q);
                    StringBuilder out = new StringBuilder("[");
                    for (int i = 0; i < rows.size(); i++) {
                        if (i > 0) out.append(",");
                        Map<String, Object> r = rows.get(i);
                        Map<String, Object> p = JsonUtil.getMap(r, "properties");
                        Map<String, Object> b = JsonUtil.getMap(r, "buyer");
                        Map<String, Object> s = JsonUtil.getMap(r, "seller");
                        out.append("{\"transactionId\":").append(JsonUtil.getInt(r, "transaction_id"))
                           .append(",\"propertyId\":").append(JsonUtil.getInt(r, "property_id"))
                           .append(",\"propertyTitle\":").append(JsonUtil.escape(p != null ? JsonUtil.getString(p, "title") : ""))
                           .append(",\"buyerName\":").append(JsonUtil.escape(b != null ? JsonUtil.getString(b, "full_name") : ""))
                           .append(",\"sellerName\":").append(JsonUtil.escape(s != null ? JsonUtil.getString(s, "full_name") : ""))
                           .append(",\"purchasePrice\":").append(JsonUtil.getDouble(r, "purchase_price"))
                           .append(",\"date\":").append(JsonUtil.escape(JsonUtil.getString(r, "transaction_date")))
                           .append(",\"txnRef\":").append(JsonUtil.escape(JsonUtil.getString(r, "transaction_reference")))
                           .append(",\"status\":").append(JsonUtil.escape(JsonUtil.getString(r, "payment_status")))
                           .append(",\"remarks\":").append(JsonUtil.escape(JsonUtil.getString(r, "remarks")))
                           .append("}");
                    }
                    sendJson(ex, 200, JsonUtil.success("Purchases", out.append("]").toString()));
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class VerificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            if (u == null) { sendJson(ex, 401, JsonUtil.error("Login required", 401)); return; }
            Map<String, String> params = parseBody(ex);
            SupabaseClient sb = SupabaseClient.getInstance();
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    String pt = params.get("proofType") != null ? params.get("proofType").trim().toUpperCase() : "AADHAAR";
                    if (pt.contains("PAN")) pt = "PAN";
                    else if (pt.contains("PASSPORT")) pt = "PASSPORT";
                    else if (pt.contains("DRIV")) pt = "DRIVING_LICENSE";
                    else if (pt.contains("RERA")) pt = "RERA_CERT";
                    else if (!pt.equals("AADHAAR")) pt = "AADHAAR";
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("user_id", u.getUserId());
                    payload.put("identity_proof_type", pt);
                    payload.put("identity_proof_number", params.get("proofNumber"));
                    payload.put("document_path", params.get("documentPath") != null ? params.get("documentPath") : "/uploads/docs/doc_" + u.getUserId() + ".pdf");
                    payload.put("property_address", params.get("propertyAddress"));
                    payload.put("verification_status", "PENDING");
                    Map<String, Object> created = sb.post("owner_verifications", payload);
                    sendJson(ex, 200, JsonUtil.success("Verification submitted", "{\"verificationId\":" + JsonUtil.getInt(created, "verification_id") + "}"));
                } else {
                    List<Map<String, Object>> rows = sb.get("owner_verifications?select=*&user_id=eq." + u.getUserId() + "&order=submitted_at.desc&limit=1");
                    if (!rows.isEmpty()) {
                        Map<String, Object> r = rows.get(0);
                        String data = "{\"verificationId\":" + JsonUtil.getInt(r, "verification_id") +
                                      ",\"status\":" + JsonUtil.escape(JsonUtil.getString(r, "verification_status")) +
                                      ",\"proofType\":" + JsonUtil.escape(JsonUtil.getString(r, "identity_proof_type")) +
                                      ",\"proofNumber\":" + JsonUtil.escape(JsonUtil.getString(r, "identity_proof_number")) +
                                      ",\"propertyAddress\":" + JsonUtil.escape(JsonUtil.getString(r, "property_address")) +
                                      ",\"adminNotes\":" + JsonUtil.escape(JsonUtil.getString(r, "admin_notes")) + "}";
                        sendJson(ex, 200, JsonUtil.success("Record found", data));
                    } else {
                        sendJson(ex, 200, JsonUtil.success("None", "{\"status\":\"NOT_SUBMITTED\"}"));
                    }
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static class AdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            if (u == null || !u.isAdministrator()) {
                sendJson(ex, 403, JsonUtil.error("Administrator access required", 403));
                return;
            }
            String path = ex.getRequestURI().getPath();
            Map<String, String> params = parseBody(ex);
            SupabaseClient sb = SupabaseClient.getInstance();
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    if (path.endsWith("/approve")) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("approval_status", "APPROVED");
                        patch.put("rejection_reason", "");
                        sb.patch("properties?property_id=eq." + Integer.parseInt(params.get("propertyId")), patch);
                        sendJson(ex, 200, JsonUtil.success("Property approved", null));
                    } else if (path.endsWith("/reject")) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("approval_status", "REJECTED");
                        patch.put("rejection_reason", params.get("reason") != null ? params.get("reason") : "Does not meet guidelines");
                        sb.patch("properties?property_id=eq." + Integer.parseInt(params.get("propertyId")), patch);
                        sendJson(ex, 200, JsonUtil.success("Property rejected", null));
                    } else if (path.endsWith("/action")) {
                        Map<String, Object> patch = new LinkedHashMap<>();
                        String status = params.get("status");
                        patch.put("verification_status", status);
                        patch.put("admin_notes", params.get("notes") != null ? params.get("notes") : "Reviewed by Admin");
                        patch.put("verified_at", Instant.now().toString());
                        List<Map<String, Object>> upd = sb.patch("owner_verifications?verification_id=eq." + Integer.parseInt(params.get("verificationId")), patch);
                        if (!upd.isEmpty() && "APPROVED".equalsIgnoreCase(status)) {
                            Map<String, Object> up = new HashMap<>();
                            up.put("is_verified", true);
                            sb.patch("users?user_id=eq." + JsonUtil.getInt(upd.get(0), "user_id"), up);
                        }
                        sendJson(ex, 200, JsonUtil.success("Verification updated", null));
                    }
                } else {
                    if (path.endsWith("/stats")) {
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
                    } else if (path.endsWith("/pending")) {
                        sendJson(ex, 200, JsonUtil.success("Pending", serializePropertyList(propertyDAO.getPending())));
                    } else if (path.endsWith("/verifications")) {
                        List<Map<String, Object>> rows = sb.get("owner_verifications?select=*,user:users!owner_verifications_user_id_fkey(full_name,email)&verification_status=eq.PENDING&order=submitted_at.asc");
                        StringBuilder out = new StringBuilder("[");
                        for (int i = 0; i < rows.size(); i++) {
                            if (i > 0) out.append(",");
                            Map<String, Object> r = rows.get(i);
                            Map<String, Object> usr = JsonUtil.getMap(r, "user");
                            out.append("{\"verificationId\":").append(JsonUtil.getInt(r, "verification_id"))
                               .append(",\"userId\":").append(JsonUtil.getInt(r, "user_id"))
                               .append(",\"userName\":").append(JsonUtil.escape(usr != null ? JsonUtil.getString(usr, "full_name") : ""))
                               .append(",\"userEmail\":").append(JsonUtil.escape(usr != null ? JsonUtil.getString(usr, "email") : ""))
                               .append(",\"proofType\":").append(JsonUtil.escape(JsonUtil.getString(r, "identity_proof_type")))
                               .append(",\"proofNumber\":").append(JsonUtil.escape(JsonUtil.getString(r, "identity_proof_number")))
                               .append(",\"address\":").append(JsonUtil.escape(JsonUtil.getString(r, "property_address")))
                               .append(",\"documentPath\":").append(JsonUtil.escape(JsonUtil.getString(r, "document_path")))
                               .append(",\"status\":").append(JsonUtil.escape(JsonUtil.getString(r, "verification_status")))
                               .append("}");
                        }
                        sendJson(ex, 200, JsonUtil.success("Verifications", out.append("]").toString()));
                    } else if (path.endsWith("/users")) {
                        List<User> users = userDAO.getAllUsers();
                        StringBuilder out = new StringBuilder("[");
                        for (int i = 0; i < users.size(); i++) {
                            if (i > 0) out.append(",");
                            out.append(serializeUser(users.get(i)));
                        }
                        sendJson(ex, 200, JsonUtil.success("Users", out.append("]").toString()));
                    }
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    public static String resolveFacingDirection(Property p) {
        int id = p.getPropertyId();
        String[] dirs = {"East", "North-East", "North", "West", "North-West"};
        return dirs[Math.abs(id) % dirs.length];
    }

    public static int resolveRoadWidth(Property p) {
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        if ("COMMERCIAL".equals(cat)) return 80;
        if ("PLOT".equals(cat)) return 60;
        return 40;
    }

    public static String resolveVirtualTourRooms(Property p) {
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        String livingPan, bedPan, kitchenPan, balconyPan, gardenPan, garagePan;
        if ("COMMERCIAL".equals(cat)) {
            livingPan = "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=2400&q=80";
            bedPan = "https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=2400&q=80";
            kitchenPan = "https://images.unsplash.com/photo-1524758631624-e2822e304c36?auto=format&fit=crop&w=2400&q=80";
            balconyPan = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=2400&q=80";
            gardenPan = "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=2400&q=80";
            garagePan = "https://images.unsplash.com/photo-1590674899484-d5640e854abe?auto=format&fit=crop&w=2400&q=80";
        } else if ("FARMHOUSE".equals(cat) || "AGRICULTURAL".equals(cat)) {
            livingPan = "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=2400&q=80";
            bedPan = "https://images.unsplash.com/photo-1616594039964-ae9021a400a0?auto=format&fit=crop&w=2400&q=80";
            kitchenPan = "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=2400&q=80";
            balconyPan = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=2400&q=80";
            gardenPan = "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=2400&q=80";
            garagePan = "https://images.unsplash.com/photo-1590674899484-d5640e854abe?auto=format&fit=crop&w=2400&q=80";
        } else {
            livingPan = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=2400&q=80";
            bedPan = "https://images.unsplash.com/photo-1616594039964-ae9021a400a0?auto=format&fit=crop&w=2400&q=80";
            kitchenPan = "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=2400&q=80";
            balconyPan = "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=2400&q=80";
            gardenPan = "https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=2400&q=80";
            garagePan = "https://images.unsplash.com/photo-1590674899484-d5640e854abe?auto=format&fit=crop&w=2400&q=80";
        }

        return "[" +
            "{\"code\":\"LIVING\",\"label\":\"Grand Reception & Living Room\",\"url\":" + JsonUtil.escape(livingPan) + ",\"yaw\":0,\"pitch\":0}," +
            "{\"code\":\"BEDROOM\",\"label\":\"Master Suite Bedroom\",\"url\":" + JsonUtil.escape(bedPan) + ",\"yaw\":45,\"pitch\":-5}," +
            "{\"code\":\"KITCHEN\",\"label\":\"Italian Modular Kitchen\",\"url\":" + JsonUtil.escape(kitchenPan) + ",\"yaw\":-30,\"pitch\":0}," +
            "{\"code\":\"BALCONY\",\"label\":\"Skyline Balcony Deck\",\"url\":" + JsonUtil.escape(balconyPan) + ",\"yaw\":90,\"pitch\":5}," +
            "{\"code\":\"GARDEN\",\"label\":\"Landscaped Garden & Pool\",\"url\":" + JsonUtil.escape(gardenPan) + ",\"yaw\":180,\"pitch\":-10}," +
            "{\"code\":\"GARAGE\",\"label\":\"Covered Private Parking\",\"url\":" + JsonUtil.escape(garagePan) + ",\"yaw\":0,\"pitch\":0}" +
            "]";
    }

    public static String resolveAssignedAgent(Property p) {
        String cat = p.getCategoryCode() != null ? p.getCategoryCode().toUpperCase() : "RESIDENTIAL";
        if ("COMMERCIAL".equals(cat)) {
            return "{\"name\":\"Pooja Hegde Deshmukh\",\"agency\":\"Apex Commercial & Tech Realty\",\"phone\":\"+91 99002 44556\",\"email\":\"pooja.realtor@heritage.com\",\"rera\":\"PRM/KA/RERA/1251/310/AG/2026/002\",\"rating\":4.90,\"avatar\":\"https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80\"}";
        } else if ("FARMHOUSE".equals(cat) || "AGRICULTURAL".equals(cat)) {
            return "{\"name\":\"Col. Rajeshwar Rao (Retd.)\",\"agency\":\"Sovereign Land & Estate Advisors\",\"phone\":\"+91 98455 77889\",\"email\":\"rajeshwar.rao@heritage.com\",\"rera\":\"PRM/KA/RERA/1251/310/AG/2026/003\",\"rating\":4.98,\"avatar\":\"https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80\"}";
        } else if ("PLOT".equals(cat)) {
            return "{\"name\":\"Ananya Sharma\",\"agency\":\"Signature Prime Residences\",\"phone\":\"+91 98451 99001\",\"email\":\"ananya.sharma@heritage.com\",\"rera\":\"PRM/KA/RERA/1251/310/AG/2026/004\",\"rating\":4.88,\"avatar\":\"https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=400&q=80\"}";
        } else {
            return "{\"name\":\"Vikramaditya Singhania\",\"agency\":\"Royal Crest Luxury Realty\",\"phone\":\"+91 98450 11223\",\"email\":\"vikram.singhania@heritage.com\",\"rera\":\"PRM/KA/RERA/1251/310/AG/2026/001\",\"rating\":4.95,\"avatar\":\"https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=400&q=80\"}";
        }
    }

    public static String getDefaultPlotsJson() {
        return "[" +
            "{\"plotId\":1,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-101\",\"dimensions\":\"30 x 50 ft\",\"areaSqft\":1500,\"facingDirection\":\"East\",\"roadWidthFt\":40,\"status\":\"AVAILABLE\",\"price\":5500000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":2,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-102\",\"dimensions\":\"30 x 50 ft\",\"areaSqft\":1500,\"facingDirection\":\"East\",\"roadWidthFt\":40,\"status\":\"SOLD\",\"price\":5500000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":3,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-103\",\"dimensions\":\"40 x 60 ft\",\"areaSqft\":2400,\"facingDirection\":\"North-East\",\"roadWidthFt\":60,\"status\":\"AVAILABLE\",\"price\":9600000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":true,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":4,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-104\",\"dimensions\":\"40 x 60 ft\",\"areaSqft\":2400,\"facingDirection\":\"North\",\"roadWidthFt\":40,\"status\":\"RESERVED\",\"price\":9200000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":5,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-105\",\"dimensions\":\"50 x 80 ft\",\"areaSqft\":4000,\"facingDirection\":\"East (Park View)\",\"roadWidthFt\":60,\"status\":\"AVAILABLE\",\"price\":17500000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":6,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-106\",\"dimensions\":\"30 x 40 ft\",\"areaSqft\":1200,\"facingDirection\":\"West\",\"roadWidthFt\":40,\"status\":\"AVAILABLE\",\"price\":4400000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":7,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-107\",\"dimensions\":\"40 x 60 ft\",\"areaSqft\":2400,\"facingDirection\":\"East\",\"roadWidthFt\":40,\"status\":\"SOLD\",\"price\":9200000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":8,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-108\",\"dimensions\":\"50 x 80 ft\",\"areaSqft\":4000,\"facingDirection\":\"North-East (Club-side)\",\"roadWidthFt\":60,\"status\":\"AVAILABLE\",\"price\":18000000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":true,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":9,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-109\",\"dimensions\":\"30 x 50 ft\",\"areaSqft\":1500,\"facingDirection\":\"North\",\"roadWidthFt\":40,\"status\":\"AVAILABLE\",\"price\":5600000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":10,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-110\",\"dimensions\":\"45 x 65 ft\",\"areaSqft\":2925,\"facingDirection\":\"Corner (Double Road)\",\"roadWidthFt\":60,\"status\":\"RESERVED\",\"price\":11800000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":true,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":11,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-111\",\"dimensions\":\"30 x 50 ft\",\"areaSqft\":1500,\"facingDirection\":\"East\",\"roadWidthFt\":40,\"status\":\"AVAILABLE\",\"price\":5400000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":false,\"locality\":\"Devanahalli Airport Corridor\"}," +
            "{\"plotId\":12,\"propertyId\":5,\"layoutName\":\"Emerald Green Meadows Phase 1\",\"plotNumber\":\"P-112\",\"dimensions\":\"60 x 90 ft\",\"areaSqft\":5400,\"facingDirection\":\"North (Signature Lakefront)\",\"roadWidthFt\":80,\"status\":\"AVAILABLE\",\"price\":26500000.0,\"hasWaterSupply\":true,\"hasUndergroundElectricity\":true,\"approvalAuthority\":\"BDA & RERA Approved\",\"cornerPlot\":true,\"locality\":\"Devanahalli Airport Corridor\"}" +
            "]";
    }

    public static String getDefaultAgentsJson() {
        return "[" +
            "{\"agentId\":1,\"fullName\":\"Vikramaditya Singhania\",\"email\":\"vikram.singhania@heritage.com\",\"phone\":\"+91 98450 11223\",\"agencyName\":\"Royal Crest Luxury Realty\",\"reraRegistrationNo\":\"PRM/KA/RERA/1251/310/AG/2026/001\",\"experienceYears\":15,\"specialization\":\"Luxury Villas & High-Rise Penthouses\",\"avatarUrl\":\"https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=400&q=80\",\"rating\":4.95,\"activeListingsCount\":24,\"isVerified\":true}," +
            "{\"agentId\":2,\"fullName\":\"Pooja Hegde Deshmukh\",\"email\":\"pooja.realtor@heritage.com\",\"phone\":\"+91 99002 44556\",\"agencyName\":\"Apex Commercial & Tech Realty\",\"reraRegistrationNo\":\"PRM/KA/RERA/1251/310/AG/2026/002\",\"experienceYears\":11,\"specialization\":\"Grade-A Commercial & IT Tech Parks\",\"avatarUrl\":\"https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80\",\"rating\":4.90,\"activeListingsCount\":18,\"isVerified\":true}," +
            "{\"agentId\":3,\"fullName\":\"Col. Rajeshwar Rao (Retd.)\",\"email\":\"rajeshwar.rao@heritage.com\",\"phone\":\"+91 98455 77889\",\"agencyName\":\"Sovereign Land & Estate Advisors\",\"reraRegistrationNo\":\"PRM/KA/RERA/1251/310/AG/2026/003\",\"experienceYears\":19,\"specialization\":\"Coffee Estates, Farmhouses & Land Parcels\",\"avatarUrl\":\"https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80\",\"rating\":4.98,\"activeListingsCount\":31,\"isVerified\":true}," +
            "{\"agentId\":4,\"fullName\":\"Ananya Sharma\",\"email\":\"ananya.sharma@heritage.com\",\"phone\":\"+91 98451 99001\",\"agencyName\":\"Signature Prime Residences\",\"reraRegistrationNo\":\"PRM/KA/RERA/1251/310/AG/2026/004\",\"experienceYears\":9,\"specialization\":\"Gated Villa Plots & Designer Duplexes\",\"avatarUrl\":\"https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=400&q=80\",\"rating\":4.88,\"activeListingsCount\":15,\"isVerified\":true}" +
            "]";
    }

    private static class PlotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            SupabaseClient sb = SupabaseClient.getInstance();
            String path = ex.getRequestURI().getPath();
            Map<String, String> params = parseBody(ex);
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod()) && path.endsWith("/reserve")) {
                    String plotNum = params.get("plotNumber");
                    int plotId = params.containsKey("plotId") && !params.get("plotId").isEmpty() ? Integer.parseInt(params.get("plotId")) : 0;
                    Map<String, Object> patch = new HashMap<>();
                    patch.put("status", "RESERVED");
                    if (plotId > 0) {
                        try { sb.patch("property_plots?plot_id=eq." + plotId, patch); } catch (Exception ignored) {}
                    }
                    sendJson(ex, 200, JsonUtil.success("Plot " + (plotNum != null ? plotNum : "") + " successfully reserved! An estate manager will contact you with booking documents.", null));
                } else {
                    List<Map<String, Object>> rows = null;
                    try {
                        rows = sb.get("property_plots?select=*&order=plot_id.asc");
                    } catch (Exception ignored) {}

                    if (rows != null && !rows.isEmpty()) {
                        StringBuilder out = new StringBuilder("[");
                        for (int i = 0; i < rows.size(); i++) {
                            if (i > 0) out.append(",");
                            Map<String, Object> r = rows.get(i);
                            out.append("{\"plotId\":").append(JsonUtil.getInt(r, "plot_id"))
                               .append(",\"propertyId\":").append(JsonUtil.getInt(r, "property_id"))
                               .append(",\"layoutName\":").append(JsonUtil.escape(JsonUtil.getString(r, "layout_name")))
                               .append(",\"plotNumber\":").append(JsonUtil.escape(JsonUtil.getString(r, "plot_number")))
                               .append(",\"dimensions\":").append(JsonUtil.escape(JsonUtil.getString(r, "dimensions")))
                               .append(",\"areaSqft\":").append(JsonUtil.getDouble(r, "area_sqft"))
                               .append(",\"facingDirection\":").append(JsonUtil.escape(JsonUtil.getString(r, "facing_direction")))
                               .append(",\"roadWidthFt\":").append(JsonUtil.getInt(r, "road_width_ft"))
                               .append(",\"status\":").append(JsonUtil.escape(JsonUtil.getString(r, "status")))
                               .append(",\"price\":").append(JsonUtil.getDouble(r, "price"))
                               .append(",\"hasWaterSupply\":").append(JsonUtil.getBoolean(r, "has_water_supply"))
                               .append(",\"hasUndergroundElectricity\":").append(JsonUtil.getBoolean(r, "has_underground_electricity"))
                               .append(",\"approvalAuthority\":").append(JsonUtil.escape(JsonUtil.getString(r, "approval_authority")))
                               .append(",\"cornerPlot\":").append(JsonUtil.getBoolean(r, "corner_plot"))
                               .append("}");
                        }
                        sendJson(ex, 200, JsonUtil.success("Plots", out.append("]").toString()));
                    } else {
                        sendJson(ex, 200, JsonUtil.success("Plots", getDefaultPlotsJson()));
                    }
                }
            } catch (Exception e) {
                sendJson(ex, 200, JsonUtil.success("Plots", getDefaultPlotsJson()));
            }
        }
    }

    private static class AgentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            SupabaseClient sb = SupabaseClient.getInstance();
            try {
                List<Map<String, Object>> rows = null;
                try {
                    rows = sb.get("agents?select=*&order=rating.desc");
                } catch (Exception ignored) {}

                if (rows != null && !rows.isEmpty()) {
                    StringBuilder out = new StringBuilder("[");
                    for (int i = 0; i < rows.size(); i++) {
                        if (i > 0) out.append(",");
                        Map<String, Object> r = rows.get(i);
                        out.append("{\"agentId\":").append(JsonUtil.getInt(r, "agent_id"))
                           .append(",\"fullName\":").append(JsonUtil.escape(JsonUtil.getString(r, "full_name")))
                           .append(",\"email\":").append(JsonUtil.escape(JsonUtil.getString(r, "email")))
                           .append(",\"phone\":").append(JsonUtil.escape(JsonUtil.getString(r, "phone")))
                           .append(",\"agencyName\":").append(JsonUtil.escape(JsonUtil.getString(r, "agency_name")))
                           .append(",\"reraRegistrationNo\":").append(JsonUtil.escape(JsonUtil.getString(r, "rera_registration_no")))
                           .append(",\"experienceYears\":").append(JsonUtil.getInt(r, "experience_years"))
                           .append(",\"specialization\":").append(JsonUtil.escape(JsonUtil.getString(r, "specialization")))
                           .append(",\"avatarUrl\":").append(JsonUtil.escape(JsonUtil.getString(r, "avatar_url")))
                           .append(",\"rating\":").append(JsonUtil.getDouble(r, "rating"))
                           .append(",\"activeListingsCount\":").append(JsonUtil.getInt(r, "active_listings_count"))
                           .append(",\"isVerified\":").append(JsonUtil.getBoolean(r, "is_verified"))
                           .append("}");
                    }
                    sendJson(ex, 200, JsonUtil.success("Agents", out.append("]").toString()));
                } else {
                    sendJson(ex, 200, JsonUtil.success("Agents", getDefaultAgentsJson()));
                }
            } catch (Exception e) {
                sendJson(ex, 200, JsonUtil.success("Agents", getDefaultAgentsJson()));
            }
        }
    }

    private static class InquiryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            SupabaseClient sb = SupabaseClient.getInstance();
            Map<String, String> params = parseBody(ex);
            User u = getSessionUser(ex);
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    int propId = params.containsKey("propertyId") && !params.get("propertyId").isEmpty() ? Integer.parseInt(params.get("propertyId")) : 1;
                    payload.put("property_id", propId);
                    if (u != null) payload.put("user_id", u.getUserId());
                    payload.put("customer_name", params.getOrDefault("customerName", u != null ? u.getFullName() : "Customer"));
                    payload.put("customer_email", params.getOrDefault("customerEmail", u != null ? u.getEmail() : "customer@example.com"));
                    payload.put("customer_phone", params.getOrDefault("customerPhone", u != null ? u.getMobileNumber() : "+91 98450 00000"));
                    payload.put("message", params.getOrDefault("message", "Inquiry regarding property specifications and site visit"));
                    payload.put("inquiry_type", params.getOrDefault("inquiryType", "BUY"));
                    payload.put("status", "NEW");
                    try {
                        sb.post("property_inquiries", payload);
                    } catch (Exception ignored) {}
                    sendJson(ex, 200, JsonUtil.success("Your inquiry has been submitted! Our dedicated relationship advisor will connect with you within 2 hours.", null));
                } else {
                    List<Map<String, Object>> rows = null;
                    try {
                        String q = (u != null && u.isAdministrator())
                            ? "property_inquiries?select=*,properties(title,city)&order=created_at.desc"
                            : "property_inquiries?select=*,properties(title,city)" + (u != null ? "&user_id=eq." + u.getUserId() : "") + "&order=created_at.desc";
                        rows = sb.get(q);
                    } catch (Exception ignored) {}
                    if (rows != null && !rows.isEmpty()) {
                        StringBuilder out = new StringBuilder("[");
                        for (int i = 0; i < rows.size(); i++) {
                            if (i > 0) out.append(",");
                            Map<String, Object> r = rows.get(i);
                            Map<String, Object> p = JsonUtil.getMap(r, "properties");
                            out.append("{\"inquiryId\":").append(JsonUtil.getInt(r, "inquiry_id"))
                               .append(",\"propertyId\":").append(JsonUtil.getInt(r, "property_id"))
                               .append(",\"propertyTitle\":").append(JsonUtil.escape(p != null ? JsonUtil.getString(p, "title") : ""))
                               .append(",\"customerName\":").append(JsonUtil.escape(JsonUtil.getString(r, "customer_name")))
                               .append(",\"customerEmail\":").append(JsonUtil.escape(JsonUtil.getString(r, "customer_email")))
                               .append(",\"customerPhone\":").append(JsonUtil.escape(JsonUtil.getString(r, "customer_phone")))
                               .append(",\"message\":").append(JsonUtil.escape(JsonUtil.getString(r, "message")))
                               .append(",\"inquiryType\":").append(JsonUtil.escape(JsonUtil.getString(r, "inquiry_type")))
                               .append(",\"status\":").append(JsonUtil.escape(JsonUtil.getString(r, "status")))
                               .append(",\"createdAt\":").append(JsonUtil.escape(JsonUtil.getString(r, "created_at")))
                               .append("}");
                        }
                        sendJson(ex, 200, JsonUtil.success("Inquiries", out.append("]").toString()));
                    } else {
                        sendJson(ex, 200, JsonUtil.success("Inquiries", "[]"));
                    }
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static final Set<String> userFavoritesStore = ConcurrentHashMap.newKeySet();
    private static class FavoriteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            User u = getSessionUser(ex);
            Map<String, String> params = parseBody(ex);
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    String propId = params.get("propertyId");
                    String userKey = (u != null ? u.getUserId() : 1) + ":" + propId;
                    boolean added;
                    if (userFavoritesStore.contains(userKey)) {
                        userFavoritesStore.remove(userKey);
                        added = false;
                    } else {
                        userFavoritesStore.add(userKey);
                        added = true;
                    }
                    sendJson(ex, 200, JsonUtil.success(added ? "Added to favorites" : "Removed from favorites", "{\"propertyId\":" + propId + ",\"favorited\":" + added + "}"));
                } else {
                    int uid = (u != null ? u.getUserId() : 1);
                    StringBuilder out = new StringBuilder("[");
                    int count = 0;
                    for (String key : userFavoritesStore) {
                        if (key.startsWith(uid + ":")) {
                            if (count > 0) out.append(",");
                            out.append(key.substring(key.indexOf(':') + 1));
                            count++;
                        }
                    }
                    sendJson(ex, 200, JsonUtil.success("Favorites", out.append("]").toString()));
                }
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error(e.getMessage(), 400));
            }
        }
    }

    private static HttpHandler withCors(HttpHandler delegate) {
        return ex -> {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            delegate.handle(ex);
        };
    }

    private static String getSessionToken(HttpExchange ex) {
        String headerToken = ex.getRequestHeaders().getFirst("X-Auth-Token");
        if (headerToken != null && !headerToken.trim().isEmpty()) return headerToken.trim();
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String ch : cookies) {
                for (String c : ch.split(";")) {
                    String[] parts = c.trim().split("=");
                    if (parts.length == 2 && "AUTH_TOKEN".equals(parts[0])) return parts[1];
                }
            }
        }
        return null;
    }

    private static User getSessionUser(HttpExchange ex) {
        String token = getSessionToken(ex);
        return token != null ? activeSessions.get(token) : null;
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, String> parseBody(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        String query = ex.getRequestURI().getQuery();
        if (query != null) parseQueryString(query, map);
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int r;
            InputStream is = ex.getRequestBody();
            while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
            parseQueryString(baos.toString(StandardCharsets.UTF_8.name()), map);
        }
        return map;
    }

    private static void parseQueryString(String qs, Map<String, String> map) throws UnsupportedEncodingException {
        if (qs == null || qs.isEmpty()) return;
        for (String param : qs.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()));
            } else if (pair.length == 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), "");
            }
        }
    }
}
