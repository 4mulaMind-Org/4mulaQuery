package com.formulaquery.api;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stores connection details for an external database (PostgreSQL, and
 * later Cassandra) that the user has registered with 4mulaQuery.
 *
 * The password is stored ENCRYPTED (see CredentialEncryptionService) —
 * never in plain text.
 */
@Document(collection = "db_connections")
public class DatabaseConnectionConfig {

    @Id
    private String id;

    private String name;        // user-friendly label, e.g. "My Postgres Dev DB"
    private String type;        // "POSTGRESQL" (CASSANDRA coming later)
    private String host;
    private int port;
    private String database;    // database name (Postgres) or keyspace (Cassandra, later)
    private String username;
    private String encryptedPassword;
    private long createdAt;

    public DatabaseConnectionConfig() {}

    public DatabaseConnectionConfig(String name, String type, String host, int port,
                                    String database, String username, String encryptedPassword) {
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}