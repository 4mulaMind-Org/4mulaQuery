package com.formulaquery.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ============================================================================
 *                              UserStore
 * ============================================================================
 *
 * UserStore is a lightweight file-based repository responsible for managing
 * user accounts in the FormulaQuery application.
 *
 * Features:
 * ---------------------------------------------------------------------------
 * • User Registration
 * • User Login Authentication
 * • User Search by Email
 * • OTP Storage
 * • OTP Verification
 * • Password Reset
 * • Persistent JSON Storage
 *
 * Data Storage:
 * ---------------------------------------------------------------------------
 * All user information is stored inside:
 *
 *      data/users.json
 *
 * using the Jackson ObjectMapper library.
 *
 * This class is registered as a Spring Component so that it can be injected
 * into controllers and services.
 *
 * NOTE:
 * ---------------------------------------------------------------------------
 * Passwords are currently stored in plain text for demonstration purposes.
 * In a production application, passwords should always be encrypted using
 * BCryptPasswordEncoder or another secure hashing algorithm.
 *
 * Author  : FormulaMind
 * Project : FormulaQuery
 * ============================================================================
 */
@Component
public class UserStore {

    /**
     * Location of the JSON file used for storing user information.
     */
    private static final String FILE_PATH = "data/users.json";

    /**
     * Jackson ObjectMapper used to convert Java objects
     * to JSON and JSON back to Java objects.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * ========================================================================
     *                              User Model
     * ========================================================================
     *
     * Represents a registered user.
     *
     * Fields:
     * ------------------------------------------------------------------------
     * name       -> User's full name
     * email      -> Unique email address
     * password   -> Login password
     * otp        -> Latest generated OTP
     * otpExpiry  -> Expiration time of OTP
     */
    public static class User {

        /** User's full name */
        public String name;

        /** User's email address */
        public String email;

        /** User's password */
        public String password;

        /** One Time Password used for password reset */
        public String otp;

        /** Expiration time of OTP */
        public String otpExpiry;

        /**
         * Default constructor.
         *
         * Required by Jackson during JSON deserialization.
         */
        public User() {
        }

        /**
         * Parameterized constructor.
         *
         * @param name User name
         * @param email User email
         * @param password User password
         */
        public User(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }

    /**
     * =========================================================================
     * Load All Users
     * =========================================================================
     *
     * Reads every registered user from users.json.
     *
     * If the JSON file does not exist,
     * an empty list is returned.
     *
     * If any IOException occurs,
     * an empty list is returned.
     *
     * @return List<User>
     */
    @SuppressWarnings("unchecked")
    private List<User> loadUsers() {
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) return new ArrayList<>();
            String content = new String(Files.readAllBytes(file.toPath()));
            if (content.trim().equals("{}") || content.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file,
                mapper.getTypeFactory().constructCollectionType(List.class, User.class));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * =========================================================================
     * Save Users
     * =========================================================================
     *
     * Saves the complete user list into users.json.
     *
     * The directory is created automatically if missing.
     *
     * @param users Updated list of users.
     */
    private void saveUsers(List<User> users) {

        try {

            File file = new File(FILE_PATH);

            file.getParentFile().mkdirs();

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, users);

        } catch (IOException e) {

            System.err.println("[UserStore] Save failed : " + e.getMessage());

        }
    }

    /**
     * =========================================================================
     * Find User by Email
     * =========================================================================
     *
     * Searches for a user using email.
     *
     * Email comparison is case-insensitive.
     *
     * @param email User email.
     * @return Optional<User>
     */
    public Optional<User> findByEmail(String email) {

        return loadUsers()

                .stream()

                .filter(user -> user.email.equalsIgnoreCase(email))

                .findFirst();
    }

    /**
     * =========================================================================
     * Check Email Exists
     * =========================================================================
     *
     * Determines whether a user already exists.
     *
     * @param email Email to search.
     * @return true if email already exists.
     */
    public boolean existsByEmail(String email) {

        return findByEmail(email).isPresent();

    }

