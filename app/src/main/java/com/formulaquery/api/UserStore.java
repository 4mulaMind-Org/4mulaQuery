package com.formulaquery.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service component responsible for managing user-related operations.
 *
 * <p>This class acts as a bridge between the application and the
 * UserRepository. It provides functionality for:
 * <ul>
 *     <li>User registration</li>
 *     <li>User authentication (login)</li>
 *     <li>OTP generation and storage</li>
 *     <li>OTP verification</li>
 *     <li>Password reset</li>
 * </ul>
 *
 * <p>All user data is stored and retrieved using MongoDB.</p>
 *
 * @author Abdul Qadir
 * @version 1.0
 */
@Component
public class UserStore {

    /**
     * Repository used for performing database operations
     * on User documents.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Finds a user by email.
     *
     * @param email User's email address.
     * @return Optional containing the user if found,
     *         otherwise Optional.empty().
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Checks whether a user already exists.
     *
     * @param email User's email address.
     * @return true if the email exists, otherwise false.
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Registers a new user.
     *
     * <p>If the email is already registered,
     * registration is rejected.</p>
     *
     * @param name User's full name.
     * @param email User's email.
     * @param password User's password.
     * @return true if registration succeeds,
     *         false if the email already exists.
     */
    public boolean register(String name, String email, String password) {

        // Prevent duplicate registration
        if (existsByEmail(email)) return false;

        // Save the new user
        userRepository.save(new User(name, email, password));

        // Log successful registration
        System.out.println("[UserStore] Registered: " + email);

        return true;
    }

    /**
     * Authenticates a user using email and password.
     *
     * @param email User's email.
     * @param password User's password.
     * @return Optional containing the authenticated user
     *         if credentials are valid.
     */
    public Optional<User> login(String email, String password) {
        return findByEmail(email)
            .filter(u -> u.getPassword().equals(password));
    }

    /**
     * Saves a One-Time Password (OTP) for password recovery.
     *
     * <p>The OTP is valid for 10 minutes.</p>
     *
     * @param email User's email.
     * @param otp Generated OTP.
     */
    public void saveOtp(String email, String otp) {

        findByEmail(email).ifPresent(user -> {

            // Store generated OTP
            user.setOtp(otp);

            // Set OTP expiry time
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(10).toString());

            // Save updated user
            userRepository.save(user);

            // Log OTP generation
            System.out.println("[UserStore] OTP saved for: " + email);
        });
    }

    /**
     * Verifies whether the provided OTP is valid.
     *
     * <p>The OTP is considered valid only if:
     * <ul>
     *     <li>The user exists.</li>
     *     <li>The OTP matches.</li>
     *     <li>The OTP has not expired.</li>
     * </ul>
     *
     * @param email User's email.
     * @param otp OTP entered by the user.
     * @return true if OTP is valid, otherwise false.
     */
    public boolean verifyOtp(String email, String otp) {

        // Find user by email
        Optional<User> opt = findByEmail(email);

        // User does not exist
        if (opt.isEmpty()) return false;

        User user = opt.get();

        // Invalid or missing OTP
        if (user.getOtp() == null || !user.getOtp().equals(otp)) return false;

        // OTP expiry not available
        if (user.getOtpExpiry() == null) return false;

        // Convert expiry string to LocalDateTime
        LocalDateTime expiry = LocalDateTime.parse(user.getOtpExpiry());

        // OTP is valid only before expiry
        return LocalDateTime.now().isBefore(expiry);
    }

    /**
     * Resets the user's password after successful OTP verification.
     *
     * <p>After resetting:
     * <ul>
     *     <li>Password is updated.</li>
     *     <li>OTP is cleared.</li>
     *     <li>OTP expiry is removed.</li>
     * </ul>
     *
     * @param email User's email.
     * @param otp Verified OTP.
     * @param newPassword New password.
     * @return true if password reset succeeds,
     *         otherwise false.
     */
    public boolean resetPassword(String email, String otp, String newPassword) {

        // OTP verification failed
        if (!verifyOtp(email, otp)) return false;

        findByEmail(email).ifPresent(user -> {

            // Update password
            user.setPassword(newPassword);

            // Clear OTP after successful password reset
            user.setOtp(null);
            user.setOtpExpiry(null);

            // Save updated user
            userRepository.save(user);

            // Log password reset
            System.out.println("[UserStore] Password reset for: " + email);
        });

        return true;
    }
}