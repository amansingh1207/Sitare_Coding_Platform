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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for the free Wandbox code-execution API (https://wandbox.org).
 *
 * <p>Used ONLY for single custom-input runs from the editor: Wandbox executes
 * arbitrary stdin and returns the real stdout, which DOMjudge can never do.
 * No key, no signup, no cost. Uses only the JDK HTTP client plus the
 * project's Jackson: no new dependencies.
 *
 * <p>Protocol (verified live against the public instance):
 * <pre>
 * POST /api/compile.json {"compiler":"gcc-head","code":"...","stdin":"..."}
 *   -&gt; 200 {"status":"0","compiler_output":"","compiler_error":"",
 *   "program_output":"...","program_error":"","program_exit_code":"0"}
 * </pre>
 * Execution is synchronous: no polling, no tokens. Compiler names come from
 * {@code GET /api/list.json}; the pinned names in {@link WandboxExecutionService}
 * were verified against that listing.
 */
public class WandboxClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;
    private final Duration requestTimeout;

    public WandboxClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(60));
    }

    /** Test-only seam: inject a stubbed {@link HttpClient}. */
    WandboxClient(String baseUrl, HttpClient http, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
        this.requestTimeout = requestTimeout;
    }

    /** One synchronous Wandbox compile+run outcome. */
    public record RunResult(String status, String compilerOutput, String compilerError,
                            String programOutput, String programError, int programExitCode) {
    }

    /** Base failure: malformed responses and non-200 statuses. */
    public static class WandboxException extends RuntimeException {
        WandboxException(String message) {
            super(message);
        }

        WandboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The API host itself is unreachable (DNS, refused, timeout). */
    public static class WandboxConnectionException extends WandboxException {
        WandboxConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Compile (if needed) and run {@code code} with {@code stdin}.
     *
     * @param compilerOptionRaw raw flags for compilers that accept them
     *                          (e.g. {@code -std=c++17 -O2}); blank means none
     */
    public RunResult compile(String compiler, String code, String stdin,
                             String compilerOptionRaw) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("compiler", compiler);
            payload.put("code", code);
            payload.put("stdin", stdin == null ? "" : stdin);
            payload.put("save", false);
            if (compilerOptionRaw != null && !compilerOptionRaw.isBlank()) {
                payload.put("compiler-option-raw", compilerOptionRaw);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/compile.json"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new WandboxException("Wandbox compile failed with HTTP "
                        + response.statusCode());
            }
            return parse(JSON.readTree(response.body()));
        } catch (JsonProcessingException e) {
            throw new WandboxException("Malformed JSON from Wandbox", e);
        } catch (IOException e) {
            throw new WandboxConnectionException(
                    "Cannot reach Wandbox at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WandboxException("Wandbox request interrupted", e);
        }
    }

    private static RunResult parse(JsonNode root) {
        if (!root.isObject()) {
            throw new WandboxException("Wandbox response is not a JSON object");
        }
        return new RunResult(
                root.path("status").asText(""),
                root.path("compiler_output").asText(""),
                root.path("compiler_error").asText(""),
                root.path("program_output").asText(""),
                root.path("program_error").asText(""),
                exitCode(root.path("program_exit_code")));
    }

    /** {@code program_exit_code} arrives as a string ("0") — sometimes a number. */
    private static int exitCode(JsonNode node) {
        if (node.isInt()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
