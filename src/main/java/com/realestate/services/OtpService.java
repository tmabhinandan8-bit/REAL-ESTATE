package com.realestate.services;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.realestate.exceptions.AuthenticationException;
import com.realestate.util.PasswordUtil;

/**
 * Thread-safe OTP Manager for Forgot Password verification.
 * Generates cryptographically secure 6-digit OTPs with 5-minute TTL, attempt limits, and resend cooldown.
 */
public final class OtpService {

    private static final long OTP_TTL_MS = 5 * 60 * 1000L;       // 5 minutes
    private static final long RESEND_COOLDOWN_MS = 20 * 1000L;   // 20 seconds
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class OtpRecord {
        final String otpHash;
        final String salt;
        final long createdAt;
        final long expiresAt;
        int failedAttempts;
        boolean verified;
        String resetToken;

        OtpRecord(String otpHash, String salt, long createdAt, long expiresAt) {
            this.otpHash = otpHash;
            this.salt = salt;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    private static volatile OtpService instance;
    private final Map<String, OtpRecord> store = new ConcurrentHashMap<>();

    private OtpService() {}

    public static OtpService getInstance() {
        if (instance == null) {
            synchronized (OtpService.class) {
                if (instance == null) instance = new OtpService();
            }
        }
        return instance;
    }

    /**
     * Generates and stores a 6-digit OTP for the normalized email address.
     */
    public String generateOtp(String email) throws AuthenticationException {
        String key = email.trim().toLowerCase();
        long now = System.currentTimeMillis();
        OtpRecord existing = store.get(key);
        if (existing != null && (now - existing.createdAt) < RESEND_COOLDOWN_MS && now < existing.expiresAt) {
            long waitSec = Math.max(1, (RESEND_COOLDOWN_MS - (now - existing.createdAt)) / 1000);
            throw new AuthenticationException("Please wait " + waitSec + " seconds before requesting a new OTP.");
        }

        int code = 100000 + RANDOM.nextInt(900000);
        String otp = String.valueOf(code);
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(otp, salt);
        store.put(key, new OtpRecord(hash, salt, now, now + OTP_TTL_MS));
        return otp;
    }

    /**
     * Verifies a 6-digit OTP and returns a single-use reset token.
     */
    public String verifyOtp(String email, String otp) throws AuthenticationException {
        String key = email != null ? email.trim().toLowerCase() : "";
        OtpRecord rec = store.get(key);
        if (rec == null) {
            throw new AuthenticationException("No active OTP found for this email. Please request a new OTP.");
        }
        if (System.currentTimeMillis() > rec.expiresAt) {
            store.remove(key);
            throw new AuthenticationException("OTP has expired. Please request a new OTP.");
        }
        if (rec.failedAttempts >= MAX_ATTEMPTS) {
            store.remove(key);
            throw new AuthenticationException("Too many invalid OTP attempts. Please request a new OTP.");
        }
        if (otp == null || !PasswordUtil.verifyPassword(otp.trim(), rec.salt, rec.otpHash)) {
            rec.failedAttempts++;
            int remaining = MAX_ATTEMPTS - rec.failedAttempts;
            throw new AuthenticationException("Invalid OTP code. " + remaining + " attempt(s) remaining.");
        }

        rec.verified = true;
        rec.resetToken = UUID.randomUUID().toString();
        return rec.resetToken;
    }

    /**
     * Validates a reset token (or direct OTP) before completing password reset.
     */
    public void consumeResetAuthorization(String email, String resetToken, String otp) throws AuthenticationException {
        String key = email != null ? email.trim().toLowerCase() : "";
        OtpRecord rec = store.get(key);
        if (rec == null || System.currentTimeMillis() > rec.expiresAt) {
            store.remove(key);
            throw new AuthenticationException("Password reset session expired. Please request a new OTP.");
        }
        if (rec.verified && resetToken != null && resetToken.equals(rec.resetToken)) {
            store.remove(key);
            return;
        }
        if (otp != null && PasswordUtil.verifyPassword(otp.trim(), rec.salt, rec.otpHash)) {
            store.remove(key);
            return;
        }
        throw new AuthenticationException("Invalid or unverified OTP / reset token.");
    }
}
