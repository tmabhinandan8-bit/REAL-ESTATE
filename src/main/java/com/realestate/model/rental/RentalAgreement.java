package com.realestate.model.rental;

import java.sql.Date;
import java.sql.Timestamp;

public class RentalAgreement {
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
    private String agreementStatus; // ACTIVE, EXPIRED, TERMINATED
    private String termsAndConditions;
    private Timestamp createdAt;

    public RentalAgreement() {}

    public int getAgreementId() { return agreementId; }
    public void setAgreementId(int agreementId) { this.agreementId = agreementId; }

    public int getPropertyId() { return propertyId; }
    public void setPropertyId(int propertyId) { this.propertyId = propertyId; }

    public String getPropertyTitle() { return propertyTitle; }
    public void setPropertyTitle(String propertyTitle) { this.propertyTitle = propertyTitle; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public int getTenantId() { return tenantId; }
    public void setTenantId(int tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public double getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(double monthlyRent) { this.monthlyRent = monthlyRent; }

    public double getSecurityDeposit() { return securityDeposit; }
    public void setSecurityDeposit(double securityDeposit) { this.securityDeposit = securityDeposit; }

    public Date getAgreementStartDate() { return agreementStartDate; }
    public void setAgreementStartDate(Date agreementStartDate) { this.agreementStartDate = agreementStartDate; }

    public Date getAgreementEndDate() { return agreementEndDate; }
    public void setAgreementEndDate(Date agreementEndDate) { this.agreementEndDate = agreementEndDate; }

    public String getAgreementStatus() { return agreementStatus; }
    public void setAgreementStatus(String agreementStatus) { this.agreementStatus = agreementStatus; }

    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
