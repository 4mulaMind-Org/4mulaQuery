package com.formulaquery.api;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

    @Autowired
    private FlexibleRecordRepository flexibleRecordRepository;

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

// =========================================================================
// QUERY LOGS
// =========================================================================

/*
logs()

Purpose:
Query execution logs aur performance statistics retrieve karna.

Flow:
1. Query logger obtain karo
2. Session logs fetch karo
3. Total queries count karo
4. Average execution time calculate karo
5. Success rate calculate karo
6. Response return karo
*/

@GetMapping("/logs")
public ResponseEntity<Map<String, Object>> logs() {
    QueryLogger logger = engineService.getQueryLogger();
    List<QueryLog> sessionLogs = logger.getSessionLogs();
    
    Map<String, Object> res = new HashMap<>();
    res.put("totalQueries", logger.getTotalLogs());
    res.put("logs", sessionLogs);
    
    // Avg exec time
    double avgTime = sessionLogs.stream()
        .mapToLong(QueryLog::getExecutionTimeMs)
        .average().orElse(0);
    res.put("avgExecTime", avgTime);
    
    // Success rate
    long successCount = sessionLogs.stream()
        .filter(QueryLog::isSuccess)
        .count();
    double successRate = sessionLogs.isEmpty() ? 0 :
        (successCount * 100.0) / sessionLogs.size();
    res.put("successRate", successRate);
    
    // Type counts
    Map<String, Long> typeCounts = sessionLogs.stream()
        .collect(java.util.stream.Collectors.groupingBy(
            log -> log.getType().toString(),
            java.util.stream.Collectors.counting()
        ));
    res.put("typeCounts", typeCounts);
    
    // Avg time per type
    Map<String, Double> avgTimePerType = sessionLogs.stream()
        .collect(java.util.stream.Collectors.groupingBy(
            log -> log.getType().toString(),
            java.util.stream.Collectors.averagingLong(QueryLog::getExecutionTimeMs)
        ));
    res.put("avgTimePerType", avgTimePerType);

    // Recent logs for timeline
    List<Map<String, Object>> recentLogs = sessionLogs.stream()
        .limit(20)
        .map(log -> {
            Map<String, Object> m = new HashMap<>();
            m.put("type", log.getType().toString());
            m.put("ms", log.getExecutionTimeMs());
            m.put("success", log.isSuccess());
            m.put("timestamp", log.getTimestamp());
            return m;
        })
        .collect(java.util.stream.Collectors.toList());
    res.put("recentLogs", recentLogs);
    
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

        Optional<User> user =
                userStore.login(email, password);

        if (user.isPresent()) {

            res.put("success", true);
            res.put("name", user.get().getName());
            res.put("email", user.get().getEmail());
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
    // CSV IMPORT
    // =========================================================================

    /**
     * Imports records from a CSV file into the database.
     *
     * <p><b>Endpoint:</b></p>
     * <pre>
     * POST /api/import/csv
     * </pre>
     *
     * <p><b>Content-Type:</b></p>
     * <pre>
     * multipart/form-data
     * </pre>
     *
     * <p><b>Request Parameter:</b></p>
     * <ul>
     *   <li><b>file</b> - CSV file containing user records.</li>
     * </ul>
     *
     * <p><b>Expected CSV Format:</b></p>
     * <pre>
     * name,email
     * John,john@gmail.com
     * Alice,alice@gmail.com
     * Bob,bob@gmail.com
     * </pre>
     *
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li>Read the uploaded CSV file.</li>
     *   <li>Skip the header row.</li>
     *   <li>Extract the <b>name</b> and <b>email</b> columns.</li>
     *   <li>Generate sequential IDs starting from 1.</li>
     *   <li>Insert each record using the database engine.</li>
     *   <li>Count successful and failed imports.</li>
     *   <li>Return the import summary.</li>
     * </ol>
     *
     * <p><b>Example Response:</b></p>
     * <pre>
     * {
     *   "success": true,
     *   "imported": 25,
     *   "failed": 1,
     *   "message": "25 rows imported!"
     * }
     * </pre>
     *
     * @param file Uploaded CSV file containing records.
     * @return A map containing:
     * <ul>
     *   <li><b>success</b> - Import status.</li>
     *   <li><b>imported</b> - Number of successfully imported records.</li>
     *   <li><b>failed</b> - Number of failed records.</li>
     *   <li><b>message</b> - Summary of the import operation.</li>
     * </ul>
     */

    @PostMapping("/import/csv")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()));
            
            String headerLine = reader.readLine(); // Skip header
            String[] headers = headerLine.split(",");
            
            int imported = 0;
            int failed = 0;
            String line;
            int id = (int)(System.currentTimeMillis() % 100000);
            
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 1) {
                    // First column = name, generate email from it
                    String name = values[0].trim();
                    String email = name.toLowerCase().replaceAll("\\s+", "") + "@data.csv";
                    String result = engineService.executeCommand(
                        "insert," + id + "," + name + "," + email);
                    if (result.contains("Error")) failed++;
                    else imported++;
                    id++;
                }
            }
            
            res.put("success", true);
            res.put("imported", imported);
            res.put("failed", failed);
            res.put("message", imported + " rows imported!");
            
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Error: " + e.getMessage());
        }
        return res;
    }

    // =========================================================================
    // FLEXIBLE DATASET IMPORT
    // =========================================================================

    /*
    importFlexible()

    Purpose:
    CSV file ko dynamic dataset ke roop me MongoDB me import karna.

    Flow:
    1. Dataset name file name se extract karo
    2. CSV headers read karo
    3. Existing dataset delete karo
    4. Dynamic fields prepare karo
    5. Records MongoDB me save karo
    6. Import summary return karo
    */
    @PostMapping("/import/flexible")
    public Map<String, Object> importFlexible(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        try {
            String fileName = file.getOriginalFilename()
                .replace(".csv", "");
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()));
            
            // Headers read karo
            String headerLine = reader.readLine();
            String[] headers = headerLine.split(",");
            
            // Purana data delete karo same dataset ka
            flexibleRecordRepository.deleteByDatasetName(fileName);
            
            int imported = 0;
            String line;
            
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> fields = new LinkedHashMap<>();
                
                for (int i = 0; i < headers.length; i++) {
                    String value = i < values.length ? values[i].trim() : "";
                    fields.put(headers[i].trim(), value);
                }
                
                flexibleRecordRepository.save(
                    new FlexibleRecord(fileName, fields));
                imported++;
            }
            
            res.put("success", true);
            res.put("imported", imported);
            res.put("dataset", fileName);
            res.put("columns", headers.length);
            res.put("message", imported + " rows imported!");
            
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Error: " + e.getMessage());
        }
        return res;
    }

    // =========================================================================
    // DATASET OPERATIONS
    // =========================================================================

    /*
    getDatasets()

    Purpose:
    Available datasets ki list retrieve karna.

    Flow:
    1. Sabhi records fetch karo
    2. Unique dataset names extract karo
    3. Dataset list return karo
    */
    @GetMapping("/datasets")
    public Map<String, Object> getDatasets() {
        Map<String, Object> res = new HashMap<>();
        // Distinct dataset names
        List<FlexibleRecord> all = flexibleRecordRepository.findAll();
        List<String> datasets = all.stream()
            .map(FlexibleRecord::getDatasetName)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        res.put("datasets", datasets);
        return res;
    }

    /*
    getDataset(name)

    Purpose:
    Selected dataset ke sabhi records retrieve karna.

    Flow:
    1. Dataset name receive karo
    2. Matching records fetch karo
    3. Dynamic field data extract karo
    4. Dataset details return karo
    */
   @GetMapping("/dataset/{name}")
    public Map<String, Object> getDataset(@PathVariable String name) {
        Map<String, Object> res = new HashMap<>();
        List<FlexibleRecord> records = 
            flexibleRecordRepository.findByDatasetName(name);
        res.put("dataset", name);
        res.put("total", records.size());
        res.put("records", records.stream()
            .map(FlexibleRecord::getFields)
            .collect(java.util.stream.Collectors.toList()));
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