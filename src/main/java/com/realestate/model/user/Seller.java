package com.realestate.model.user;

import com.realestate.interfaces.Verifiable;

public class Seller extends User implements Verifiable {
    private int listingsCount;
    private String identityProofNumber;

    public Seller() {
        super();
        this.setUserRole("SELLER");
    }

    public Seller(int userId, String fullName, String email, String mobileNumber) {
        super(userId, fullName, email, mobileNumber, "SELLER");
    }

    @Override
    public String getRolePermissionsDescription() {
        return "Permissions: Post properties for sale/lease, view visit requests, accept purchase offers.";
    }

    @Override
    public String getVerificationDetails() {
        return "Seller ID: " + getUserId() + " | Status: " + (isVerified() ? "Approved" : "Pending Verification");
    }

    public int getListingsCount() {
        return listingsCount;
    }

    public void setListingsCount(int listingsCount) {
        this.listingsCount = listingsCount;
    }

    public String getIdentityProofNumber() {
        return identityProofNumber;
    }

    public void setIdentityProofNumber(String identityProofNumber) {
        this.identityProofNumber = identityProofNumber;
    }
}