    /**
     * =========================================================================
     * Register User
     * =========================================================================
     *
     * Registration Process:
     *
     * 1. Check duplicate email.
     * 2. Load users.
     * 3. Add new user.
     * 4. Save updated list.
     *
     * @param name User name.
     * @param email User email.
     * @param password User password.
     * @return true if registration succeeds.
     */
    public boolean register(String name, 
            String email, 
            String password) {
        if (existsByEmail(email)) return false;
        List<User> users = loadUsers();
        users.add(new User(name, email, password));
        saveUsers(users);
        System.out.println("[UserStore] Registered : " + email);
        return true;
    }

    /**
     * =========================================================================
     * Login User
     * =========================================================================
     *
     * Authenticates user credentials.
     *
     * Login succeeds only if:
     *
     * • Email exists
     * • Password matches
     *
     * @param email User email.
     * @param password User password.
     * @return Optional<User>
     */
    public Optional<User> login(String email,
                                String password) {

        return findByEmail(email)

                .filter(user -> user.password.equals(password));

    }

    /**
     * =========================================================================
     * Save OTP
     * =========================================================================
     *
     * Stores a generated OTP for password recovery.
     *
     * OTP validity:
     *
     *      10 Minutes
     *
     * @param email User email.
     * @param otp Generated OTP.
     */
    public void saveOtp(String email,
                        String otp) {

        List<User> users = loadUsers();

        for (User user : users) {

            if (user.email.equalsIgnoreCase(email)) {

                user.otp = otp;

                user.otpExpiry = LocalDateTime.now()

                        .plusMinutes(10)

                        .toString();

                break;
            }
        }

        saveUsers(users);

        System.out.println("[UserStore] OTP saved : " + email);

    }

    /**
     * =========================================================================
     * Verify OTP
     * =========================================================================
     *
     * Checks:
     *
     * 1. User exists
     * 2. OTP matches
     * 3. OTP not expired
     *
     * @param email User email.
     * @param otp Entered OTP.
     * @return true if OTP is valid.
     */
    public boolean verifyOtp(String email,
                             String otp) {

        Optional<User> optionalUser = findByEmail(email);

        if (optionalUser.isEmpty()) {

            return false;

        }

        User user = optionalUser.get();

        if (user.otp == null) {

            return false;

        }

        if (!user.otp.equals(otp)) {

            return false;

        }

        if (user.otpExpiry == null) {

            return false;

        }

        LocalDateTime expiry = LocalDateTime.parse(user.otpExpiry);

        return LocalDateTime.now().isBefore(expiry);

    }

    /**
     * =========================================================================
     * Reset Password
     * =========================================================================
     *
     * Steps:
     *
     * 1. Verify OTP
     * 2. Update password
     * 3. Remove OTP
     * 4. Remove expiry time
     * 5. Save user list
     *
     * @param email User email.
     * @param otp User OTP.
     * @param newPassword New password.
     * @return true if password updated.
     */
    public boolean resetPassword(String email,
                                 String otp,
                                 String newPassword) {

        if (!verifyOtp(email, otp)) {

            return false;

        }

        List<User> users = loadUsers();

        for (User user : users) {

            if (user.email.equalsIgnoreCase(email)) {

                user.password = newPassword;

                user.otp = null;

                user.otpExpiry = null;

                break;
            }
        }

        saveUsers(users);

        System.out.println("[UserStore] Password reset : " + email);

        return true;

    }

    /**
     * =========================================================================
     * Constructor
     * =========================================================================
     *
     * Initializes storage during application startup.
     *
     * If users.json does not exist:
     *
     * • Creates data directory.
     * • Creates empty users.json.
     */
    public UserStore() {

        File file = new File(FILE_PATH);

        if (!file.exists()) {

            file.getParentFile().mkdirs();

            saveUsers(new ArrayList<>());

            System.out.println("[UserStore] Created : " + FILE_PATH);

        }
    }

}