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

@Service
public class ScreenshotToCsvService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You look at an image, describe what you see in one short natural
        sentence (like a helpful assistant would), and also extract any
        structured data from it.

        First write a one-sentence "description" of the image — plain,
        conversational, like you're telling someone what's in the picture.

        Then decide which of these four categories the image falls into,
        and extract accordingly:

        CATEGORY 1 — TABLE
        A spreadsheet, screenshot of a table, or printed grid with rows/columns.
        -> "rows": array of objects, each mapping the column header (as it
           appears in the image) to the cell value (string). Only include
           values you can actually read; use null for unreadable cells.

        CATEGORY 2 — TEXT
        A document, paragraph, sign, or block of prose (not tabular, not a photo
        of objects).
        -> "rows": array of objects, one per paragraph, each shaped
           {"paragraph": "<verbatim text as it appears, no summarizing>"}.
           Transcribe exactly what is visible — do not paraphrase or correct it.

        CATEGORY 3 — OBJECTS
        A photo of a real-world scene with countable items (e.g. a shop shelf,
        a fridge, a table with items on it, a room, a street).
        -> "rows": array of objects, one per distinct item type, shaped
           {"item": "<name>", "count": "<your best count as a string>",
            "category": "<general category if obvious, else empty>",
            "approximate": "true" or "false"}.
           Set "approximate":"true" whenever items are stacked, overlapping,
           partially hidden, or otherwise uncertain — be honest about
           uncertainty rather than presenting a guess as an exact count.

        CATEGORY 4 — OTHER
        Nothing recognizable as a table, text block, or countable-object scene.
        -> "rows": empty array.

        Respond with ONLY raw JSON, no markdown fences, no preamble. Exact shape:
        {"description": "<one natural sentence about the image>", "type": "TABLE|TEXT|OBJECTS|OTHER", "rows": [ ... ]}
        """;

    public static class ExtractionResult {
        public String description = "";
        public String type = "OTHER";
        public List<String> headers = new ArrayList<>();
        public List<Map<String, String>> rows = new ArrayList<>();
        public boolean tableFound;
    }

    public ExtractionResult extract(byte[] imageBytes, String mimeType) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "llava");
        body.put("prompt", "Extract structured data from this image. Respond with ONLY raw JSON matching the schema in the system prompt.\n\nSYSTEM PROMPT:\n" + SYSTEM_PROMPT);
        body.put("images", List.of(base64Image));
        body.put("stream", false);
        body.put("format", "json");

        String requestBody = mapper.writeValueAsString(body);
        String url = OLLAMA_URL;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        String rawText = root.path("response").asText();
        String cleanJson = stripCodeFences(rawText);

        JsonNode parsed = mapper.readTree(cleanJson);

        ExtractionResult result = new ExtractionResult();
        result.description = parsed.hasNonNull("description") ? parsed.get("description").asText() : "";
        result.type = parsed.hasNonNull("type") ? parsed.get("type").asText() : "OTHER";

        JsonNode rowsNode = parsed.path("rows");

        if (!rowsNode.isArray() || rowsNode.isEmpty()) {
            result.tableFound = false;
            return result;
        }

        result.tableFound = true;

        Iterator<String> firstRowKeys = rowsNode.get(0).fieldNames();
        while (firstRowKeys.hasNext()) {
            result.headers.add(firstRowKeys.next());
        }

        for (JsonNode rowNode : rowsNode) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String header : result.headers) {
                JsonNode val = rowNode.get(header);
                row.put(header, (val == null || val.isNull()) ? "" : val.asText());
            }
            result.rows.add(row);
        }

        return result;
    }

    private String stripCodeFences(String text) {
        Matcher m = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(text);
        return m.find() ? m.group(1).trim() : text.trim();
    }
}