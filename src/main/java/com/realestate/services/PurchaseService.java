package com.realestate.services;

import java.sql.SQLException;
import java.util.List;
import com.realestate.dao.PurchaseDAO;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.exceptions.PaymentFailedException;
import com.realestate.model.purchase.PurchaseTransaction;

public class PurchaseService {

    private final PurchaseDAO purchaseDAO;
    private final AsyncNotificationService notificationService;

    public PurchaseService() {
        this.purchaseDAO = new PurchaseDAO();
        this.notificationService = AsyncNotificationService.getInstance();
    }

    public PurchaseTransaction executeSimulatedPurchase(PurchaseTransaction txn) 
            throws SQLException, InvalidPropertyDataException, PaymentFailedException {
        if (txn.getPurchasePrice() <= 0) {
            throw new InvalidPropertyDataException("Purchase price must be positive.");
        }
        if (txn.getBuyerId() <= 0 || txn.getPropertyId() <= 0) {
            throw new InvalidPropertyDataException("Buyer and Property are required.");
        }

        txn.processSimulatedPayment(txn.getPurchasePrice());
        PurchaseTransaction recorded = purchaseDAO.recordPurchaseTransaction(txn);

        notificationService.dispatchNotification(
            "buyer@realestate.com",
            "Property Purchase Confirmed!",
            "Congratulations! Your purchase transaction #" + recorded.getTransactionReference() + " for property #" + recorded.getPropertyId() + " has completed successfully."
        );

        return recorded;
    }

    public List<PurchaseTransaction> getPurchasesForUser(int userId) throws SQLException {
        return purchaseDAO.getPurchasesByUser(userId);
    }

    public List<PurchaseTransaction> getAllTransactions() throws SQLException {
        return purchaseDAO.getAllPurchaseTransactions();
    }
}
