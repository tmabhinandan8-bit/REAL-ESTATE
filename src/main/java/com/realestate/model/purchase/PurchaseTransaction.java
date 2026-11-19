package com.realestate.model.purchase;

import java.sql.Timestamp;
import com.realestate.interfaces.Payable;
import java.util.UUID;

public class PurchaseTransaction implements Payable {
    private int transactionId;
    private int propertyId;
    private String propertyTitle;
    private int buyerId;
    private String buyerName;
    private int sellerId;
    private String sellerName;
    private double purchasePrice;
    private Timestamp transactionDate;
    private String paymentStatus; // PENDING, SUCCESS, FAILED
    private String transactionReference;
    private String remarks;

    public PurchaseTransaction() {
        this.transactionDate = new Timestamp(System.currentTimeMillis());
        this.paymentStatus = "SUCCESS";
    }

    @Override
    public double calculateTotalPayable() {
        return purchasePrice;
    }

    @Override
    public String generateTransactionReference() {
        return "TXN-PURCHASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public boolean processSimulatedPayment(double amount) {
        this.purchasePrice = amount;
        this.transactionReference = generateTransactionReference();
        this.paymentStatus = "SUCCESS";
        return true;
    }

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getPropertyId() { return propertyId; }
    public void setPropertyId(int propertyId) { this.propertyId = propertyId; }

    public String getPropertyTitle() { return propertyTitle; }
    public void setPropertyTitle(String propertyTitle) { this.propertyTitle = propertyTitle; }

    public int getBuyerId() { return buyerId; }
    public void setBuyerId(int buyerId) { this.buyerId = buyerId; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public double getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }

    public Timestamp getTransactionDate() { return transactionDate; }
    public void setTransactionDate(Timestamp transactionDate) { this.transactionDate = transactionDate; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
