package com.realestate.model.property;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import com.realestate.interfaces.Auditable;
import com.realestate.interfaces.Searchable;
import com.realestate.exceptions.InvalidPropertyDataException;

/**
 * Abstract Base Class demonstrating Java OOP Inheritance, Polymorphism,
 * Abstraction, and Encapsulation. Represents the base for all 6 property types.
 */
public abstract class Property implements Searchable, Auditable {
    // Encapsulated private fields
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
    private String areaUnit; // 'Sq. Ft.', 'Acres', 'Cents'
    private String state;
    private String city;
    private String locality;
    private String address;
    private String pincode;
    private String approvalStatus; // PENDING, APPROVED, REJECTED
    private String listingStatus;  // AVAILABLE, UNDER_OFFER, SOLD, RENTED, INACTIVE
    private boolean isFeatured;
    private String rejectionReason;
    private String primaryImageUrl;
    private List<String> imageUrls = new ArrayList<>();
    private List<String> amenities = new ArrayList<>();
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Joined Owner Information
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private boolean ownerVerified;

    // Default Constructor demonstrating 'this' chaining
    public Property() {
        this(0, 0, "", "BUY", 0.0, 0.0, "Karnataka", "Mysuru");
    }

    // Parameterized Constructor
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
        this.isFeatured = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    // Abstract methods demonstrating Polymorphism
    public abstract String getPropertyTypeDetails();
    public abstract double calculateEstimatedMaintenanceCost();
    public abstract void validateCategorySpecifics() throws InvalidPropertyDataException;

    // Method Overloading demonstration (1): Default government stamp duty/registration estimate (6.6%)
    public double calculateEstimatedRegistrationFee() {
        return calculateEstimatedRegistrationFee(6.6);
    }

    // Method Overloading demonstration (2): Overloaded method with custom stamp duty percentage
    public double calculateEstimatedRegistrationFee(double customRatePercentage) {
        if ("BUY".equalsIgnoreCase(this.purpose)) {
            return (this.price * customRatePercentage) / 100.0;
        }
        return 0.0; // No registration fee for standard rentals/leases
    }

    // Implementing Searchable interface methods
    @Override
    public boolean matchesKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return true;
        String kw = keyword.toLowerCase().trim();
        return (title != null && title.toLowerCase().contains(kw)) ||
               (description != null && description.toLowerCase().contains(kw)) ||
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

    // Implementing Auditable interface methods
    @Override
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @Override
    public String getAuditSummary() {
        return "Property ID: " + propertyId + " | Title: " + title + " | Price: Rs." + price + " | Status: " + approvalStatus;
    }

    // Getters and Setters (Encapsulation)
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

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

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
