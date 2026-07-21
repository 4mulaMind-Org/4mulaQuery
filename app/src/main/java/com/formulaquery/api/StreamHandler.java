package com.formulaquery.api;

import java.io.*;

/**
 * StreamHandler — Manages I/O streams for the C++ engine process.
 *
 * Responsibilities:
 * 1. Write commands to the process's stdin.
 * 2. Read full output from the process's stdout.
 *
 * Design Principle:
 * • Single Responsibility — handles only Stream I/O.
 *
 * Example usage:
 * StreamHandler sh = new StreamHandler();
 * sh.writeCommand(process, "insert,1,name,email");
 * String output = sh.readOutput(process);
 */
public class StreamHandler {

    /**
     * Writes a command to the stdin of the given C++ process.
     * Closing the writer sends EOF to the process, which may trigger termination.
     *
     * @param process Running C++ process
     * @param command Command string to send
     * @throws IOException If an I/O error occurs
     */
    public void writeCommand(Process process, String command) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }
    }

    /**
     * Reads all output from the stdout of the given C++ process.
     *
     * @param process Running C++ process
     * @return Full output as a String
     * @throws IOException If an I/O error occurs
     */
    public String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}