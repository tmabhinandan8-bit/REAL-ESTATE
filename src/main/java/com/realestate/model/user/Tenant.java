package com.realestate.model.user;

import com.realestate.interfaces.Payable;
import java.util.UUID;

public class Tenant extends User implements Payable {
    private String employmentType;
    private double monthlyBudget;

    public Tenant() {
        super();
        this.setUserRole("TENANT");
    }

    public Tenant(int userId, String fullName, String email, String mobileNumber, String employmentType, double monthlyBudget) {
        super(userId, fullName, email, mobileNumber, "TENANT");
        this.employmentType = employmentType;
        this.monthlyBudget = monthlyBudget;
    }

    @Override
    public String getRolePermissionsDescription() {
        return "Permissions: Browse rental properties, book visits, manage rental agreements, pay rent.";
    }

    @Override
    public double calculateTotalPayable() {
        return this.monthlyBudget;
    }

    @Override
    public String generateTransactionReference() {
        return "TENANT-RENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public boolean processSimulatedPayment(double amount) {
        return amount > 0;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public double getMonthlyBudget() {
        return monthlyBudget;
    }

    public void setMonthlyBudget(double monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }
}
