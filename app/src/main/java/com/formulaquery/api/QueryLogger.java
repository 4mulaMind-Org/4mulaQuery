package com.formulaquery.api;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * QueryLogger — Collects and stores query execution logs for ML.
 * 
 * Responsibilities:
 * 1. Saves each query execution to CSV (data/query_logs.csv)
 * 2. Maintains session logs in memory
 * 3. Provides query statistics and filtering
 * 
 * CSV Format:
 * timestamp,type,execution_ms,success,command
 */
public class QueryLogger {

    private static final String LOG_FILE = "data/query_logs.csv";
    private static final String CSV_HEADER = "timestamp,type,execution_ms,success,command";

    private final List<QueryLog> sessionLogs = new ArrayList<>();

    public QueryLogger() { initLogFile(); }

    /** Initializes log file with header if not exists. */
    private void initLogFile() {
        try {
            Path path = Paths.get(LOG_FILE);
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                    writer.write(CSV_HEADER); writer.newLine();
                }
                System.out.println("[QueryLogger] Log file created: " + LOG_FILE);
            }
        } catch (IOException e) {
            System.err.println("[QueryLogger] Init error: " + e.getMessage());
        }
    }

    /** Logs a query in memory and file. */
    public void log(QueryLog log) {
        sessionLogs.add(log);
        appendToFile(log);
        System.out.println("[QueryLogger] " + log);
    }

    /** Appends a query log to CSV file. */
    private void appendToFile(QueryLog log) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(log.toCsv()); writer.newLine();
        } catch (IOException e) {
            System.err.println("[QueryLogger] Write error: " + e.getMessage());
        }
    }

    /** Returns all session logs. */
    public List<QueryLog> getSessionLogs() {
        return new ArrayList<>(sessionLogs);
    }

    /** Returns logs filtered by CommandType. */
    public List<QueryLog> getLogsByType(CommandType type) {
        return sessionLogs.stream().filter(log -> log.getType() == type).toList();
    }

    /** Returns average execution time in milliseconds. */
    public double getAverageExecutionTime() {
        if (sessionLogs.isEmpty()) return 0;
        return sessionLogs.stream().mapToLong(QueryLog::getExecutionTimeMs).average().orElse(0);
    }

    /** Returns total number of logs in this session. */
    public int getTotalLogs() { return sessionLogs.size(); }
}