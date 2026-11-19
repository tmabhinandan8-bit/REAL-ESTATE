package com.realestate.services;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.realestate.dao.UserDAO;
import com.realestate.exceptions.AuthenticationException;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.model.user.User;

public class UserService {

    private final UserDAO userDAO;
    private final AsyncNotificationService notificationService;

    public UserService() {
        this.userDAO = new UserDAO();
        this.notificationService = AsyncNotificationService.getInstance();
    }

    public EmailService.EmailValidationResult validateEmail(String email) {
        return EmailService.validateEmail(email);
    }

    public boolean emailExists(String email) throws SQLException {
        return userDAO.findByEmail(email) != null;
    }

    public User registerUser(String fullName, String email, String mobileNumber, String password, String role)
            throws SQLException, AuthenticationException, InvalidPropertyDataException {
        if (fullName == null || fullName.trim().length() < 2) {
            throw new InvalidPropertyDataException("Full name must be at least 2 characters.");
        }
        EmailService.EmailValidationResult emailCheck = EmailService.validateEmail(email);
        if (!emailCheck.valid) {
            throw new InvalidPropertyDataException(emailCheck.message);
        }
        if (password == null || password.length() < 6) {
            throw new InvalidPropertyDataException("Password must be at least 6 characters long.");
        }
        if (mobileNumber == null || mobileNumber.trim().length() < 8) {
            throw new InvalidPropertyDataException("Please provide a valid mobile number.");
        }

        String normalizedRole = (role != null && !role.trim().isEmpty()) ? role.trim().toUpperCase() : "BUYER";
        if ("ADMIN".equals(normalizedRole)) {
            throw new AuthenticationException("Administrator accounts cannot be created via public registration.");
        }
        if (!normalizedRole.equals("BUYER") && !normalizedRole.equals("SELLER") && !normalizedRole.equals("TENANT")
                && !normalizedRole.equals("PROPERTY_OWNER") && !normalizedRole.equals("AGENT")) {
            throw new InvalidPropertyDataException("Invalid user role specified.");
        }

        User user = UserDAO.createUserInstance(normalizedRole);
        user.setFullName(fullName);
        user.setEmail(emailCheck.normalizedEmail);
        user.setMobileNumber(mobileNumber);
        user.setUserRole(normalizedRole);

        User registered = userDAO.registerUser(user, password);

        notificationService.dispatchNotification(
            registered.getEmail(),
            "Welcome to Real Estate Management System",
            "Dear " + registered.getFullName() + ", your account with role " + registered.getUserRole() + " has been successfully registered."
        );

        return registered;
    }

    public User login(String email, String password) throws SQLException, AuthenticationException {
        if (email == null || password == null || email.trim().isEmpty() || password.trim().isEmpty()) {
            throw new AuthenticationException("Email and password cannot be empty.");
        }
        EmailService.EmailValidationResult emailCheck = EmailService.validateEmail(email);
        if (!emailCheck.valid) {
            throw new AuthenticationException(emailCheck.message);
        }
        return userDAO.authenticate(emailCheck.normalizedEmail, password);
    }

    /**
     * Generates a 6-digit OTP and dispatches it to the user's registered email address.
     */
    public Map<String, Object> requestPasswordResetOtp(String email)
            throws SQLException, AuthenticationException, InvalidPropertyDataException {
        EmailService.EmailValidationResult check = EmailService.validateEmail(email);
        if (!check.valid) {
            throw new InvalidPropertyDataException(check.message);
        }
        User user = userDAO.findByEmail(check.normalizedEmail);
        if (user == null) {
            throw new AuthenticationException("No registered account found with email: " + check.normalizedEmail);
        }

        String otp = OtpService.getInstance().generateOtp(check.normalizedEmail);
        EmailService.EmailSendResult sendRes = EmailService.sendOtpEmail(user.getEmail(), user.getFullName(), otp);

        notificationService.dispatchNotification(
            user.getEmail(),
            "Password Reset OTP Requested",
            "OTP dispatched (" + sendRes.deliveryMode + ") for account " + user.getEmail()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("email", EmailService.maskEmail(user.getEmail()));
        result.put("deliveryMode", sendRes.deliveryMode);
        result.put("sentToInbox", sendRes.sentToInbox);
        result.put("infoMessage", sendRes.infoMessage);
        result.put("expiresInSeconds", 300);
        if (!sendRes.sentToInbox) {
            result.put("demoOtp", otp);
        }
        return result;
    }

    public String verifyPasswordResetOtp(String email, String otp) throws AuthenticationException {
        return OtpService.getInstance().verifyOtp(email, otp);
    }

    public void resetPassword(String email, String resetToken, String otp, String newPassword)
            throws SQLException, AuthenticationException, InvalidPropertyDataException {
        if (newPassword == null || newPassword.length() < 6) {
            throw new InvalidPropertyDataException("New password must be at least 6 characters long.");
        }
        EmailService.EmailValidationResult check = EmailService.validateEmail(email);
        if (!check.valid) {
            throw new InvalidPropertyDataException(check.message);
        }
        OtpService.getInstance().consumeResetAuthorization(check.normalizedEmail, resetToken, otp);
        boolean updated = userDAO.updatePasswordByEmail(check.normalizedEmail, newPassword);
        if (!updated) {
            throw new AuthenticationException("Failed to update password. Account not found.");
        }
        notificationService.dispatchNotification(
            check.normalizedEmail,
            "Password Reset Successful",
            "Your Real Estate account password was successfully updated."
        );
    }

    public User getUserById(int userId) throws SQLException {
        return userDAO.getUserById(userId);
    }

    public List<User> getAllUsers() throws SQLException {
        return userDAO.getAllUsers();
    }
}
