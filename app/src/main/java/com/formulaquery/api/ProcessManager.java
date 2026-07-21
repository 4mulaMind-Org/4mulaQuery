package com.formulaquery.api;

import java.io.IOException;

/**
 * ProcessManager — Manages the lifecycle of the C++ engine process.
 * 
 * Responsibilities:
 * 1. Start the C++ engine process with proper I/O redirection.
 * 2. Forcefully stop the process if needed.
 * 
 * Design Principle:
 * • Single Responsibility — only manages process creation and termination.
 * 
 * Example usage:
 * ProcessManager pm = new ProcessManager("./core/4mulaQuery");
 * Process process = pm.startProcess();
 * pm.forceStop(process);
 */
public class ProcessManager {

    private final String enginePath;

    /**
     * Constructs ProcessManager with the path to the C++ engine binary.
     *
     * @param enginePath Path to the C++ executable
     */
    public ProcessManager(String enginePath) {
        this.enginePath = enginePath;
    }

    /**
     * Starts the C++ engine process.
     * Merges stderr into stdout so that all output can be read from Java.
     *
     * @return Started Process object
     * @throws IOException If the binary is not found or cannot start
     */
    public Process startProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(enginePath);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Forcefully terminates the process if it is still alive.
     * Useful for cleanup or hanging processes.
     *
     * @param process Process instance to terminate
     */
    public void forceStop(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * Returns the path to the C++ engine binary.
     *
     * @return Engine binary path
     */
    public String getEnginePath() {
        return enginePath;
    }
}