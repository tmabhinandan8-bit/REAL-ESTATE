package com.realestate.services;

import java.sql.SQLException;
import java.util.List;
import com.realestate.dao.RentalDAO;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.model.rental.RentalAgreement;
import com.realestate.model.rental.RentPayment;

public class RentalService {

    private final RentalDAO rentalDAO;
    private final AsyncNotificationService notificationService;

    public RentalService() {
        this.rentalDAO = new RentalDAO();
        this.notificationService = AsyncNotificationService.getInstance();
    }

    public RentalAgreement createAgreement(RentalAgreement agreement) throws SQLException, InvalidPropertyDataException {
        if (agreement.getMonthlyRent() <= 0) {
            throw new InvalidPropertyDataException("Monthly rent must be greater than zero.");
        }
        if (agreement.getAgreementStartDate() == null || agreement.getAgreementEndDate() == null) {
            throw new InvalidPropertyDataException("Start date and end date are required.");
        }

        RentalAgreement created = rentalDAO.createRentalAgreement(agreement);

        notificationService.dispatchNotification(
            "tenant@realestate.com",
            "Rental Agreement Activated",
            "Rental agreement #" + created.getAgreementId() + " has been initiated for property #" + created.getPropertyId()
        );

        return created;
    }

    public List<RentalAgreement> getAgreementsForUser(int userId) throws SQLException {
        return rentalDAO.getAgreementsByUser(userId);
    }

    public RentPayment payRent(RentPayment payment) throws SQLException, InvalidPropertyDataException {
        if (payment.getAmountPaid() <= 0) {
            throw new InvalidPropertyDataException("Rent payment amount must be positive.");
        }
        payment.processSimulatedPayment(payment.getAmountPaid());
        RentPayment recorded = rentalDAO.recordRentPayment(payment);

        notificationService.dispatchNotification(
            "owner@realestate.com",
            "Rent Payment Received",
            "Rent of Rs. " + recorded.getAmountPaid() + " received for month " + recorded.getPaymentMonth() + ". Ref: " + recorded.getTransactionRef()
        );

        return recorded;
    }

    public List<RentPayment> getRentPayments(int agreementId) throws SQLException {
        return rentalDAO.getRentPaymentsForAgreement(agreementId);
    }
}
