package com.realestate.interfaces;

/**
 * Interface demonstrating OOP Payable operations for financial transactions
 * like rental payments, booking tokens, and simulated property purchases.
 */
public interface Payable {
    double calculateTotalPayable();
    String generateTransactionReference();
    boolean processSimulatedPayment(double amount);
}
