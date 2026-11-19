package com.realestate.interfaces;

/**
 * Interface for entities requiring administrative or document verification
 * such as property owners and title deeds.
 */
public interface Verifiable {
    boolean isVerified();
    void setVerified(boolean verified);
    String getVerificationDetails();
}
