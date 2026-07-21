package com.formulaquery.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ============================================================
 * UserStore — Persistent User Storage Component
 * ============================================================
 *
 * This component acts as a lightweight user database for the
 * 4mulaQuery application.
 *
 * Instead of using a traditional database (MySQL/PostgreSQL),
 * user records are stored in a JSON file:
 *
 *      data/users.json
 *
 * Responsibilities:
 *
 * • Persist user accounts
 * • Retrieve users by email
 * • Update user profiles
 * • Check user existence
 *
 * Storage Strategy:
 *
 * The JSON file stores data in the following structure:
 *
 * {
 *   "email@example.com": {
 *       "name": "User Name",
 *       "email": "email@example.com",
 *       "password": "password"
 *   }
 * }
 *
 * Advantages of this approach:
 *
 * • Extremely lightweight
 * • No external database dependency
 * • Easy to inspect and debug
 * • Works across server restarts
 *
 * This component is registered as a Spring Bean using
 * the @Component annotation and is injected into the
 * API controller where user operations are required.
 *
 * ============================================================
 */
@Component
public class UserStore {

    /**
     * File path used to persist user data.
     */
    private static final String FILE = "data/users.json";

    /**
     * Jackson ObjectMapper used for JSON serialization
     * and deserialization of user data.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructor responsible for initializing
     * the persistent storage file.
     *
     * Startup Process:
     *
     * 1. Ensure the data directory exists
     * 2. Create users.json if it does not exist
     * 3. Initialize the file with an empty JSON object
     *
     * This guarantees that the application always
     * has a valid storage file available.
     */
    public UserStore() {
        try {
            Path path = Paths.get(FILE);

            /**
             * Create directory structure if missing.
             */
            Files.createDirectories(path.getParent());

            /**
             * Create users.json file if it does not exist.
             */
            if (!Files.exists(path)) {
                Files.writeString(path, "{}");
                System.out.println("[UserStore] Created: " + FILE);
            }
        } catch (IOException e) {
            System.err.println("[UserStore] Init error: " + e.getMessage());
        }
    }

    /**
     * Load all users from the JSON storage file.
     *
     * Returns:
     * Map structure where:
     *
     * key   → email
     * value → user information map
     *
     * Example:
     *
     * {
     *   "user@email.com" → {
     *       name,
     *       email,
     *       password
     *   }
     * }
     *
     * If the file cannot be read, an empty map is returned
     * to prevent application crashes.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, String>> loadAll() {
        try {
            return mapper.readValue(new File(FILE), HashMap.class);
        } catch (IOException e) {
            System.err.println("[UserStore] Read error: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Persist all users back to the JSON file.
     *
     * This method serializes the user map and writes it
     * to the storage file using pretty-printed formatting
     * for easier debugging and readability.
     */
    public void saveAll(Map<String, Map<String, String>> users) {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(new File(FILE), users);
        } catch (IOException e) {
            System.err.println("[UserStore] Write error: " + e.getMessage());
        }
    }

    /**
     * Retrieve a user by email.
     *
     * Parameters:
     *      email → unique identifier for user
     *
     * Returns:
     *      Map containing user data
     *
     * If no user is found, returns null.
     */
    public Map<String, String> findByEmail(String email) {
        return loadAll().get(email);
    }

    /**
     * Save a new user into the storage.
     *
     * Parameters:
     *      email    → user email (primary key)
     *      name     → user name
     *      password → account password
     *
     * The method:
     * 1. Loads existing users
     * 2. Creates a new user record
     * 3. Adds it to the user map
     * 4. Persists the updated data
     */
    public void saveUser(String email, String name, String password) {
        Map<String, Map<String, String>> users = loadAll();

        Map<String, String> user = new HashMap<>();
        user.put("name",     name);
        user.put("email",    email);
        user.put("password", password);

        users.put(email, user);
        saveAll(users);
    }

    /**
     * Update an existing user profile.
     *
     * Parameters:
     *      email    → user identifier
     *      name     → new name
     *      password → optional new password
     *
     * Behavior:
     * • Updates the name
     * • Updates password only if provided
     */
    public void updateUser(String email, String name, String password) {
        Map<String, Map<String, String>> users = loadAll();

        if (!users.containsKey(email))
            return;

        users.get(email).put("name", name);

        if (password != null && !password.isEmpty())
            users.get(email).put("password", password);

        saveAll(users);
    }

    /**
     * Check if a user already exists in the storage.
     *
     * Parameters:
     *      email → user email
     *
     * Returns:
     *      true  → user exists
     *      false → user not found
     */
    public boolean exists(String email) {
        return loadAll().containsKey(email);
    }
}