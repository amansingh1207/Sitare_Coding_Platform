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

/**
 * Thin client for the public Piston code-execution API (https://emkc.org).
 *
 * <p>Used ONLY for single custom-input runs from the editor: Piston executes
 * arbitrary stdin and returns the real stdout, which DOMjudge can never do.
 * No key, no signup, no cost. Uses only the JDK HTTP client plus the
 * project's Jackson: no new dependencies.
 *
 * <p>Protocol (Piston v2, verified live):
 * <pre>
 * POST /execute {"language":"python","version":"3.10.0",
 *   "files":[{"name":"main.py","content":"..."}],
 *   "stdin":"...","run_timeout":2000}
 *   -&gt; 200 {"run":{"stdout":...,"stderr":...,"code":0,"signal":null,...},
 *   "compile":{...}|null}
 * </pre>
 * Execution is synchronous: no polling, no tokens.
 */
public class PistonClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;
    private final Duration requestTimeout;

    public PistonClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(60));
    }

    /** Test-only seam: inject a stubbed {@link HttpClient}. */
    PistonClient(String baseUrl, HttpClient http, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
        this.requestTimeout = requestTimeout;
    }

    /** Compile output of a Piston run; null when the language needs no build. */
    public record CompileResult(String stdout, String stderr, int code, String signal) {
        boolean failed() {
            return code != 0;
        }
    }

    /** One synchronous Piston execution. */
    public record RunResult(String stdout, String stderr, int code, String signal,
                            CompileResult compile) {
    }

    /** Base failure: malformed responses and non-200 statuses. */
    public static class PistonException extends RuntimeException {
        PistonException(String message) {
            super(message);
        }

        PistonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The API host itself is unreachable (DNS, refused, timeout). */
    public static class PistonConnectionException extends PistonException {
        PistonConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Execute {@code source} with {@code stdin} and return the run outcome.
     *
     * @param runTimeoutMs per-run wall limit forwarded to Piston
     *                     (Piston caps it server-side; our problems default 2s)
     */
    public RunResult execute(String language, String version, String filename,
                             String source, String stdin, long runTimeoutMs) {
        try {
            String body = JSON.writeValueAsString(java.util.Map.of(
                    "language", language,
                    "version", version,
                    "files", java.util.List.of(java.util.Map.of(
                            "name", filename,
                            "content", source)),
                    "stdin", stdin == null ? "" : stdin,
                    "run_timeout", Math.max(100, Math.min(runTimeoutMs, 20_000))));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/execute"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new PistonException("Piston execute failed with HTTP "
                        + response.statusCode());
            }
            return parse(JSON.readTree(response.body()));
        } catch (JsonProcessingException e) {
            throw new PistonException("Malformed JSON from Piston", e);
        } catch (IOException e) {
            throw new PistonConnectionException(
                    "Cannot reach Piston at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PistonException("Piston request interrupted", e);
        }
    }

    private static RunResult parse(JsonNode root) {
        JsonNode run = root.path("run");
        if (run.isMissingNode()) {
            throw new PistonException("Piston response has no run object");
        }
        JsonNode compile = root.path("compile");
        CompileResult compiled = null;
        if (!compile.isMissingNode() && !compile.isNull()) {
            compiled = new CompileResult(
                    compile.path("stdout").asText(""),
                    compile.path("stderr").asText(""),
                    compile.path("code").asInt(0),
                    compile.hasNonNull("signal")
                            ? compile.path("signal").asText() : null);
        }
        return new RunResult(
                run.path("stdout").asText(""),
                run.path("stderr").asText(""),
                run.path("code").asInt(-1),
                run.hasNonNull("signal") ? run.path("signal").asText() : null,
                compiled);
    }
}
