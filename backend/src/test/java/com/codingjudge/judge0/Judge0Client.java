package com.codingjudge.judge0;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 proof-of-concept client for the Judge0 code-execution API.
 *
 * <p>ISOLATED: lives under {@code src/test} and is used only by the Judge0
 * POC tests. It is NOT wired into the submission flow, the Docker judge, or
 * any controller. Production integration (Phase 2) will decide what survives.
 *
 * <p>Protocol (verified against Judge0 CE v1.13.1 docs at ce.judge0.com):
 * <pre>
 * POST /submissions?base64_encoded=false&wait=false  -&gt; 201 {"token": "..."}
 * GET  /submissions/{token}?base64_encoded=false&amp;fields=... -&gt; 200 {...}
 * </pre>
 * {@code wait=true} is deliberately never used: it does not scale and the
 * production architecture will always poll by token.
 *
 * <p>Status ids: 1 = In Queue, 2 = Processing, 3 = Accepted, 4 = Wrong
 * Answer, 5 = Time Limit Exceeded, 6 = Compilation Error, 7-12 = Runtime
 * Error variants, 13 = Internal Error, 14 = Exec Format Error.
 */
public class Judge0Client {

    public static final int STATUS_IN_QUEUE = 1;
    public static final int STATUS_PROCESSING = 2;
    public static final int STATUS_ACCEPTED = 3;
    public static final int STATUS_WRONG_ANSWER = 4;
    public static final int STATUS_TIME_LIMIT_EXCEEDED = 5;
    public static final int STATUS_COMPILATION_ERROR = 6;
    public static final int STATUS_INTERNAL_ERROR = 13;

    private static final String DEFAULT_FIELDS =
            "stdout,stderr,compile_output,message,status,time,memory,token";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    /**
     * Optional RapidAPI host (e.g. {@code judge0-ce.p.rapidapi.com}). When set,
     * auth goes out as {@code X-RapidAPI-Key}/{@code X-RapidAPI-Host} instead
     * of {@code X-Auth-Token}, which is what RapidAPI-fronted Judge0 wants.
     */
    private final String rapidApiHost;
    private final HttpClient http;
    private final Duration requestTimeout;

