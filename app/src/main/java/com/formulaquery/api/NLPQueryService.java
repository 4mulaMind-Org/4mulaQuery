package com.formulaquery.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts natural-language queries into commands supported by
 * the 4mulaQuery C++ database engine.
 *
 * Schema: id (uint32), username (char[32]), email (char[255])
 *
 * Uses Google Gemini API for natural-language processing.
 */
@Service
public class NLPQueryService {

    // Gemini API key loaded from application.properties.
    @Value("${nlp.server.url:http://localhost:5001}")
    private String nlpServerUrl;

    @Value("${gemini.api.key:}")
    private String apiKey;

    // Gemini model used for natural-language query translation.
    private static final String MODEL = "gemini-3.5-flash-lite";

    // Gemini API endpoint used to generate the response.
    private static final String GEMINI_API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    // HTTP client used to communicate with the Gemini API.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Jackson ObjectMapper used for creating and parsing JSON.
    private final ObjectMapper mapper = new ObjectMapper();

    /*
     * Defines the database schema and the commands Gemini
     * is allowed to generate.
     */
    private static final String SYSTEM_PROMPT = """
        You translate natural language into a JSON command for a fixed-schema database engine.

        SCHEMA (only these fields exist):
          - id: unsigned integer
          - username: string (max 32 chars)
          - email: string (max 255 chars)

        ALLOWED COMMANDS (exactly these four, nothing else):
          1. INSERT  -> requires id, username, email
          2. SEARCH  -> requires id
          3. DELETE  -> requires id
          4. ALL     -> no fields required

        There is NO filtering by username/email, NO WHERE clauses beyond id lookup,
        and NO joins, aggregates, or sorting. If the user asks for something outside
        this scope (e.g. "find users with gmail addresses"), return command "UNSUPPORTED"
        with a short "reason" field explaining what's missing.

        Respond with ONLY raw JSON, no markdown fences, no preamble. Exact shape:
        {"command": "INSERT|SEARCH|DELETE|ALL|UNSUPPORTED", "id": <int or null>, "username": <string or null>, "email": <string or null>, "reason": <string or null>}

        Examples:
        User: "add a user with id 5 named John, email john@test.com"
        {"command": "INSERT", "id": 5, "username": "John", "email": "john@test.com", "reason": null}

        User: "find user 12"
        {"command": "SEARCH", "id": 12, "username": null, "email": null, "reason": null}

        User: "remove user with id 7"
        {"command": "DELETE", "id": 7, "username": null, "email": null, "reason": null}

        User: "show me everything"
        {"command": "ALL", "id": null, "username": null, "email": null, "reason": null}

        User: "find all users with a gmail email"
        {"command": "UNSUPPORTED", "id": null, "username": null, "email": null, "reason": "Filtering by email domain is not supported — only lookup by id, or ALL."}
        """;

    /**
     * Stores the structured response returned by Gemini
     * and the final command sent to the C++ engine.
     */
    public static class NLPResult {
        public String command;
        public Integer id;
        public String username;
        public String email;
        public String reason;
        public String engineCommand;
    }

    /**
     * Sends the user's natural-language query to Gemini,
     * parses the response, validates it, and builds the
     * corresponding C++ engine command.
     */
    /**
     * Calls the 4mulaMind-LLM Flask server to translate the user's
     * natural-language query into the fixed-schema database command.
     */
    public NLPResult translate(String userQuery) throws Exception {
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        payload.put("query", userQuery);
        String requestBody = mapper.writeValueAsString(payload);
        String url = nlpServerUrl + "/translate";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("4mulaMind NLP server error: " + response.statusCode() + " " + response.body());
        }

        JsonNode parsed = mapper.readTree(response.body());

        NLPResult result = new NLPResult();
        result.command = parsed.path("command").asText();
        result.id = parsed.hasNonNull("id") ? parsed.get("id").asInt() : null;
        result.username = parsed.hasNonNull("username") ? parsed.get("username").asText() : null;
        result.email = parsed.hasNonNull("email") ? parsed.get("email").asText() : null;
        result.reason = parsed.hasNonNull("reason") ? parsed.get("reason").asText() : null;

        validateAndBuildEngineCommand(result);
        return result;
    }

    /**
     * Validates Gemini's output and converts it into the
     * command format expected by the C++ engine.
     */
    private void validateAndBuildEngineCommand(NLPResult r) {

        switch (r.command) {

            case "INSERT":

                // INSERT requires ID, username, and email.
                if (r.id == null
                        || isBlank(r.username)
                        || isBlank(r.email)) {

                    throw new IllegalArgumentException(
                            "INSERT requires id, username, and email."
                    );
                }

                // Enforce the fixed username size limit.
                if (r.username.length() > 32) {
                    throw new IllegalArgumentException(
                            "username exceeds 32 char limit."
                    );
                }

                // Enforce the fixed email size limit.
                if (r.email.length() > 255) {
                    throw new IllegalArgumentException(
                            "email exceeds 255 char limit."
                    );
                }

                // Build the final INSERT command for the C++ engine.
                r.engineCommand = String.format(
                        "insert,%d,%s,%s",
                        r.id,
                        sanitize(r.username),
                        sanitize(r.email)
                );

                break;

            case "SEARCH":

                // SEARCH requires an ID.
                if (r.id == null) {
                    throw new IllegalArgumentException(
                            "SEARCH requires id."
                    );
                }

                // Build the final SEARCH command.
                r.engineCommand = String.format(
                        "search,%d",
                        r.id
                );

                break;

            case "DELETE":

                // DELETE requires an ID.
                if (r.id == null) {
                    throw new IllegalArgumentException(
                            "DELETE requires id."
                    );
                }

                // Build the final DELETE command.
                r.engineCommand = String.format(
                        "delete,%d",
                        r.id
                );

                break;

            case "ALL":

                // ALL does not require any parameters.
                r.engineCommand = "all";

                break;

            case "UNSUPPORTED":

                // Unsupported queries are not sent to the C++ engine.
                r.engineCommand = null;

                break;

            default:

                // Reject commands outside the supported command set.
                throw new IllegalArgumentException(
                        "Unknown command from model: " + r.command
                );
        }
    }

    // Checks whether a string is null, empty, or only whitespace.
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Sanitizes values before they are sent through the
     * C++ stdin protocol.
     */
    private String sanitize(String s) {
        return s
                .replaceAll("[\\s]+", "_")
                .replaceAll("[^a-zA-Z0-9_@.\\-]", "");
    }

    /**
     * Removes optional Markdown code fences from Gemini's response.
     */
    private String stripCodeFences(String text) {
        Matcher m = Pattern
                .compile("```(?:json)?\\s*([\\s\\S]*?)```")
                .matcher(text);

        return m.find()
                ? m.group(1).trim()
                : text.trim();
    }
}