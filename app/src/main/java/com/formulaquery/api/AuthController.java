package com.formulaquery.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ------------------------------------------------------------
 * Authentication Controller
 * ------------------------------------------------------------
 * Handles all authentication-related API endpoints.
 *
 * Features:
 * - User Registration
 * - OTP Verification
 * - User Login
 * - Forgot Password
 * - Password Reset
 *
 * This controller acts as the API layer
 * between client requests and AuthService.
 * ------------------------------------------------------------
 */

@RestController
@RequestMapping("/api/auth/v2")
public class AuthController {

    /**
     * Authentication service layer
     * containing business logic
     */
    @Autowired
    private AuthService authService;

    // ------------------------------------------------------------
    // Register Endpoint
    // ------------------------------------------------------------

    /**
     * Registers a new user
     *
     * Endpoint:
     * POST /api/auth/register
     *
     * Required Fields:
     * - name
     * - email
     * - phone
     * - password
     *
     * @param body request payload
     * @return JSON response
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {

        String result = authService.register(
            body.get("name"),
            body.get("email"),
            body.get("phone"),
            body.get("password")
        );

        Map<String, Object> res = new HashMap<>();

        // Registration successful
        res.put("success", result.equals("OTP_SENT"));

        // Human-readable response messages
        res.put("message", switch (result) {

            case "OTP_SENT" ->
                "OTP sent to your email!";

            case "EMAIL_EXISTS" ->
                "Email already registered!";

            default ->
                "Registration failed!";
        });

        return res;
    }

    // ------------------------------------------------------------
    // Verify OTP Endpoint
    // ------------------------------------------------------------

    /**
     * Verifies user email OTP
     *
     * Endpoint:
     * POST /api/auth/verify
     *
     * Required Fields:
     * - email
     * - otp
     *
     * @param body request payload
     * @return verification response
     */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {

        String result = authService.verifyOtp(
            body.get("email"),
            body.get("otp")
        );

        Map<String, Object> res = new HashMap<>();

        // Verification success status
        res.put("success", result.equals("VERIFIED"));

        // Response messages
        res.put("message", switch (result) {

            case "VERIFIED" ->
                "Account verified!";

            case "INVALID_OTP" ->
                "Invalid OTP!";

            case "OTP_EXPIRED" ->
                "OTP expired!";

            case "USER_NOT_FOUND" ->
                "User not found!";

            default ->
                "Verification failed!";
        });

        return res;
    }

    // ------------------------------------------------------------
    // Login Endpoint
    // ------------------------------------------------------------

    /**
     * Authenticates user login
     *
     * Endpoint:
     * POST /api/auth/login
     *
     * Required Fields:
     * - email
     * - password
     *
     * @param body request payload
     * @return login response
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {

        String result = authService.login(
            body.get("email"),
            body.get("password")
        );

        Map<String, Object> res = new HashMap<>();

        // Successful login
        if (result.startsWith("SUCCESS:")) {

            res.put("success", true);

            // Extract username from result
            res.put("name", result.split(":")[1]);

            res.put("email", body.get("email"));

            res.put("message", "Login successful!");

        } else {

            // Login failed
            res.put("success", false);

            res.put("message", switch (result) {

                case "USER_NOT_FOUND" ->
                    "Email not registered!";

                case "NOT_VERIFIED" ->
                    "Please verify your email first!";

                case "WRONG_PASSWORD" ->
                    "Wrong password!";

                default ->
                    "Login failed!";
            });
        }

        return res;
    }

    // ------------------------------------------------------------
    // Forgot Password Endpoint
    // ------------------------------------------------------------

    /**
     * Sends password reset OTP
     *
     * Endpoint:
     * POST /api/auth/forgot
     *
     * Required Fields:
     * - email
     *
     * @param body request payload
     * @return password reset OTP status
     */
    @PostMapping("/forgot")
    public Map<String, Object> forgot(@RequestBody Map<String, String> body) {

        String result = authService.forgotPassword(
            body.get("email")
        );

        Map<String, Object> res = new HashMap<>();

        // OTP send status
        res.put("success", result.equals("OTP_SENT"));

        res.put("message", switch (result) {

            case "OTP_SENT" ->
                "OTP sent to your email!";

            case "USER_NOT_FOUND" ->
                "Email not registered!";

            default ->
                "Failed!";
        });

        return res;
    }

    // ------------------------------------------------------------
    // Reset Password Endpoint
    // ------------------------------------------------------------

    /**
     * Resets user password
     *
     * Endpoint:
     * POST /api/auth/reset
     *
     * Required Fields:
     * - email
     * - otp
     * - password
     *
     * @param body request payload
     * @return password reset response
     */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody Map<String, String> body) {

        String result = authService.resetPassword(
            body.get("email"),
            body.get("otp"),
            body.get("password")
        );

        Map<String, Object> res = new HashMap<>();

        // Password reset status
        res.put("success", result.equals("PASSWORD_RESET"));

        res.put("message", switch (result) {

            case "PASSWORD_RESET" ->
                "Password reset successful!";

            case "INVALID_OTP" ->
                "Invalid OTP!";

            case "OTP_EXPIRED" ->
                "OTP expired!";

            case "USER_NOT_FOUND" ->
                "User not found!";

            default ->
                "Reset failed!";
        });

        return res;
    }
}