package com.formulaquery.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * QueryLog — Represents a single query execution record.
 * 
 * Responsibilities:
 * 1. Holds query details: command, type, result, execution time, timestamp, success.
 * 2. Converts query data into CSV format for logging.
 * 3. Provides utility getters for analytics or ML optimization.
 * 
 * CSV Format:
 * timestamp,type,executionTimeMs,success,command
 * 
 * Example:
 * 2026-03-28 22:15:01,INSERT,45,true,"insert,1,name,email"
 */
public class QueryLog {

    private final String rawCommand;
    private final CommandType type;
    private final String result;
    private final long executionTimeMs;
    private final String timestamp;
    private final boolean success;

    /**
     * Constructs a QueryLog object after query execution.
     * Determines success based on result content.
     *
     * @param rawCommand Original CSV command string executed
     * @param result Output from the engine
     * @param executionTimeMs Execution time in milliseconds
     */
    public QueryLog(String rawCommand, String result, long executionTimeMs) {
        this.rawCommand      = rawCommand;
        this.type            = CommandType.from(rawCommand);
        this.result          = result;
        this.executionTimeMs = executionTimeMs;
        this.timestamp       = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.success         = !result.contains("Error") && !result.contains("Bridge Error");
    }

    // ── Getters ──────────────────────────────────────────────
    public String getRawCommand() { return rawCommand; }
    public CommandType getType() { return type; }
    public String getResult() { return result; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getTimestamp() { return timestamp; }
    public boolean isSuccess() { return success; }

    /**
     * Converts this log to CSV format for file storage.
     * Replaces any double quotes in command to single quotes.
     *
     * @return CSV-formatted string
     */
    public String toCsv() {
        return String.format("%s,%s,%d,%s,\"%s\"",
            timestamp,
            type.name(),
            executionTimeMs,
            success,
            rawCommand.replace("\"", "'")
        );
    }

    /**
     * Returns a concise string representation of the log.
     *
     * @return String with timestamp, command type, execution time, and success
     */
    @Override
    public String toString() {
        return String.format("[%s] %s | %dms | success=%s",
            timestamp, type.name(), executionTimeMs, success);
    }
}