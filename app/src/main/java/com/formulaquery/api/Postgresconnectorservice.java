package com.formulaquery.api;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Connects to an external PostgreSQL database using PostgresWireClient
 * (our own from-scratch implementation of the Postgres wire protocol —
 * no JDBC, no driver library) and lets the user list tables and run
 * READ-ONLY (SELECT) queries.
 *
 * Never allows write operations — this is an exploration tool over
 * someone else's database, not a place to mutate it.
 */
@Service
public class PostgresConnectorService {

    public void testConnection(DatabaseConnectionConfig config, String password) throws IOException {
        try (PostgresWireClient client = connect(config, password)) {
            // connection + handshake succeeded if we get here
        }
    }

    public List<String> listTables(DatabaseConnectionConfig config, String password) throws IOException {
        try (PostgresWireClient client = connect(config, password)) {
            List<Map<String, Object>> rows = client.executeQuery(
                    "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = 'public'"
            );
            return rows.stream()
                    .map(r -> String.valueOf(r.get("tablename")))
                    .toList();
        }
    }

    /**
     * Executes a SELECT-only query. Throws IllegalArgumentException for
     * anything else (INSERT/UPDATE/DELETE/DROP/ALTER/etc).
     */
    public List<Map<String, Object>> runQuery(DatabaseConnectionConfig config, String password, String sql) throws IOException {

        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed for safety.");
        }

        try (PostgresWireClient client = connect(config, password)) {
            return client.executeQuery(sql);
        }
    }

    private PostgresWireClient connect(DatabaseConnectionConfig config, String password) throws IOException {
        return PostgresWireClient.connect(
                config.getHost(),
                config.getPort(),
                config.getDatabase(),
                config.getUsername(),
                password
        );
    }
}