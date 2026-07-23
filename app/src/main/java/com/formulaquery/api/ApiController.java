package com.formulaquery.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ============================================================================
 *                              ApiController
 * ============================================================================
 *
 * ApiController is the primary REST controller of the FormulaQuery backend.
 *
 * It exposes HTTP APIs for:
 *
 * ---------------------------------------------------------------------------
 * DATABASE OPERATIONS
 * ---------------------------------------------------------------------------
 * • Insert Record
 * • Search Record
 * • Delete Record
 * • Display All Records
 * • Retrieve Engine Logs
 *
 * ---------------------------------------------------------------------------
 * USER AUTHENTICATION
 * ---------------------------------------------------------------------------
 * • Register
 * • Login
 * • Forgot Password
 * • Reset Password
 * • OTP Email Service
 *
 * The controller communicates with:
 *
 * • EngineService  -> Database Engine
 * • UserStore      -> User Management
 * • JavaMailSender -> Email Service
 *
 * Base URL
 * ---------------------------------------------------------------------------
 *      /api
 *
 * Example:
 *
 *      GET  /api/search?id=10
 *      POST /api/auth/login
 *
 * Author  : FormulaMind
 * Project : FormulaQuery
 * ============================================================================
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    /**
     * Core database engine responsible
     * for insert, search, delete and logs.
     */
    @Autowired
    private EngineService engineService;

    /**
     * File-based user repository.
     */
    @Autowired
    private UserStore userStore;

    /**
     * Spring Mail Sender used
     * for sending OTP emails.
     */
    @Autowired
    private JavaMailSender mailSender;

    // =========================================================================
    // DATABASE OPERATIONS
    // =========================================================================

    /**
     * Inserts a new record into the database.
     *
     * Endpoint:
     *      GET /api/insert
     *
     * Parameters:
     *      id
     *      name
     *      email
     *
     * Example:
     *
     * /api/insert?id=1&name=John&email=john@gmail.com
     *
     * @param id Record ID
     * @param name Person name
     * @param email Person email
     * @return Success or failure message.
     */
    @GetMapping("/insert")
public ResponseEntity<String> insert(
        @RequestParam int id,
        @RequestParam String name,
        @RequestParam String email) {
    String result = engineService.executeCommand("insert," + id + "," + name + "," + email);
    return ResponseEntity.ok(result);
}

@GetMapping("/search")
public ResponseEntity<String> search(@RequestParam int id) {
    String result = engineService.executeCommand("search," + id);
    return ResponseEntity.ok(result);
}

@GetMapping("/delete")
public ResponseEntity<String> delete(@RequestParam int id) {
    String result = engineService.executeCommand("delete," + id);
    return ResponseEntity.ok(result);
}

@GetMapping("/all")
public ResponseEntity<String> all() {
    String result = engineService.executeCommand("all");
    return ResponseEntity.ok(result);
}

