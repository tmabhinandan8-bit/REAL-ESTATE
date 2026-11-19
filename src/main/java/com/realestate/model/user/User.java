package com.realestate.model.user;

import java.sql.Timestamp;
import com.realestate.interfaces.Auditable;

/**
 * Abstract Base Class demonstrating Java OOP Inheritance and Encapsulation.
 * Represents common properties and behaviors for all system users.
 */
public abstract class User implements Auditable {
    // Encapsulated private fields
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

    // Default Constructor demonstrating 'this' chaining
    public User() {
        this(0, "", "", "", "BUYER");
    }

    // Parameterized Constructor
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

    // Abstract method enforcing polymorphism across user types
    public abstract String getRolePermissionsDescription();

    // Polymorphic method that can be overridden
    public String getDisplayBadge() {
        return userRole + (isVerified ? " [VERIFIED]" : "");
    }

    // Final method: cannot be overridden by subclasses (OOP demonstration)
    public final boolean isAdministrator() {
        return "ADMIN".equalsIgnoreCase(this.userRole);
    }

    @Override
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @Override
    public String getAuditSummary() {
        return "User ID: " + userId + " | Name: " + fullName + " | Role: " + userRole;
    }

    // Getters and Setters (Encapsulation)
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        if (fullName != null) {
            this.fullName = fullName.trim();
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email != null) {
            this.email = email.trim().toLowerCase();
        }
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        this.isVerified = verified;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
