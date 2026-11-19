package com.realestate.model.rental;

import java.sql.Date;
import com.realestate.interfaces.Payable;

public class RentPayment implements Payable {
    private int rentPaymentId;
    private int agreementId;
    private String paymentMonth;
    private double amountPaid;
    private Date paymentDate;
    private String paymentMethod;
    private String transactionRef;
    private String paymentStatus; // SUCCESS, PENDING, FAILED

    public RentPayment() {}

    @Override
    public double calculateTotalPayable() {
        return amountPaid;
    }

    @Override
    public String generateTransactionReference() {
        return "RENT-" + System.currentTimeMillis();
    }

    @Override
    public boolean processSimulatedPayment(double amount) {
        this.amountPaid = amount;
        this.paymentStatus = "SUCCESS";
        this.transactionRef = generateTransactionReference();
        return true;
    }

    public int getRentPaymentId() { return rentPaymentId; }
    public void setRentPaymentId(int rentPaymentId) { this.rentPaymentId = rentPaymentId; }

    public int getAgreementId() { return agreementId; }
    public void setAgreementId(int agreementId) { this.agreementId = agreementId; }

    public String getPaymentMonth() { return paymentMonth; }
    public void setPaymentMonth(String paymentMonth) { this.paymentMonth = paymentMonth; }

    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }

    public Date getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Date paymentDate) { this.paymentDate = paymentDate; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}