@GetMapping("/logs")
public ResponseEntity<Map<String, Object>> logs() {
    QueryLogger logger = engineService.getQueryLogger();
    Map<String, Object> res = new HashMap<>();
    res.put("logs", logger.getSessionLogs());
    res.put("totalQueries", logger.getTotalLogs());
    return ResponseEntity.ok(res);
}

    // =========================================================================
    // USER REGISTRATION
    // =========================================================================

    /**
     * Registers a new user.
     *
     * Endpoint:
     *
     *      POST /api/auth/register
     *
     * Request Body:
     *
     * {
     *      "name":"John",
     *      "email":"john@gmail.com",
     *      "password":"123456"
     * }
     *
     * Registration Steps:
     *
     * • Check duplicate email.
     * • Store user.
     * • Return JSON response.
     *
     * @param body Request body.
     * @return Registration status.
     */
    @PostMapping("/auth/register")
    public Map<String, Object> register(
            @RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        if (userStore.existsByEmail(email)) {

            res.put("success", false);
            res.put("message", "Email already registered!");

            return res;

        }

        userStore.register(name, email, password);

        res.put("success", true);
        res.put("message", "Account created!");

        return res;

    }

    // =========================================================================
    // USER LOGIN
    // =========================================================================

    /**
     * Authenticates user credentials.
     *
     * Endpoint:
     *
     *      POST /api/auth/login
     *
     * Request Body:
     *
     * {
     *      "email":"john@gmail.com",
     *      "password":"123456"
     * }
     *
     * @param body Login information.
     * @return Login response.
     */
    @PostMapping("/auth/login")
    public Map<String, Object> login(
            @RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String email = body.get("email");
        String password = body.get("password");

        Optional<UserStore.User> user =
                userStore.login(email, password);

        if (user.isPresent()) {

            res.put("success", true);
            res.put("name", user.get().name);
            res.put("email", user.get().email);
            res.put("message", "Login successful!");

        } else {

            res.put("success", false);
            res.put("message", "Invalid email or password!");

        }

        return res;

    }

    // =========================================================================
    // FORGOT PASSWORD
    // =========================================================================

    /**
     * Generates an OTP and sends it to
     * the user's registered email.
     *
     * Endpoint:
     *
     *      POST /api/auth/forgot
     *
     * Request Body:
     *
     * {
     *      "email":"john@gmail.com"
     * }
     *
     * Process:
     *
     * • Verify email
     * • Generate OTP
     * • Save OTP
     * • Send Email
     *
     * @param body Email information.
     * @return Status message.
     */
    @PostMapping("/auth/forgot")
    public Map<String, Object> forgotPassword(
            @RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String email = body.get("email");

        if (!userStore.existsByEmail(email)) {

            res.put("success", false);
            res.put("message", "Email not registered!");

            return res;

        }

        String otp = String.format("%06d",
                new Random().nextInt(999999));

        userStore.saveOtp(email, otp);

        sendOtpEmail(email, otp);

        res.put("success", true);
        res.put("message", "OTP sent to your email!");

        return res;

    }

    // =========================================================================
    // RESET PASSWORD
    // =========================================================================

    /**
     * Resets user password after OTP verification.
     *
     * Endpoint:
     *
     *      POST /api/auth/reset
     *
     * Request Body:
     *
     * {
     *      "email":"john@gmail.com",
     *      "otp":"123456",
     *      "password":"newPassword"
     * }
     *
     * @param body Reset request.
     * @return Reset status.
     */
    @PostMapping("/auth/reset")
    public Map<String, Object> resetPassword(
            @RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        boolean ok = userStore.resetPassword(

                body.get("email"),

                body.get("otp"),

                body.get("password")

        );

        res.put("success", ok);

        res.put("message",

                ok
                        ? "Password reset successfully!"
                        : "Invalid or expired OTP!");

        return res;

    }

    // =========================================================================
    // EMAIL SERVICE
    // =========================================================================

    /**
     * Sends the generated OTP
     * to the user's email address.
     *
     * Email contains:
     *
     * • OTP Code
     * • Expiry Time
     * • Application Name
     *
     * @param to Recipient email.
     * @param otp Generated OTP.
     */
    private void sendOtpEmail(String to,
                              String otp) {

        try {

            SimpleMailMessage message =
                    new SimpleMailMessage();

            message.setTo(to);

            message.setSubject(
                    "4mulaQuery — Password Reset OTP");

            message.setText(

                    "Your OTP is : " + otp +

                    "\n\nThis OTP is valid for 10 minutes."

                    + "\n\nDo not share this OTP with anyone."

                    + "\n\nRegards,"

                    + "\n4mulaQuery"

                    + "\nIntelligent Database Engine"

            );

            mailSender.send(message);

            System.out.println(
                    "[Mail] OTP sent to : " + to);

        }

        catch (Exception e) {

            System.err.println(

                    "[Mail] Failed : "

                            + e.getMessage()

            );

        }

    }

}