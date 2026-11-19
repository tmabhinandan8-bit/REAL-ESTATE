package com.realestate.services;

import java.sql.SQLException;
import java.util.List;
import com.realestate.dao.VerificationDAO;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.model.verification.OwnerVerification;

public class VerificationService {

    private final VerificationDAO verificationDAO;
    private final AsyncNotificationService notificationService;

    public VerificationService() {
        this.verificationDAO = new VerificationDAO();
        this.notificationService = AsyncNotificationService.getInstance();
    }

    public OwnerVerification submitVerification(OwnerVerification v) throws SQLException, InvalidPropertyDataException {
        if (v.getIdentityProofNumber() == null || v.getIdentityProofNumber().trim().isEmpty()) {
            throw new InvalidPropertyDataException("Identity proof number is required.");
        }
        if (v.getPropertyAddress() == null || v.getPropertyAddress().trim().isEmpty()) {
            throw new InvalidPropertyDataException("Property address must be specified for title verification.");
        }

        OwnerVerification submitted = verificationDAO.submitVerification(v);

        notificationService.dispatchNotification(
            "admin@realestate.com",
            "New Owner Verification Request",
            "Owner verification request #" + submitted.getVerificationId() + " submitted by User ID #" + submitted.getUserId()
        );

        return submitted;
    }

    public List<OwnerVerification> getPendingVerifications() throws SQLException {
        return verificationDAO.getPendingVerifications();
    }

    public boolean updateStatus(int verificationId, String status, String adminNotes) throws SQLException {
        return verificationDAO.updateVerificationStatus(verificationId, status, adminNotes);
    }

    public OwnerVerification getByUserId(int userId) throws SQLException {
        return verificationDAO.getVerificationByUserId(userId);
    }
}
