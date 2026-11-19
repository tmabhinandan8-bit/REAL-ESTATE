package com.realestate.model.verification;

import java.sql.Timestamp;
import com.realestate.interfaces.Verifiable;

public class OwnerVerification implements Verifiable {
    private int verificationId;
    private int userId;
    private String userName;
    private String userEmail;
    private String identityProofType;
    private String identityProofNumber;
    private String documentPath;
    private String propertyAddress;
    private String verificationStatus; // PENDING, APPROVED, REJECTED
    private String adminNotes;
    private Timestamp submittedAt;
    private Timestamp verifiedAt;

    public OwnerVerification() {
        this.verificationStatus = "PENDING";
        this.submittedAt = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public boolean isVerified() {
        return "APPROVED".equalsIgnoreCase(this.verificationStatus);
    }

    @Override
    public void setVerified(boolean verified) {
        this.verificationStatus = verified ? "APPROVED" : "REJECTED";
        this.verifiedAt = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public String getVerificationDetails() {
        return "Verification #" + verificationId + " for User: " + userName + " (" + identityProofType + ")";
    }

    public int getVerificationId() { return verificationId; }
    public void setVerificationId(int verificationId) { this.verificationId = verificationId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getIdentityProofType() { return identityProofType; }
    public void setIdentityProofType(String identityProofType) { this.identityProofType = identityProofType; }

    public String getIdentityProofNumber() { return identityProofNumber; }
    public void setIdentityProofNumber(String identityProofNumber) { this.identityProofNumber = identityProofNumber; }

    public String getDocumentPath() { return documentPath; }
    public void setDocumentPath(String documentPath) { this.documentPath = documentPath; }

    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }

    public Timestamp getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Timestamp verifiedAt) { this.verifiedAt = verifiedAt; }
}
