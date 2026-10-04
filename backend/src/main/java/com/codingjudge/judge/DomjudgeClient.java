package com.codingjudge.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Thin client for the DOMjudge CCS REST API (verified live against 9.0.0).
 *
 * <p>Uses only the JDK HTTP client plus the project's Jackson: no new
 * dependencies. Authentication is HTTP basic (team or admin account);
 * credentials travel in the Authorization header and are never logged.
 *
 * <p>Two facts this client is built around (both verified, not guessed):
 * <ul>
 *   <li>Submission source goes as {@code files:[{data: base64(ZIP)}]} —
 *       plain {@code source_code} is rejected with {@code 400}.</li>
 *   <li>Run stdout is {@code Serializer\Exclude}d server-side: per-test
 *       program output is NOT available through the API. Verdicts therefore
 *       come from DOMjudge, never from our own comparison.</li>
 * </ul>
 */
public class DomjudgeClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient http;
    private final Duration requestTimeout;

    public DomjudgeClient(String baseUrl, String username, String password) {
        this(baseUrl, username, password, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(60));
    }

    /** Test-only seam: inject a stubbed {@link HttpClient}. */
    DomjudgeClient(String baseUrl, String username, String password,
                   HttpClient http, Duration requestTimeout) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = base + "/api/v4";
        String credentials = username + ":" + (password == null ? "" : password);
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.http = http;
        this.requestTimeout = requestTimeout;
    }

    /** Submit one source file; returns the DOMjudge submission id. */
    public String submit(String contest, String languageId, String problemDomId,
                         String filename, String source, String entryPoint) {
        HttpResponse<String> response = send("POST", "/contests/" + contest + "/submissions",
                submitBody(languageId, problemDomId, filename, source, entryPoint));
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new DomjudgeAuthException("DOMjudge rejected credentials (HTTP "
                    + response.statusCode() + ")");
        }
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new DomjudgeValidationException("DOMjudge rejected the submission (HTTP "
                    + response.statusCode() + "): " + truncate(response.body()));
        }
        String id = text(parse(response.body()), "id");
        if (id == null || id.isBlank()) {
            throw new DomjudgeException("DOMjudge submit returned no id");
        }
        return id;
    }

    /** Poll until the judgement carries a verdict, then return it. */
    public JsonNode awaitJudgement(String contest, String submissionId,
                                   long pollIntervalMs, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (true) {
            HttpResponse<String> response =
                    send("GET", "/contests/" + contest + "/judgements?submission_id=" + submissionId, null);
            if (response.statusCode() != 200) {
                throw new DomjudgeException("DOMjudge judgements failed with HTTP "
                        + response.statusCode());
            }
            JsonNode list = parse(response.body());
            if (list.isArray() && !list.isEmpty()) {
                JsonNode latest = list.get(0);
                if (latest.hasNonNull("judgement_type_id")) {
                    return latest;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new DomjudgeTimeoutException("Submission " + submissionId
                        + " still unjudged after " + maxWaitMs + " ms");
            }
            sleep(pollIntervalMs);
        }
    }

    /** Runs for one judging, in server (ordinal) order. */
    public List<RunResult> listRuns(String contest, String judgementId) {
        // NOTE: despite what the API docs annotation suggests, the query
        // parameter is `judging_id` (verified in RunController source and
        // live: `judgement_id` is silently ignored and returns ALL runs).
        HttpResponse<String> response = send(
                "GET", "/contests/" + contest + "/runs?judging_id=" + judgementId, null);
        if (response.statusCode() != 200) {
            throw new DomjudgeException("DOMjudge runs failed with HTTP " + response.statusCode());
        }
        List<RunResult> runs = new ArrayList<>();
        for (JsonNode node : parse(response.body())) {
            runs.add(new RunResult(
                    text(node, "id"),
                    node.path("ordinal").asInt(-1),
                    text(node, "judgement_type_id"),
                    node.path("run_time").isNumber() ? node.path("run_time").asDouble() : null));
        }
        runs.sort((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
        return runs;
    }

    /** All languages known to this DOMjudge (id + name). */
    public List<LanguageInfo> listLanguages() {
        HttpResponse<String> response = send("GET", "/languages", null);
        if (response.statusCode() != 200) {
            throw new DomjudgeException("DOMjudge languages failed with HTTP " + response.statusCode());
        }
        List<LanguageInfo> out = new ArrayList<>();
        for (JsonNode node : parse(response.body())) {
            out.add(new LanguageInfo(text(node, "id"), text(node, "name")));
        }
        return out;
    }

    /** All problems of a contest (id + short_name). */
    public List<ProblemInfo> listProblems(String contest) {
        HttpResponse<String> response = send("GET", "/contests/" + contest + "/problems", null);
        if (response.statusCode() != 200) {
            throw new DomjudgeException("DOMjudge problems failed with HTTP " + response.statusCode());
        }
        List<ProblemInfo> out = new ArrayList<>();
        for (JsonNode node : parse(response.body())) {
            out.add(new ProblemInfo(text(node, "id"), text(node, "short_name"), text(node, "name")));
        }
        return out;
    }

    /**
     * Import a problem package ZIP (multipart field <b>zip</b>). Returns the
     * created problem id.
     *
     * <p>DOMjudge derives the problem <i>externalid</i> from the ZIP filename,
     * so callers MUST pass a unique filename per distinct package (we use the
     * mirror short-name); a fixed name collides on the second import with
     * {@code problem.externalid: This value is already used.}
     */
    public String importProblem(String contest, String zipFilename, byte[] packageZip) {
        String boundary = "DomjudgeBoundary" + System.nanoTime();
        byte[] body = multipart(boundary, "zip", zipFilename, "application/zip", packageZip);
        HttpResponse<String> response = sendMultipart(
                "/contests/" + contest + "/problems", boundary, body);
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new DomjudgeAuthException("DOMjudge rejected credentials (HTTP "
                    + response.statusCode() + ")");
        }
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new DomjudgeValidationException("DOMjudge rejected the problem package (HTTP "
                    + response.statusCode() + "): " + truncate(response.body()));
        }
        String id = text(parse(response.body()), "problem_id");
        if (id == null || id.isBlank()) {
            throw new DomjudgeException("DOMjudge import returned no problem_id");
        }
        return id;
    }

    /** Submission payload (factored for unit tests): source travels as base64 ZIP. */
    static java.util.Map<String, Object> submitBody(String languageId, String problemDomId,
                                                   String filename, String source, String entryPoint) {
        java.util.Map<String, Object> file = new java.util.LinkedHashMap<>();
        file.put("filename", "sources.zip");
        file.put("mime", "application/zip");
        file.put("data", zipBase64(filename, source));
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("language_id", languageId);
        body.put("problem_id", problemDomId);
        body.put("files", List.of(file));
        if (entryPoint != null && !entryPoint.isBlank()) {
            body.put("entry_point", entryPoint);
        }
        return body;
    }

    private static String zipBase64(String filename, String source) {
        return Base64.getEncoder().encodeToString(zipSingleFile(filename, source));
    }

    private static byte[] multipart(String boundary, String field, String filename,
                                    String mime, byte[] content) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headBytes = head.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[headBytes.length + content.length + tailBytes.length];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(content, 0, out, headBytes.length, content.length);
        System.arraycopy(tailBytes, 0, out, headBytes.length + content.length, tailBytes.length);
        return out;
    }

    /** Zip one source file in-memory (DOMjudge problem packages are ZIPs too). */
    public static byte[] zipSingleFile(String filename, String content) {
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buf)) {
                zip.putNextEntry(new java.util.zip.ZipEntry(filename));
                zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return buf.toByteArray();
        } catch (java.io.IOException e) {
            throw new DomjudgeException("Failed to zip submission source", e);
        }
    }

    private HttpResponse<String> send(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Authorization", authHeader);
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers
                                .ofString(JSON.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (JsonProcessingException e) {
            throw new DomjudgeException("Failed to encode DOMjudge request", e);
        } catch (IOException e) {
            throw new DomjudgeConnectionException(
                    "Cannot reach DOMjudge at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomjudgeException("DOMjudge request interrupted", e);
        }
    }

    private HttpResponse<String> sendMultipart(String path, String boundary, byte[] body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DomjudgeConnectionException(
                    "Cannot reach DOMjudge at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomjudgeException("DOMjudge request interrupted", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new DomjudgeException("Malformed JSON from DOMjudge: " + truncate(body), e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomjudgeException("DOMjudge polling interrupted", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isNull() || child.isMissingNode() ? null : child.asText();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    public record RunResult(String id, int ordinal, String judgementTypeId, Double runTimeSeconds) {
    }

    public record LanguageInfo(String id, String name) {
    }

    public record ProblemInfo(String id, String shortName, String name) {
    }

    public static class DomjudgeException extends RuntimeException {
        public DomjudgeException(String message) {
            super(message);
        }

        public DomjudgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DomjudgeAuthException extends DomjudgeException {
        public DomjudgeAuthException(String message) {
            super(message);
        }
    }

    public static class DomjudgeValidationException extends DomjudgeException {
        public DomjudgeValidationException(String message) {
            super(message);
        }
    }

    public static class DomjudgeConnectionException extends DomjudgeException {
        public DomjudgeConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DomjudgeTimeoutException extends DomjudgeException {
        public DomjudgeTimeoutException(String message) {
            super(message);
        }
    }
}
