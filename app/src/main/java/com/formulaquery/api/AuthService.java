package com.formulaquery.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * ------------------------------------------------------------
 * Authentication Service
 * ------------------------------------------------------------
 * Handles:
 * - User Registration
 * - OTP Verification
 * - User Login
 * - Forgot Password Flow
 * - Password Reset
 * - OTP Email Sending
 *
 * Security Features:
 * - BCrypt password encryption
 * - OTP expiration handling
 * - Email verification system
 *
 * This service contains the core authentication
 * business logic for FormulaQuery.
 * ------------------------------------------------------------
 */

@Service
public class AuthService {

    /**
     * Repository used for database operations
     * related to User entity
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Spring mail service used
     * for sending OTP emails
     */
    @Autowired
    private JavaMailSender mailSender;

    /**
     * Password encoder for secure
     * password hashing
     */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ------------------------------------------------------------
    // Register New User
    // ------------------------------------------------------------

    /**
     * Registers a new user
     *
     * Flow:
     * 1. Check existing email
     * 2. Encrypt password
     * 3. Generate OTP
     * 4. Save user
     * 5. Send verification email
     *
     * @return registration status
     */
    public String register(String name, String email, String phone, String password) {

        // Prevent duplicate email registration
        if (userRepository.existsByEmail(email)) {
            return "EMAIL_EXISTS";
        }

        // Create new user object
        User user = new User();

        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);

        // Encrypt password before saving
        user.setPassword(encoder.encode(password));

        // Account remains unverified until OTP verification
        user.setVerified(false);

        // Generate verification OTP
        String otp = generateOtp();

        user.setOtp(otp);

        // OTP valid for 10 minutes
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));

        // Save user in database
        userRepository.save(user);

        // Send OTP email
        sendOtpEmail(email, otp, "Verify your 4mulaQuery account");

        return "OTP_SENT";
    }

    // ------------------------------------------------------------
    // Verify OTP
    // ------------------------------------------------------------

    /**
     * Verifies email OTP
     *
     * Checks:
     * - User existence
     * - OTP validity
     * - OTP expiration
     *
     * @return verification status
     */
    public String verifyOtp(String email, String otp) {

        Optional<User> opt = userRepository.findByEmail(email);

        // User not found
        if (opt.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        User user = opt.get();

        // Invalid OTP
        if (user.getOtp() == null || !user.getOtp().equals(otp)) {
            return "INVALID_OTP";
        }

        // Expired OTP
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return "OTP_EXPIRED";
        }

        // Mark account verified
        user.setVerified(true);

        // Clear OTP after successful verification
        user.setOtp(null);
        user.setOtpExpiry(null);

        userRepository.save(user);

        return "VERIFIED";
    }

    // ------------------------------------------------------------
    // User Login
    // ------------------------------------------------------------

    /**
     * Authenticates user login
     *
     * Checks:
     * - User existence
     * - Verification status
     * - Password correctness
     *
     * @return login status
     */
    public String login(String email, String password) {

        Optional<User> opt = userRepository.findByEmail(email);

        // User not found
        if (opt.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        User user = opt.get();

        // Block login if email not verified
        if (!user.isVerified()) {
            return "NOT_VERIFIED";
        }

        // Validate encrypted password
        if (!encoder.matches(password, user.getPassword())) {
            return "WRONG_PASSWORD";
        }

        return "SUCCESS:" + user.getName();
    }

    // ------------------------------------------------------------
    // Forgot Password
    // ------------------------------------------------------------

    /**
     * Sends password reset OTP
     *
     * @return reset status
     */
    public String forgotPassword(String email) {

        Optional<User> opt = userRepository.findByEmail(email);

        // User not found
        if (opt.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        User user = opt.get();

        // Generate password reset OTP
        String otp = generateOtp();

        user.setOtp(otp);

        // OTP valid for 10 minutes
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));

        userRepository.save(user);

        // Send reset OTP email
        sendOtpEmail(email, otp, "Reset your 4mulaQuery password");

        return "OTP_SENT";
    }

    // ------------------------------------------------------------
    // Reset Password
    // ------------------------------------------------------------

    /**
     * Resets user password
     *
     * Checks:
     * - OTP correctness
     * - OTP expiration
     *
     * @return reset status
     */
    public String resetPassword(String email, String otp, String newPassword) {

        Optional<User> opt = userRepository.findByEmail(email);

        // User not found
        if (opt.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        User user = opt.get();

        // Invalid OTP
        if (user.getOtp() == null || !user.getOtp().equals(otp)) {
            return "INVALID_OTP";
        }

        // Expired OTP
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return "OTP_EXPIRED";
        }

        // Encrypt and update password
        user.setPassword(encoder.encode(newPassword));

        // Clear OTP after successful password reset
        user.setOtp(null);
        user.setOtpExpiry(null);

        userRepository.save(user);

        return "PASSWORD_RESET";
    }

    // ------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------

    /**
     * Generates 6-digit OTP
     */
    private String generateOtp() {

        return String.format(
            "%06d",
            new Random().nextInt(999999)
        );
    }

    /**
     * Sends OTP email
     *
     * @param to recipient email
     * @param otp generated OTP
     * @param subject email subject
     */
    private void sendOtpEmail(String to, String otp, String subject) {

        SimpleMailMessage msg = new SimpleMailMessage();

        msg.setTo(to);

        msg.setSubject(subject);

        msg.setText(
            "Your 4mulaQuery OTP is: " + otp + "\n\n" +
            "This OTP is valid for 10 minutes.\n\n" +
            "4mulaQuery — Intelligent Database Engine"
        );

        mailSender.send(msg);
    }
}