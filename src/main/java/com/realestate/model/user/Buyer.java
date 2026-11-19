package com.realestate.model.user;

import com.realestate.interfaces.Payable;
import java.util.UUID;

/**
 * Concrete class representing a prospective property Buyer.
 * Demonstrates inheritance and implementation of Payable interface.
 */
public class Buyer extends User implements Payable {
    private double maxBudget;
    private String preferredCity;

    public Buyer() {
        super();
        this.setUserRole("BUYER");
    }

    public Buyer(int userId, String fullName, String email, String mobileNumber, double maxBudget, String preferredCity) {
        super(userId, fullName, email, mobileNumber, "BUYER");
        this.maxBudget = maxBudget;
        this.preferredCity = preferredCity;
    }

    @Override
    public String getRolePermissionsDescription() {
        return "Permissions: Browse properties, book visits, make purchase offers, submit reviews.";
    }

    @Override
    public double calculateTotalPayable() {
        return this.maxBudget;
    }

    @Override
    public String generateTransactionReference() {
        return "BUYER-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public boolean processSimulatedPayment(double amount) {
        return amount > 0 && amount <= this.maxBudget;
    }

    public double getMaxBudget() {
        return maxBudget;
    }

    public void setMaxBudget(double maxBudget) {
        this.maxBudget = maxBudget;
    }

    public String getPreferredCity() {
        return preferredCity;
    }

    public void setPreferredCity(String preferredCity) {
        this.preferredCity = preferredCity;
    }
}