    public Judge0Client(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, null);
    }

    public Judge0Client(String baseUrl, String apiKey, String rapidApiHost) {
        this(baseUrl, apiKey, rapidApiHost, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build(), Duration.ofSeconds(30));
    }

    /** Test-only seam: inject a stubbed {@link HttpClient}. */
    Judge0Client(String baseUrl, String apiKey, HttpClient http, Duration requestTimeout) {
        this(baseUrl, apiKey, null, http, requestTimeout);
    }

    Judge0Client(String baseUrl, String apiKey, String rapidApiHost,
                 HttpClient http, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.rapidApiHost = rapidApiHost;
        this.http = http;
        this.requestTimeout = requestTimeout;
    }

    public static boolean isTerminal(int statusId) {
        return statusId != STATUS_IN_QUEUE && statusId != STATUS_PROCESSING;
    }

    public static boolean isRuntimeError(int statusId) {
        return statusId >= 7 && statusId <= 12;
    }

    /** Submit source code and return the Judge0 submission token. */
    public String submit(String sourceCode, int languageId, String stdin,
                         Double cpuTimeLimitSec, Double memoryLimitKb) {
        return submit(sourceCode, languageId, stdin, null, cpuTimeLimitSec, memoryLimitKb);
    }

    /** Submit with an optional expected output (Judge0-side comparison). */
    public String submit(String sourceCode, int languageId, String stdin, String expectedOutput,
                         Double cpuTimeLimitSec, Double memoryLimitKb) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_code", sourceCode);
        body.put("language_id", languageId);
        if (stdin != null) {
            body.put("stdin", stdin);
        }
        if (expectedOutput != null) {
            body.put("expected_output", expectedOutput);
        }
        if (cpuTimeLimitSec != null) {
            body.put("cpu_time_limit", cpuTimeLimitSec);
        }
        if (memoryLimitKb != null) {
            body.put("memory_limit", memoryLimitKb);
        }
        // POC-only deviation (documented in docs/JUDGE0_POC.md): this laptop's
        // kernel is cgroup-v2-only while Judge0 CE 1.13.1 drives isolate in
        // cgroup-v1 mode, so every execution dies with Internal Error.
        // Setting both per-process flags makes Judge0 skip `--cg` entirely
        // and enforce limits with plain rlimits instead. Verdicts, polling
        // and the API flow are identical; only cgroup accounting is skipped.
        // Production (hosted Judge0) uses the defaults.
        body.put("enable_per_process_and_thread_time_limit", true);
        body.put("enable_per_process_and_thread_memory_limit", true);
        HttpResponse<String> response = send("POST",
                "/submissions?base64_encoded=false&wait=false", body);
        int code = response.statusCode();
        if (code == 201) {
            String token = text(parse(response.body()), "token");
            if (token == null || token.isBlank()) {
                throw new Judge0Exception("Judge0 returned 201 without a token");
            }
            return token;
        }
        if (code == 401) {
            throw new Judge0AuthException("Judge0 rejected the API key (401)");
        }
        if (code == 422) {
            throw new Judge0ValidationException("Judge0 rejected the submission: "
                    + truncate(response.body()));
        }
        if (code == 503) {
            throw new Judge0QueueFullException("Judge0 submission queue is full (503)");
        }
        throw new Judge0Exception("Judge0 submit failed with HTTP " + code + ": "
                + truncate(response.body()));
    }

    /** Fetch the current state of a submission by token. */
    public Judge0Result get(String token) {
        HttpResponse<String> response = send("GET",
                "/submissions/" + token + "?base64_encoded=false&fields=" + DEFAULT_FIELDS, null);
        if (response.statusCode() != 200) {
            throw new Judge0Exception("Judge0 fetch failed with HTTP "
                    + response.statusCode() + ": " + truncate(response.body()));
        }
        return toResult(parse(response.body()));
    }

    /**
     * Poll until the submission leaves queue/processing states.
     *
     * @throws Judge0TimeoutException if no terminal state is reached in time
     */
    public Judge0Result waitForTerminal(String token, long pollIntervalMs, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (true) {
            Judge0Result result = get(token);
            if (isTerminal(result.statusId())) {
                return result;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new Judge0TimeoutException(
                        "Submission " + token + " still not terminal after " + maxWaitMs + " ms");
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Judge0Exception("Polling interrupted", e);
            }
        }
    }

    /** List all languages (active + archived) known to this Judge0 instance. */
    public List<LanguageInfo> listLanguages() {
        HttpResponse<String> response = send("GET", "/languages/all", null);
        if (response.statusCode() != 200) {
            throw new Judge0Exception("Judge0 languages failed with HTTP " + response.statusCode());
        }
        List<LanguageInfo> out = new ArrayList<>();
        for (JsonNode node : parse(response.body())) {
            out.add(new LanguageInfo(node.path("id").asInt(-1),
                    node.path("name").asText(""), node.path("is_archived").asBoolean(false)));
        }
        return out;
    }

    private HttpResponse<String> send(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json");
            // The key itself is never logged or printed anywhere in this class.
            if (apiKey != null && !apiKey.isBlank()) {
                if (rapidApiHost != null && !rapidApiHost.isBlank()) {
                    builder.header("X-RapidAPI-Key", apiKey);
                    builder.header("X-RapidAPI-Host", rapidApiHost);
                } else {
                    builder.header("X-Auth-Token", apiKey);
                }
            }
            if (body != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // Note: Jackson's JsonProcessingException extends IOException,
            // so encode and transport failures land here together.
            throw new Judge0ConnectionException(
                    "Cannot reach Judge0 at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Judge0Exception("Judge0 request interrupted", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new Judge0Exception("Malformed JSON from Judge0: " + truncate(body), e);
        }
    }

    private Judge0Result toResult(JsonNode node) {
        JsonNode status = node.path("status");
        return new Judge0Result(
                text(node, "token"),
                status.path("id").asInt(-1),
                status.path("description").asText(""),
                text(node, "stdout"),
                text(node, "stderr"),
                text(node, "compile_output"),
                text(node, "message"),
                number(node, "time"),
                number(node, "memory"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isNull() || child.isMissingNode() ? null : child.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode child = node.path(field);
        // Judge0 serializes some numerics (notably `time`) as JSON strings.
        if (child.isNumber()) {
            return child.asDouble();
        }
        if (child.isTextual()) {
            try {
                return Double.parseDouble(child.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    public record Judge0Result(
            String token,
            int statusId,
            String statusDescription,
            String stdout,
            String stderr,
            String compileOutput,
            String message,
            Double timeSeconds,
            Double memoryKilobytes) {
    }

    public record LanguageInfo(int id, String name, boolean archived) {
    }

    public static class Judge0Exception extends RuntimeException {
        public Judge0Exception(String message) {
            super(message);
        }

        public Judge0Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Judge0AuthException extends Judge0Exception {
        public Judge0AuthException(String message) {
            super(message);
        }
    }

    public static class Judge0ValidationException extends Judge0Exception {
        public Judge0ValidationException(String message) {
            super(message);
        }
    }

    public static class Judge0QueueFullException extends Judge0Exception {
        public Judge0QueueFullException(String message) {
            super(message);
        }
    }

    public static class Judge0ConnectionException extends Judge0Exception {
        public Judge0ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Judge0TimeoutException extends Judge0Exception {
        public Judge0TimeoutException(String message) {
            super(message);
        }
    }
}
