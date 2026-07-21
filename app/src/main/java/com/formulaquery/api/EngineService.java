package com.formulaquery.api;

/**
 * EngineService — Main orchestrator for 4mulaQuery engine.
 * Coordinates command execution via C++ engine and logs queries for ML.
 *
 * Components:
 *  • ProcessManager — handles C++ process lifecycle
 *  • StreamHandler  — handles stdin/stdout communication
 *  • QueryLogger    — logs query and execution time
 *
 * OOP Principles:
 *  • Single Responsibility — each class has one responsibility
 *  • Composition          — uses injected components
 *  • Encapsulation        — internal details are hidden
 */
public class EngineService {

    private final ProcessManager processManager;
    private final StreamHandler  streamHandler;
    private final QueryLogger    queryLogger;

    /**
     * Initializes EngineService with path to C++ engine.
     *
     * @param enginePath Path to C++ binary (e.g., "./core/4mulaQuery")
     */
    public EngineService(String enginePath) {
        this.processManager = new ProcessManager(enginePath);
        this.streamHandler  = new StreamHandler();
        this.queryLogger    = new QueryLogger();
    }

    /**
     * Executes a CSV-formatted command via the C++ engine.
     * Steps:
     *  1. Start the C++ process
     *  2. Send command to process
     *  3. Read output from process
     *  4. Wait for process to finish
     *  5. Log command, result, and execution time
     *
     * @param command CSV-formatted command (e.g., "insert,1,name,email")
     * @return Output from C++ engine or error message
     */
    public String executeCommand(String command) {
        long startTime = System.currentTimeMillis();
        String result;
        Process process = null;

        try {
            process = processManager.startProcess();
            streamHandler.writeCommand(process, command);
            result = streamHandler.readOutput(process);
            process.waitFor();
        } catch (Exception e) {
            result = "> Bridge Error: " + e.getMessage();
        } finally {
            processManager.forceStop(process);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        queryLogger.log(new QueryLog(command, result, executionTime));

        return result;
    }

    /** Returns the QueryLogger instance. */
    public QueryLogger getQueryLogger() { return queryLogger; }

    /** Returns the ProcessManager instance. */
    public ProcessManager getProcessManager() { return processManager; }
}