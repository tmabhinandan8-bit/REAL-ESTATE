package com.realestate.model.user;

import com.realestate.interfaces.Verifiable;

public class Agent extends User implements Verifiable {
    private String agencyName;
    private String reraRegistrationNumber;
    private double commissionRatePercentage;

    public Agent() {
        super();
        this.setUserRole("AGENT");
        this.commissionRatePercentage = 2.0;
    }

    public Agent(int userId, String fullName, String email, String mobileNumber, String agencyName, String reraNumber) {
        super(userId, fullName, email, mobileNumber, "AGENT");
        this.agencyName = agencyName;
        this.reraRegistrationNumber = reraNumber;
        this.commissionRatePercentage = 2.0;
    }

    @Override
    public String getRolePermissionsDescription() {
        return "Permissions: Multi-property brokerage, project listings, client visit coordination.";
    }

    @Override
    public String getVerificationDetails() {
        return "RERA Agent: " + reraRegistrationNumber + " | Agency: " + agencyName;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getReraRegistrationNumber() {
        return reraRegistrationNumber;
    }

    public void setReraRegistrationNumber(String reraRegistrationNumber) {
        this.reraRegistrationNumber = reraRegistrationNumber;
    }

    public double getCommissionRatePercentage() {
        return commissionRatePercentage;
    }

    public void setCommissionRatePercentage(double commissionRatePercentage) {
        this.commissionRatePercentage = commissionRatePercentage;
    }
}
