package com.formulaquery.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ============================================================================
 *                          ExternalDbController
 * ============================================================================
 *
 * Handles connecting 4mulaQuery to EXTERNAL databases that the user owns
 * elsewhere — separate from 4mulaQuery's own C++ engine and MongoDB storage.
 *
 * Currently supports: PostgreSQL, via a from-scratch wire-protocol client
 * (PostgresWireClient / PostgresConnectorService) — no JDBC, no driver
 * library. Cassandra support is being built next (its native protocol is
 * a separate, more complex undertaking) and is rejected with a clear
 * "not yet" message until it lands.
 *
 * SAFETY: All queries run through here are restricted to SELECT only —
 * this is an exploration tool over someone else's database, never a place
 * to mutate it.
 *
 * Base URL: /api/connections
 * ============================================================================
 */
@RestController
@RequestMapping("/api/connections")
public class ExternalDbController {

    @Autowired
    private DatabaseConnectionRepository connectionRepository;

    @Autowired
    private CredentialEncryptionService encryptionService;

    @Autowired
    private PostgresConnectorService postgresConnectorService;

    private static final Set<String> SUPPORTED_TYPES = Set.of("POSTGRESQL");

    // =========================================================================
    // REGISTER A NEW CONNECTION
    // =========================================================================

    /*
    addConnection(body)

    Purpose:
    Ek naya external database connection register karna. Password
    encrypt karke MongoDB me save hota hai, kabhi plain text me nahi.
    Connection ko save karne se pehle test kiya jaata hai — agar
    connect na ho paaye, kuch bhi save nahi hota.

    Endpoint:
         POST /api/connections

    Request Body:
    {
         "name": "My Postgres Dev DB",
         "type": "POSTGRESQL",
         "host": "localhost",
         "port": 5432,
         "database": "mydb",
         "username": "postgres",
         "password": "secret"
    }
    */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addConnection(@RequestBody Map<String, Object> body) {

        Map<String, Object> res = new HashMap<>();

        try {
            String name = (String) body.get("name");
            String type = String.valueOf(body.get("type")).toUpperCase();
            String host = (String) body.get("host");
            int port = ((Number) body.get("port")).intValue();
            String database = (String) body.get("database");
            String username = (String) body.get("username");
            String password = (String) body.get("password");

            if ("CASSANDRA".equals(type)) {
                res.put("success", false);
                res.put("message", "Cassandra support is coming in the next phase — PostgreSQL only for now.");
                return ResponseEntity.badRequest().body(res);
            }

            if (!SUPPORTED_TYPES.contains(type)) {
                res.put("success", false);
                res.put("message", "type must be POSTGRESQL");
                return ResponseEntity.badRequest().body(res);
            }

            // Test the connection before saving — fail fast with a clear message
            // rather than silently storing a broken connection.
            DatabaseConnectionConfig testConfig = new DatabaseConnectionConfig(
                    name, type, host, port, database, username, null);

            postgresConnectorService.testConnection(testConfig, password);

            String encryptedPassword = encryptionService.encrypt(password);

            DatabaseConnectionConfig config = new DatabaseConnectionConfig(
                    name, type, host, port, database, username, encryptedPassword);

            DatabaseConnectionConfig saved = connectionRepository.save(config);

            res.put("success", true);
            res.put("id", saved.getId());
            res.put("message", "Connection added and verified successfully!");

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Could not connect: " + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    // =========================================================================
    // LIST SAVED CONNECTIONS
    // =========================================================================

    /*
    listConnections()

    Purpose:
    Saare saved connections ki list dena — passwords kabhi expose nahi
    hote, sirf metadata (name, type, host, database) dikhta hai.
    */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listConnections() {

        List<Map<String, Object>> result = new ArrayList<>();

        for (DatabaseConnectionConfig c : connectionRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("host", c.getHost());
            m.put("port", c.getPort());
            m.put("database", c.getDatabase());
            m.put("username", c.getUsername());
            // encryptedPassword intentionally omitted from the response
            result.add(m);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // DELETE A CONNECTION
    // =========================================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConnection(@PathVariable String id) {
        Map<String, Object> res = new HashMap<>();
        connectionRepository.deleteById(id);
        res.put("success", true);
        res.put("message", "Connection removed.");
        return ResponseEntity.ok(res);
    }

    // =========================================================================
    // LIST TABLES FOR A CONNECTION
    // =========================================================================

    /*
    listTables(id)

    Purpose:
    Ek registered connection ke database ke andar available tables
    ki list lana.
    */
    @GetMapping("/{id}/tables")
    public ResponseEntity<Map<String, Object>> listTables(@PathVariable String id) {

        Map<String, Object> res = new HashMap<>();

        Optional<DatabaseConnectionConfig> maybeConfig = connectionRepository.findById(id);
        if (maybeConfig.isEmpty()) {
            res.put("success", false);
            res.put("message", "Connection not found.");
            return ResponseEntity.badRequest().body(res);
        }

        DatabaseConnectionConfig config = maybeConfig.get();

        try {
            String password = encryptionService.decrypt(config.getEncryptedPassword());
            List<String> tables = postgresConnectorService.listTables(config, password);

            res.put("success", true);
            res.put("tables", tables);
            return ResponseEntity.ok(res);

        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Failed to list tables: " + e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    // =========================================================================
    // RUN A READ-ONLY QUERY
    // =========================================================================

    /*
    runQuery(id, body)

    Purpose:
    Ek registered connection ke upar raw SQL query chalana. SIRF SELECT
    queries allowed hain — koi bhi destructive operation (INSERT/UPDATE/
    DELETE/DROP) reject ho jaata hai.

    Endpoint:
         POST /api/connections/{id}/query

    Request Body:
    {
         "query": "SELECT * FROM users LIMIT 20"
    }
    */
    @PostMapping("/{id}/query")
    public ResponseEntity<Map<String, Object>> runQuery(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        Map<String, Object> res = new HashMap<>();

        String query = body.get("query");
        if (query == null || query.trim().isEmpty()) {
            res.put("success", false);
            res.put("message", "query field is required");
            return ResponseEntity.badRequest().body(res);
        }

        Optional<DatabaseConnectionConfig> maybeConfig = connectionRepository.findById(id);
        if (maybeConfig.isEmpty()) {
            res.put("success", false);
            res.put("message", "Connection not found.");
            return ResponseEntity.badRequest().body(res);
        }

        DatabaseConnectionConfig config = maybeConfig.get();

        try {
            String password = encryptionService.decrypt(config.getEncryptedPassword());
            List<Map<String, Object>> rows = postgresConnectorService.runQuery(config, password, query);

            res.put("success", true);
            res.put("total", rows.size());
            res.put("rows", rows);
            return ResponseEntity.ok(res);

        } catch (IllegalArgumentException e) {
            // e.g. non-SELECT query blocked
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);

        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Query failed: " + e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }
}