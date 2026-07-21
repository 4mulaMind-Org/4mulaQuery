package com.formulaquery.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ApiController
 *
 * Ye class 4mulaQuery ke saare REST APIs handle karti hai.
 * Frontend se jo bhi request aati hai wo yahin se backend engine tak jaati hai.
 *
 * Main responsibilities:
 * 1. Database commands ko EngineService ko forward karna
 * 2. Query analytics provide karna
 * 3. User authentication handle karna
 *
 * Base URL: /api
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    /** Core database engine jo commands execute karta hai */
    @Autowired
    private EngineService engineService;

    /** User storage component (users.json file manage karta hai) */
    @Autowired
    private UserStore userStore;

    // ───────────────── DATABASE ENDPOINTS ─────────────────

    /**
     * Insert record into database engine
     *
     * Example:
     * /api/insert?id=1&name=John&email=john@mail.com
     */
    @GetMapping("/insert")
    public String insertData(
            @RequestParam int id,
            @RequestParam String name,
            @RequestParam String email) {

        return engineService.executeCommand(
            String.format("insert,%d,%s,%s", id, name, email));
    }

    /**
     * Get all records stored in engine
     */
    @GetMapping("/all")
    public String getAllData() {
        return engineService.executeCommand("all");
    }

    /**
     * Search record by ID
     */
    @GetMapping("/search")
    public String search(@RequestParam int id) {
        return engineService.executeCommand("search," + id);
    }

    /**
     * Delete record by ID
     */
    @GetMapping("/delete")
    public String deleteData(@RequestParam int id) {
        return engineService.executeCommand("delete," + id);
    }

    // ───────────────── ANALYTICS ENDPOINT ─────────────────

    /**
     * Query analytics return karta hai
     *
     * Response me milega:
     * - totalQueries
     * - successRate
     * - avgExecTime
     * - query type counts
     * - recent query logs
     */
    @GetMapping("/logs")
    public Map<String, Object> getLogs() {

        // Engine ke query logs fetch karo
        List<QueryLog> logs = engineService.getQueryLogger().getSessionLogs();

        // Query types ka count store karne ke liye map
        Map<String, Integer> typeCounts = new HashMap<>();
        typeCounts.put("INSERT", 0);
        typeCounts.put("SEARCH", 0);
        typeCounts.put("DELETE", 0);
        typeCounts.put("ALL", 0);

        long totalTime = 0;
        int successful = 0;

        // Logs iterate karke analytics calculate karo
        for (QueryLog log : logs) {
            String type = log.getType().toString();

            typeCounts.put(type,
                    typeCounts.getOrDefault(type, 0) + 1);

            totalTime += log.getExecutionTimeMs();

            if (log.isSuccess())
                successful++;
        }

        Map<String, Object> response = new HashMap<>();

        // Total queries executed
        response.put("totalQueries", logs.size());

        // Success percentage
        response.put("successRate",
                logs.isEmpty() ? 0 :
                (successful * 100.0 / logs.size()));

        // Average execution time
        response.put("avgExecTime",
                logs.isEmpty() ? 0 :
                (totalTime * 1.0 / logs.size()));

        response.put("typeCounts", typeCounts);

        // Sirf last 20 logs frontend ko bhejo
        List<QueryLog> recent = logs.size() > 20
                ? logs.subList(logs.size() - 20, logs.size())
                : logs;

        // Lightweight response banane ke liye mapping
        response.put("recentLogs",
                recent.stream().map(l -> {

                    Map<String, Object> m = new HashMap<>();

                    m.put("type", l.getType().toString());
                    m.put("ms", l.getExecutionTimeMs());
                    m.put("success", l.isSuccess());

                    return m;

                }).toList());

        return response;
    }

    // ───────────────── AUTH ENDPOINTS ─────────────────

    /**
     * User Registration API
     *
     * POST /api/auth/register
     *
     * Body:
     * {
     *   "name": "...",
     *   "email": "...",
     *   "password": "..."
     * }
     */
    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        // Validate input fields
        if (name == null || email == null || password == null) {
            res.put("success", false);
            res.put("message", "All fields required");
            return res;
        }

        // Check if email already exists
        if (userStore.exists(email)) {
            res.put("success", false);
            res.put("message", "Email already registered");
            return res;
        }

        // Save new user
        userStore.saveUser(email, name, password);

        res.put("success", true);
        res.put("name", name);
        res.put("email", email);
        res.put("message", "Account created!");

        return res;
    }

    /**
     * User Login API
     *
     * POST /api/auth/login
     *
     * Body:
     * {
     *   "email": "...",
     *   "password": "..."
     * }
     */
    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            res.put("success", false);
            res.put("message", "All fields required");
            return res;
        }

        // Find user
        Map<String, String> user = userStore.findByEmail(email);

        if (user == null) {
            res.put("success", false);
            res.put("message", "Account not found. Sign up first.");
            return res;
        }

        // Password check
        if (!user.get("password").equals(password)) {
            res.put("success", false);
            res.put("message", "Incorrect password.");
            return res;
        }

        res.put("success", true);
        res.put("name", user.get("name"));
        res.put("email", email);

        return res;
    }

    /**
     * User profile update API
     *
     * POST /api/auth/update
     *
     * Body:
     * {
     *   "email": "...",
     *   "name": "...",
     *   "password": "optional"
     * }
     */
    @PostMapping("/auth/update")
    public Map<String, Object> update(@RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String email = body.get("email");
        String name = body.get("name");
        String password = body.get("password");

        // Validation
        if (email == null || name == null) {
            res.put("success", false);
            res.put("message", "Name and email required");
            return res;
        }

        // User exist check
        if (!userStore.exists(email)) {
            res.put("success", false);
            res.put("message", "User not found");
            return res;
        }

        // Update user
        userStore.updateUser(email, name, password);

        res.put("success", true);
        res.put("name", name);
        res.put("email", email);
        res.put("message", "Updated successfully!");

        return res;
    }
}