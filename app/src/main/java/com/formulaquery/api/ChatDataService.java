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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Powers "Chat with your Data" — a conversational interface over a
 * flexible (dynamic-schema) dataset.
 *
 * Two-step design so the LLM never invents numbers:
 *   1. INTERPRET  — Gemini turns the question + short conversation
 *                    history into a structured command (FILTER / AGGREGATE / ALL).
 *   2. COMPUTE     — Java executes that command against the real records.
 *   3. PHRASE      — Gemini is given ONLY the verified computed value and
 *                    asked to phrase one sentence around it — it is
 *                    explicitly told not to add any numbers of its own.
 */
@Service
public class ChatDataService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String GEMINI_API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public static class ChatTurn {
        public String role;   // "user" | "assistant"
        public String text;
    }

    public static class Condition {
        public String field;
        public String op;
        public String value;
    }

    public static class Aggregation {
        public String op;     // COUNT | AVG | SUM | MIN | MAX
        public String field;  // not required for COUNT
    }

    public static class InterpretResult {
        public String command; // FILTER | AGGREGATE | ALL | UNSUPPORTED
        public String logic = "AND";
        public List<Condition> conditions = new ArrayList<>();
        public Aggregation aggregation;
        public String reason;
    }

    public static class ChatResponse {
        public boolean supported;
        public String reply;
        public String command;
        public Double computedValue;
        public List<Map<String, String>> matchedRecords;
        public int matchedCount;
    }

    // =========================================================================
    // PUBLIC ENTRY POINT
    // =========================================================================

    public ChatResponse chat(List<String> schema, List<ChatTurn> history,
                              String userMessage, List<Map<String, String>> allRecords) throws Exception {

        InterpretResult interpreted = interpret(schema, history, userMessage);

        ChatResponse response = new ChatResponse();

        if ("UNSUPPORTED".equals(interpreted.command)) {
            response.supported = false;
            response.reply = interpreted.reason;
            return response;
        }

        // Step 2: compute for real, in Java — never trust the model with numbers
        List<Map<String, String>> filtered = applyFilter(interpreted, allRecords);

        response.supported = true;
        response.command = interpreted.command;
        response.matchedRecords = filtered.size() > 20 ? filtered.subList(0, 20) : filtered;
        response.matchedCount = filtered.size();

        Double computed = null;
        if ("AGGREGATE".equals(interpreted.command) && interpreted.aggregation != null) {
            computed = computeAggregation(interpreted.aggregation, filtered);
            response.computedValue = computed;
        }

        // Step 3: phrase the answer using ONLY the verified number(s)
        response.reply = phrase(userMessage, interpreted, computed, filtered.size());

        return response;
    }

    // =========================================================================
    // STEP 1: INTERPRET
    // =========================================================================

    private InterpretResult interpret(List<String> schema, List<ChatTurn> history, String userMessage) throws Exception {

        String systemPrompt = buildInterpretPrompt(schema);

        // Build conversation contents: prior turns + new user message
        List<Object> contents = new ArrayList<>();
        if (history != null) {
            for (ChatTurn t : history) {
                String role = "assistant".equals(t.role) ? "model" : "user";
                contents.add(turnObject(role, t.text));
            }
        }
        contents.add(turnObject("user", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> sysInstr = new LinkedHashMap<>();
        sysInstr.put("parts", List.of(Map.of("text", systemPrompt)));
        body.put("systemInstruction", sysInstr);
        body.put("contents", contents);
        body.put("generationConfig", Map.of("responseMimeType", "application/json"));

        String finalRequestBody = mapper.writeValueAsString(body);

        String rawText = callGemini(finalRequestBody);
        String cleanJson = stripCodeFences(rawText);
        JsonNode parsed = mapper.readTree(cleanJson);

        InterpretResult result = new InterpretResult();
        result.command = parsed.path("command").asText();
        result.logic = parsed.hasNonNull("logic") ? parsed.get("logic").asText() : "AND";
        result.reason = parsed.hasNonNull("reason") ? parsed.get("reason").asText() : null;

        if (parsed.has("conditions") && parsed.get("conditions").isArray()) {
            for (JsonNode c : parsed.get("conditions")) {
                Condition cond = new Condition();
                cond.field = c.path("field").asText();
                cond.op = c.path("op").asText();
                cond.value = c.path("value").asText();
                result.conditions.add(cond);
            }
        }

        if (parsed.has("aggregation") && !parsed.get("aggregation").isNull()) {
            Aggregation agg = new Aggregation();
            agg.op = parsed.get("aggregation").path("op").asText();
            agg.field = parsed.get("aggregation").hasNonNull("field")
                    ? parsed.get("aggregation").get("field").asText() : null;
            result.aggregation = agg;
        }

        validate(result, schema);
        return result;
    }

    private Map<String, Object> turnObject(String role, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("parts", List.of(Map.of("text", text)));
        return m;
    }

    // =========================================================================
    // STEP 2: COMPUTE (pure Java, verified — no LLM involved)
    // =========================================================================

    private List<Map<String, String>> applyFilter(InterpretResult r, List<Map<String, String>> records) {
        if (r.conditions.isEmpty()) return records;

        boolean isAnd = "AND".equalsIgnoreCase(r.logic);
        List<Map<String, String>> matched = new ArrayList<>();

        for (Map<String, String> record : records) {
            boolean overall = isAnd;
            for (Condition c : r.conditions) {
                boolean condResult = evaluateCondition(record.get(c.field), c.op, c.value);
                overall = isAnd ? (overall && condResult) : (overall || condResult);
            }
            if (overall) matched.add(record);
        }
        return matched;
    }

    private boolean evaluateCondition(String actual, String op, String expected) {
        if (actual == null) return false;
        try {
            double a = Double.parseDouble(actual.trim());
            double e = Double.parseDouble(expected.trim());
            return switch (op) {
                case "=" -> a == e;
                case "!=" -> a != e;
                case ">" -> a > e;
                case "<" -> a < e;
                case ">=" -> a >= e;
                case "<=" -> a <= e;
                default -> false;
            };
        } catch (NumberFormatException ignored) { }

        String a = actual.trim().toLowerCase();
        String e = expected.trim().toLowerCase();
        return switch (op) {
            case "=" -> a.equals(e);
            case "!=" -> !a.equals(e);
            case "contains" -> a.contains(e);
            default -> false;
        };
    }

    private Double computeAggregation(Aggregation agg, List<Map<String, String>> records) {
        if ("COUNT".equals(agg.op)) {
            return (double) records.size();
        }

        List<Double> values = new ArrayList<>();
        for (Map<String, String> r : records) {
            String raw = r.get(agg.field);
            if (raw == null) continue;
            try {
                values.add(Double.parseDouble(raw.trim()));
            } catch (NumberFormatException ignored) { }
        }

        if (values.isEmpty()) return null;

        return switch (agg.op) {
            case "AVG" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "SUM" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "MIN" -> Collections.min(values);
            case "MAX" -> Collections.max(values);
            default -> null;
        };
    }

    // =========================================================================
    // STEP 3: PHRASE (Gemini phrases the answer, using ONLY the verified value)
    // =========================================================================

    private String phrase(String question, InterpretResult interpreted, Double computed, int matchedCount) throws Exception {

        String factsBlock;
        if ("AGGREGATE".equals(interpreted.command)) {
            factsBlock = String.format(
                "The verified computed result is: %s(%s) = %s across %d matching record(s).",
                interpreted.aggregation.op,
                interpreted.aggregation.field == null ? "*" : interpreted.aggregation.field,
                computed == null ? "no numeric data available" : formatNumber(computed),
                matchedCount
            );
        } else if ("FILTER".equals(interpreted.command)) {
            factsBlock = String.format("The filter matched %d record(s).", matchedCount);
        } else {
            factsBlock = String.format("Showing all %d record(s).", matchedCount);
        }

        String systemPrompt = """
            You write a single short, natural, conversational sentence answering
            the user's question — like a helpful colleague, not a report.

            CRITICAL RULE: Use ONLY the verified fact given below. Do NOT invent,
            estimate, or add any number, statistic, or claim that isn't in the
            verified fact. If the verified fact says data is unavailable, say so
            plainly — do not guess.

            Verified fact:
            %s

            Respond with ONLY the sentence, no markdown, no preamble, no phrases
            like "the verified computed result" — just answer naturally.
            """.formatted(factsBlock);

        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> sysInstr = new LinkedHashMap<>();
        sysInstr.put("parts", List.of(Map.of("text", systemPrompt)));
        body.put("systemInstruction", sysInstr);
        body.put("contents", List.of(turnObject("user", question)));

        String requestBody = mapper.writeValueAsString(body);
        String rawText = callGemini(requestBody);
        return rawText.trim();
    }

    private String formatNumber(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.format("%.2f", d);
    }

    // =========================================================================
    // GEMINI CALL HELPER
    // =========================================================================

    private String callGemini(String requestBody) throws Exception {
        String url = String.format(GEMINI_API_URL_TEMPLATE, MODEL, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates: " + response.body());
        }
        return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
    }

    // =========================================================================
    // VALIDATION + PROMPT
    // =========================================================================

    private void validate(InterpretResult r, List<String> schema) {
        if (!Set.of("FILTER", "AGGREGATE", "ALL", "UNSUPPORTED").contains(r.command)) {
            throw new IllegalArgumentException("Unknown command from model: " + r.command);
        }
        for (Condition c : r.conditions) {
            if (!schema.contains(c.field)) {
                throw new IllegalArgumentException("Unknown field in condition: " + c.field);
            }
        }
        if ("AGGREGATE".equals(r.command)) {
            if (r.aggregation == null) {
                throw new IllegalArgumentException("AGGREGATE command requires an aggregation spec.");
            }
            if (!Set.of("COUNT", "AVG", "SUM", "MIN", "MAX").contains(r.aggregation.op)) {
                throw new IllegalArgumentException("Unsupported aggregation op: " + r.aggregation.op);
            }
            if (!"COUNT".equals(r.aggregation.op) &&
                    (r.aggregation.field == null || !schema.contains(r.aggregation.field))) {
                throw new IllegalArgumentException("Unknown or missing field for aggregation: " + r.aggregation.field);
            }
        }
    }

    private String buildInterpretPrompt(List<String> schema) {
        return """
            You interpret natural-language questions about a dataset into a
            structured JSON command. This is a multi-turn conversation — use
            the prior turns to resolve references like "them", "those", "what about X".

            COLUMNS (only these fields exist): %s

            ALLOWED COMMANDS:
              1. FILTER     -> "conditions": [{field, op, value}], "logic": "AND"|"OR"
              2. AGGREGATE  -> "aggregation": {"op": "COUNT|AVG|SUM|MIN|MAX", "field": "..." or null for COUNT},
                                optionally "conditions" to restrict which rows are aggregated
              3. ALL        -> return everything, no filter
              4. UNSUPPORTED -> can't be expressed with these columns/ops; give a short "reason"

            OPERATORS: =, !=, >, <, >=, <=, contains

            Respond with ONLY raw JSON, no markdown, no preamble. Exact shape:
            {"command": "FILTER|AGGREGATE|ALL|UNSUPPORTED", "logic": "AND|OR", "conditions": [{"field":"","op":"","value":""}], "aggregation": {"op":"","field":""} or null, "reason": null}

            Examples (columns: name, age, city, department, salary):

            User: "how many people work in Delhi?"
            {"command": "AGGREGATE", "logic": "AND", "conditions": [{"field":"city","op":"=","value":"Delhi"}], "aggregation": {"op":"COUNT","field":null}, "reason": null}

            User: "what's the average salary?"
            {"command": "AGGREGATE", "logic": "AND", "conditions": [], "aggregation": {"op":"AVG","field":"salary"}, "reason": null}

            User: "show me everyone in sales"
            {"command": "FILTER", "logic": "AND", "conditions": [{"field":"department","op":"=","value":"Sales"}], "aggregation": null, "reason": null}

            User (after previous turn about Sales dept): "what about their average salary?"
            {"command": "AGGREGATE", "logic": "AND", "conditions": [{"field":"department","op":"=","value":"Sales"}], "aggregation": {"op":"AVG","field":"salary"}, "reason": null}

            User: "sort by salary"
            {"command": "UNSUPPORTED", "logic": "AND", "conditions": [], "aggregation": null, "reason": "Sorting isn't supported yet — only filtering and aggregation (count, average, sum, min, max)."}
            """.formatted(String.join(", ", schema));
    }

    private String stripCodeFences(String text) {
        Matcher m = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(text);
        return m.find() ? m.group(1).trim() : text.trim();
    }
}