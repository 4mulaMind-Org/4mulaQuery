package com.formulaquery.api;

/**
 * CommandType — Represents all valid database command types.
 *
 * Responsibilities:
 * 1. Define allowed command types (INSERT, SEARCH, DELETE, ALL, UNKNOWN).
 * 2. Provide parsing logic from raw command strings.
 *
 * Usage Example:
 * CommandType type = CommandType.from("insert,1,name,email"); // Returns INSERT
 *
 * Design Principle:
 * • Enum ensures type-safety for command handling throughout the engine.
 */
public enum CommandType {
    INSERT,
    SEARCH,
    DELETE,
    ALL,
    UNKNOWN;

    /**
     * Parses a raw CSV command string and returns the corresponding CommandType.
     *
     * @param command Raw command string (e.g., "insert,1,name,email")
     * @return Matching CommandType, or UNKNOWN if unrecognized
     */
    public static CommandType from(String command) {
        if (command == null || command.isBlank()) return UNKNOWN;
        String cmd = command.split(",")[0].trim().toLowerCase();
        return switch (cmd) {
            case "insert"         -> INSERT;
            case "search"         -> SEARCH;
            case "delete"         -> DELETE;
            case "all", "select"  -> ALL;
            default               -> UNKNOWN;
        };
    }
}