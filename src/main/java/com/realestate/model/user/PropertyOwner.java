package com.realestate.model.user;

import com.realestate.interfaces.Verifiable;

public class PropertyOwner extends User implements Verifiable {
    private String titleDeedNumber;
    private int totalOwnedProperties;

    public PropertyOwner() {
        super();
        this.setUserRole("PROPERTY_OWNER");
    }

    public PropertyOwner(int userId, String fullName, String email, String mobileNumber) {
        super(userId, fullName, email, mobileNumber, "PROPERTY_OWNER");
    }

    @Override
    public String getRolePermissionsDescription() {
        return "Permissions: Post properties for sell/rent, manage rental agreements, receive rent, verify ownership.";
    }

    @Override
    public String getVerificationDetails() {
        return "Property Owner ID: " + getUserId() + " | Title Deed: " + (titleDeedNumber != null ? titleDeedNumber : "Pending");
    }

    public String getTitleDeedNumber() {
        return titleDeedNumber;
    }

    public void setTitleDeedNumber(String titleDeedNumber) {
        this.titleDeedNumber = titleDeedNumber;
    }

    public int getTotalOwnedProperties() {
        return totalOwnedProperties;
    }

    public void setTotalOwnedProperties(int totalOwnedProperties) {
        this.totalOwnedProperties = totalOwnedProperties;
    }
}
