package com.realestate.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility class demonstrating Java OOP 'final' and 'static' concepts.
 * Provides cryptographically secure salted SHA-256 hashing for passwords.
 */
public final class PasswordUtil {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Private constructor prevents instantiation (Utility class pattern)
    private PasswordUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates a random cryptographic hex salt.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Hashes a password with a specific salt using SHA-256.
     */
    public static String hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            String saltedPassword = password + salt;
            byte[] hashBytes = digest.digest(saltedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in current JVM", e);
        }
    }

    /**
     * Verifies if an entered raw password matches the stored salted hash.
     */
    public static boolean verifyPassword(String enteredPassword, String storedSalt, String storedHash) {
        if (enteredPassword == null || storedSalt == null || storedHash == null) {
            return false;
        }
        String calculatedHash = hashPassword(enteredPassword, storedSalt);
        return calculatedHash.equalsIgnoreCase(storedHash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
